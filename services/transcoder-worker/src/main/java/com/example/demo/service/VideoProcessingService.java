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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

        List<String> command = Arrays.asList(
                "ffmpeg",
                "-y",
                "-i", inputFile.toString(),
                "-preset", "ultrafast",
                "-threads", "2",
                "-filter_complex",
                "[0:v]split=4[v1][v2][v3][v4];" +
                "[0:a]asplit=4[a1][a2][a3][a4];" +
                "[v1]scale=w=640:h=-2[v360p];" +
                "[v2]scale=w=854:h=-2[v480p];" +
                "[v3]scale=w=1280:h=-2[v720p];" +
                "[v4]scale=w=1920:h=-2[v1080p]",
                "-map", "[v360p]", "-c:v:0", "libx264", "-b:v:0", "800k", "-maxrate:v:0", "1050k", "-bufsize:v:0", "1600k",
                "-map", "[a1]", "-c:a:0", "aac", "-b:a:0", "128k", "-ac", "2",
                "-map", "[v480p]", "-c:v:1", "libx264", "-b:v:1", "1400k", "-maxrate:v:1", "1750k", "-bufsize:v:1", "2800k",
                "-map", "[a2]", "-c:a:1", "aac", "-b:a:1", "128k", "-ac", "2",
                "-map", "[v720p]", "-c:v:2", "libx264", "-b:v:2", "2800k", "-maxrate:v:2", "3500k", "-bufsize:v:2", "5600k",
                "-map", "[a3]", "-c:a:2", "aac", "-b:a:2", "128k", "-ac", "2",
                "-map", "[v1080p]", "-c:v:3", "libx264", "-b:v:3", "5000k", "-maxrate:v:3", "6250k", "-bufsize:v:3", "10000k",
                "-map", "[a4]", "-c:a:3", "aac", "-b:a:3", "128k", "-ac", "2",
                "-f", "hls",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-max_muxing_queue_size", "1024",
                "-hls_segment_filename", outputPath + "/v%v/segment_%3d.ts",
                "-master_pl_name", "master.m3u8",
                "-var_stream_map", "v:0,a:0 v:1,a:1 v:2,a:2 v:3,a:3",
                outputPath + "/v%v/index.m3u8"
        );

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

        log.info("HLS generated at: {} with segments pattern {}", outputDir, outputPath + "/%v/segment_%3d.ts");
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