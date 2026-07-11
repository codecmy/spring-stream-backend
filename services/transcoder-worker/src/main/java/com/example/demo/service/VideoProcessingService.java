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

        String getName() {
            switch (language) {
                case "hin": return "Hindi";
                case "jpn": return "Japanese";
                case "eng": return "English";
                case "spa": return "Spanish";
                case "fre": case "fra": return "French";
                case "deu": case "ger": return "German";
                case "chi": case "zho": return "Chinese";
                case "kor": return "Korean";
                case "ara": return "Arabic";
                case "por": return "Portuguese";
                case "rus": return "Russian";
                case "ita": return "Italian";
                case "und": return "Unknown";
                default: return language.toUpperCase(Locale.ROOT);
            }
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

    private Path runHlsTranscoding(Path inputFile, String objectName) throws Exception {
        Path outputDir = prepareHlsOutputDir(objectName);
        String outputPath = outputDir.toString();

        List<AudioTrack> audioTracks = probeAudioTracks(inputFile);
        log.info("Audio tracks detected: {}", audioTracks.size());

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-i");
        command.add(inputFile.toString());
        command.add("-preset");
        command.add("ultrafast");
        command.add("-threads");
        command.add("2");

        // Build filter_complex — split video and first audio track 4 ways each
        String filterComplex = "[0:v]split=4[v1][v2][v3][v4];" +
                "[0:a:0]asplit=4[a1][a2][a3][a4];" +
                "[v1]scale=w=640:h=-2[v360p];" +
                "[v2]scale=w=854:h=-2[v480p];" +
                "[v3]scale=w=1280:h=-2[v720p];" +
                "[v4]scale=w=1920:h=-2[v1080p]";
        command.add("-filter_complex");
        command.add(filterComplex);

        // Video variant definitions
        String[][] videoVariants = {
                {"[v360p]", "800k", "1050k", "1600k"},
                {"[v480p]", "1400k", "1750k", "2800k"},
                {"[v720p]", "2800k", "3500k", "5600k"},
                {"[v1080p]", "5000k", "6250k", "10000k"}
        };

        // Map video streams (output indices 0-3)
        for (int v = 0; v < 4; v++) {
            command.add("-map");
            command.add(videoVariants[v][0]);
            command.add("-c:v:" + v);
            command.add("libx264");
            command.add("-b:v:" + v);
            command.add(videoVariants[v][1]);
            command.add("-maxrate:v:" + v);
            command.add(videoVariants[v][2]);
            command.add("-bufsize:v:" + v);
            command.add(videoVariants[v][3]);
        }

        // Map split audio streams (output indices 4-7, one per variant)
        for (int a = 0; a < 4; a++) {
            command.add("-map");
            command.add("[a" + (a + 1) + "]");
            command.add("-c:a:" + a);
            command.add("aac");
            command.add("-b:a:" + a);
            command.add("128k");
            command.add("-ac");
            command.add("2");
        }

        // Build var_stream_map: pair each video variant with its own audio copy
        StringBuilder varStreamMap = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) varStreamMap.append(" ");
            varStreamMap.append("v:").append(i).append(",a:").append(i);
        }

        // HLS parameters
        command.add("-f");
        command.add("hls");
        command.add("-hls_time");
        command.add("10");
        command.add("-hls_list_size");
        command.add("0");
        command.add("-max_muxing_queue_size");
        command.add("1024");
        command.add("-hls_segment_filename");
        command.add(outputPath + "/v%v/segment_%3d.ts");
        command.add("-master_pl_name");
        command.add("master.m3u8");
        if (varStreamMap.length() > 0) {
            command.add("-var_stream_map");
            command.add(varStreamMap.toString());
        }
        command.add(outputPath + "/v%v/index.m3u8");

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
            log.error("FFmpeg failed (exit {}):\n{}", exitCode, output);
            throw new RuntimeException("HLS FFmpeg failed with exit code " + exitCode);
        }
        log.info("FFmpeg output:\n{}", output);

        log.info("HLS generated at: {}", outputDir);
        return outputDir;
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