package com.example.demo.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class VideoProcessingService {
    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);

    private final MinioClient minioClient;
    private final String bucketName;
    private final String hlsBucketName;
    private final Path videoBaseDir;
    private final Path hlsBaseDir;

    public VideoProcessingService(MinioClient minioClient,
                                   @Value("${spring.minio.bucket}") String bucketName,
                                   @Value("${spring.minio.hls-bucket}") String hlsBucketName,
                                   @Value("${spring.files.video}") String filesVideo,
                                   @Value("${spring.files.hls}") String filesHls) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.hlsBucketName = hlsBucketName;
        this.videoBaseDir = Paths.get(filesVideo).toAbsolutePath();
        this.hlsBaseDir = Paths.get(filesHls).toAbsolutePath();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(videoBaseDir);
        Files.createDirectories(hlsBaseDir);
    }

    private static class AudioTrack {
        final int streamIndex;
        final String language;
        final String codec;

        AudioTrack(int streamIndex, String language, String codec) {
            this.streamIndex = streamIndex;
            this.language = language;
            this.codec = codec;
        }
    }

    private List<AudioTrack> probeAudioTracks(Path inputFile) {
        List<String> cmd = Arrays.asList(
                "ffprobe", "-v", "error",
                "-select_streams", "a",
                "-show_entries", "stream=index:stream_tags=language",
                "-of", "csv=p=0",
                inputFile.toString()
        );
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("ffprobe failed with exit code {}", exitCode);
                return List.of();
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", 2);
                if (parts.length == 0) continue;
                try {
                    int index = Integer.parseInt(parts[0].trim());
                    String lang = parts.length > 1 && !parts[1].trim().isEmpty()
                            ? parts[1].trim() : "und";
                    tracks.add(new AudioTrack(index, lang, "aac"));
                } catch (NumberFormatException ignored) {
                }
            }

            log.info("Detected {} audio tracks via ffprobe", tracks.size());
            return tracks;
        } catch (Exception e) {
            log.warn("Failed to probe audio tracks: {}", e.getMessage());
            return List.of();
        }
    }

    public void process(String objectName) {
        log.info("Processing video: {}", objectName);
        Path localVideo = null;
        Path hlsOutput = null;
        try {
            localVideo = downloadSource(objectName);
            hlsOutput = runHlsTranscoding(localVideo, objectName);
            uploadHlsArtifacts(hlsOutput, objectName);
        } catch (Exception e) {
            log.error("Processing failed for {}: {}", objectName, e.getMessage(), e);
            throw new RuntimeException("Processing failed for " + objectName, e);
        } finally {
            cleanupLocalArtifacts(localVideo, hlsOutput);
        }
    }

    private Path downloadSource(String objectName) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        Path localVideo = videoBaseDir.resolve(objectName);
        Files.createDirectories(localVideo.getParent());

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())){
            Files.copy(stream, localVideo, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Saved to temp file: {}", localVideo);
        return localVideo;
    }

    private static final String[] BANDWIDTHS = {"800000", "1400000", "2800000", "5000000"};
    private static final String[] RESOLUTIONS = {"640x360", "854x480", "1280x720", "1920x1080"};

    private Path runHlsTranscoding(Path inputFile, String objectName) throws Exception {
        Path outputDir = prepareHlsOutputDir(objectName);
        String outputPath = outputDir.toString();

        List<AudioTrack> audioTracks = probeAudioTracks(inputFile);
        log.info("Audio tracks detected: {}", audioTracks.size());
        for (AudioTrack at : audioTracks) {
            log.info("  Audio track {}: lang={}", at.streamIndex, at.language);
        }

        boolean hasAudio = !audioTracks.isEmpty();
        int audioPassCount = hasAudio ? audioTracks.size() : 1;

        for (int pass = 0; pass < audioPassCount; pass++) {
            String audioLabel = hasAudio ? audioTracks.get(pass).language : "und";
            log.info("=== FFmpeg pass {}/{}: audio={} ===", pass + 1, audioPassCount, audioLabel);

            Path passDir = outputDir.resolve("a" + pass);
            Files.createDirectories(passDir);
            String passPath = passDir.toString();

            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-y");
            command.add("-i");
            command.add(inputFile.toString());
            command.add("-preset");
            command.add("ultrafast");
            command.add("-threads");
            command.add("4");

            String filterComplex;
            if (hasAudio) {
                filterComplex = "[0:v]split=4[v1][v2][v3][v4];" +
                        "[0:a:" + pass + "]asplit=4[a1][a2][a3][a4];" +
                        "[v1]scale=w=640:h=-2[v360p];" +
                        "[v2]scale=w=854:h=-2[v480p];" +
                        "[v3]scale=w=1280:h=-2[v720p];" +
                        "[v4]scale=w=1920:h=-2[v1080p]";
            } else {
                filterComplex = "[0:v]split=4[v1][v2][v3][v4];" +
                        "anullsrc=channel_layout=stereo:sample_rate=44100[asrc];" +
                        "[asrc]asplit=4[a1][a2][a3][a4];" +
                        "[v1]scale=w=640:h=-2[v360p];" +
                        "[v2]scale=w=854:h=-2[v480p];" +
                        "[v3]scale=w=1280:h=-2[v720p];" +
                        "[v4]scale=w=1920:h=-2[v1080p]";
                command.add("-shortest");
            }

            command.add("-filter_complex");
            command.add(filterComplex);

            // Map 4 video+audio pairs
            String[][] videoVariants = {
                    {"[v360p]", "[a1]", "800k", "1050k", "1600k"},
                    {"[v480p]", "[a2]", "1400k", "1750k", "2800k"},
                    {"[v720p]", "[a3]", "2800k", "3500k", "5600k"},
                    {"[v1080p]", "[a4]", "5000k", "6250k", "10000k"}
            };

            for (int v = 0; v < 4; v++) {
                command.add("-map");
                command.add(videoVariants[v][0]); // video
                command.add("-map");
                command.add(videoVariants[v][1]); // audio copy
                command.add("-c:v:" + v);
                command.add("libx264");
                command.add("-b:v:" + v);
                command.add(videoVariants[v][2]);
                command.add("-maxrate:v:" + v);
                command.add(videoVariants[v][3]);
                command.add("-bufsize:v:" + v);
                command.add(videoVariants[v][4]);
                command.add("-c:a:" + v);
                command.add("aac");
                command.add("-b:a:" + v);
                command.add("128k");
                command.add("-ac");
                command.add("2");
                command.add("-metadata:s:a:" + v);
                command.add("language=" + audioLabel);
            }

            // HLS: 4 outputs, each has 1 video + 1 audio
            command.add("-f");
            command.add("hls");
            command.add("-hls_time");
            command.add("10");
            command.add("-hls_list_size");
            command.add("0");
            command.add("-max_muxing_queue_size");
            command.add("65536");
            command.add("-hls_segment_filename");
            command.add(passPath + "/v%v/segment_%3d.ts");
            command.add("-var_stream_map");
            command.add("v:0,a:0 v:1,a:1 v:2,a:2 v:3,a:3");
            command.add(passPath + "/v%v/index.m3u8");

            log.info("FFmpeg pass {} command: {}", pass + 1, String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(outputDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg pass {} failed (exit {}):\n{}", pass + 1, exitCode, output);
                throw new RuntimeException("HLS FFmpeg pass " + (pass + 1) + " failed with exit code " + exitCode);
            }
            log.info("FFmpeg pass {}/{} completed successfully", pass + 1, audioPassCount);
        }

        // Build master playlist manually
        writeMasterPlaylist(outputDir, audioTracks, hasAudio);
        log.info("HLS generated at: {}", outputDir);
        return outputDir;
    }

    private void writeMasterPlaylist(Path outputDir, List<AudioTrack> audioTracks, boolean hasAudio) throws IOException {
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("#EXT-X-VERSION:3\n\n");

        for (int v = 0; v < 4; v++) {
            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(BANDWIDTHS[v]).append(",")
                    .append("RESOLUTION=").append(RESOLUTIONS[v]).append("\n");
            if (hasAudio) {
                master.append("a0/v").append(v).append("/index.m3u8\n");
            } else {
                master.append("v").append(v).append("/index.m3u8\n");
            }
        }

        Path masterPath = outputDir.resolve("master.m3u8");
        Files.writeString(masterPath, master.toString());
        log.info("Written main master playlist: {}", masterPath);
        log.info("Main master playlist content:\n{}", master);

        if (hasAudio) {
            writePerAudioMasterPlaylists(outputDir, audioTracks);
        }
    }

    private void writePerAudioMasterPlaylists(Path outputDir, List<AudioTrack> audioTracks) throws IOException {
        for (int i = 0; i < audioTracks.size(); i++) {
            AudioTrack track = audioTracks.get(i);
            StringBuilder master = new StringBuilder();
            master.append("#EXTM3U\n");
            master.append("#EXT-X-VERSION:3\n\n");

            for (int v = 0; v < 4; v++) {
                master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                        .append(BANDWIDTHS[v]).append(",")
                        .append("RESOLUTION=").append(RESOLUTIONS[v]).append("\n");
                master.append("v").append(v).append("/index.m3u8\n");
            }

            Path audioDir = outputDir.resolve("a" + i);
            Path audioMasterPath = audioDir.resolve("master.m3u8");
            Files.writeString(audioMasterPath, master.toString());
            log.info("Written per-audio master for {} (a{}): {}", track.language, i, audioMasterPath);
            log.info("Per-audio master content (a{}):\n{}", i, master);
        }
    }

    private void uploadHlsArtifacts(Path hlsDir, String objectName) throws IOException {
        Path objectPath = Paths.get(objectName);
        String baseName = stripExtension(objectPath.getFileName().toString());
        String remoteBase = buildRemotePrefix(objectPath, baseName);

        try (Stream<Path> stream = Files.walk(hlsDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relative = hlsDir.relativize(file).toString().replace('\\', '/');
                        String remoteObject = remoteBase.isEmpty() ? relative : remoteBase + "/" + relative;
                        try {
                            uploadFile(file, remoteObject, hlsBucketName);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to upload " + file, e);
                        } catch (ServerException | InsufficientDataException | ErrorResponseException |
                                 InvalidKeyException | XmlParserException | InvalidResponseException |
                                 NoSuchAlgorithmException | InternalException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void uploadFile(Path file, String objectName, String targetBucket) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        long size = Files.size(file);
        String contentType = determineContentType(file);

        try (InputStream stream = Files.newInputStream(file)) {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(targetBucket)
                    .object(objectName)
                    .stream(stream, size, -1);
            builder.contentType(contentType);
            minioClient.putObject(builder.build());
            log.info("Uploaded {} to {}/{}", file.getFileName(), targetBucket, objectName);
        }
    }

    private void cleanupLocalArtifacts(Path videoFile, Path hlsDir) {
        if (videoFile != null) {
            try {
                Files.deleteIfExists(videoFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp video {}", videoFile, e);
            }
        }

        if (hlsDir != null && Files.exists(hlsDir)) {
            try (Stream<Path> stream = Files.walk(hlsDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete {}", path, e);
                            }
                        });
            } catch (IOException e) {
                log.warn("Failed to clean up {}", hlsDir, e);
            }
        }
    }

    private Path prepareHlsOutputDir(String objectName) throws IOException {
        Path objectPath = Paths.get(objectName);
        String baseName = stripExtension(objectPath.getFileName().toString());

        Path dir = hlsBaseDir;
        if (objectPath.getParent() != null) {
            dir = dir.resolve(objectPath.getParent());
        }
        dir = dir.resolve(baseName);
        Files.createDirectories(dir);
        return dir;
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String buildRemotePrefix(Path objectPath, String baseName) {
        return baseName;
    }

    private static String determineContentType(Path file) throws IOException {
        String probe = Files.probeContentType(file);
        if (probe != null) {
            return probe;
        }

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (name.endsWith(".ts")) {
            return "video/MP2T";
        }
        return "application/octet-stream";
    }
}