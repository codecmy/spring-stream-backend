package com.example.spring_stream_backend.services.impl;

import com.example.spring_stream_backend.Entity.Video;
import com.example.spring_stream_backend.config.RabbitConfig;
import com.example.spring_stream_backend.repositories.VideoRepositories;
import com.example.spring_stream_backend.services.VideoServices;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoServiceImpl implements VideoServices {
    @Value("${files.video}")
    String DIR;
    @Value("${files.video.hsl}")
    String HSL_FILE;
    private final VideoRepositories videoRepositories;
    private final MinioClient minioClient;
    @Value("${minio.bucket}")
    private String bucketName;
    @Value("${minio.hls_bucket}")
    private String hlsBucketName;
    private final RabbitTemplate rabbitTemplate;
    private final Map<String, String> hlsPrefixCache = new ConcurrentHashMap<>();
    public VideoServiceImpl(MinioClient minioClient, RabbitTemplate rabbitTemplate, VideoRepositories videoRepositories) {
        this.rabbitTemplate = rabbitTemplate;
        this.minioClient = minioClient;
        this.videoRepositories = videoRepositories;
    }
    @PostConstruct
    public void init() throws IOException {
        File file = new File(DIR);
        Files.createDirectories(Paths.get(HSL_FILE));
        if(!file.exists()) {
            boolean mkdir = file.mkdir();
            if(mkdir) {
                System.out.println("Folder created");
            }else {
                System.out.println("Folder could not be created");
            }
        }else {
            System.out.println("File already exists");
        }
    }

    @Override
    public Video save(Video video, MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("upload-", ".tmp");
        Path thumbFile = Files.createTempFile("thumb-", ".jpg");
        try {
            file.transferTo(tempFile.toFile());
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            assert fileName != null;
            String cleanFileName = StringUtils.cleanPath(fileName);
            String objectName = "raw/" + UUID.randomUUID() + "_" + cleanFileName;

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", tempFile.toString(),
                    "-vframes", "1", "-q:v", "3", thumbFile.toString()
            );
            pb.inheritIO();
            try {
                Process process = pb.start();
                int exit = process.waitFor();
                if (exit == 0 && Files.size(thumbFile) > 0) {
                    try (InputStream thumbStream = Files.newInputStream(thumbFile)) {
                        minioClient.putObject(
                                PutObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object("thumbnails/" + video.getVideoId() + ".jpg")
                                        .stream(thumbStream, Files.size(thumbFile), -1)
                                        .contentType("image/jpeg")
                                        .build()
                        );
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try (InputStream videoStream = Files.newInputStream(tempFile)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(videoStream, Files.size(tempFile), -1)
                                .contentType(contentType)
                                .build()
                );
            }
            video.setContentType(contentType);
            video.setFilepath(objectName);

            // Probe audio tracks and store language codes
            try {
                ProcessBuilder probePb = new ProcessBuilder(
                        "ffprobe", "-v", "error",
                        "-select_streams", "a",
                        "-show_entries", "stream_tags=language",
                        "-of", "csv=p=0",
                        tempFile.toString()
                );
                Process probeProcess = probePb.start();
                String probeOutput;
                try (InputStream is = probeProcess.getInputStream()) {
                    probeOutput = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                }
                probeProcess.waitFor();
                if (probeProcess.exitValue() == 0 && !probeOutput.isEmpty()) {
                    java.util.LinkedHashSet<String> langs = new java.util.LinkedHashSet<>();
                    for (String line : probeOutput.split("\n")) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            langs.add(line);
                        }
                    }
                    video.setAudioLanguages(String.join(",", langs));
                } else {
                    video.setAudioLanguages("");
                }
            } catch (Exception e) {
                System.err.println("Failed to probe audio tracks: " + e.getMessage());
                video.setAudioLanguages("");
            }

            Video save = videoRepositories.save(video);
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, objectName);
            return save;
        } catch (IOException | ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            throw new IOException("Failed to upload video to object storage", e);
        } finally {
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            try { Files.deleteIfExists(thumbFile); } catch (IOException ignored) {}
        }
    }
    private String findHlsPrefixByVideoId(String videoId) {
        String cached = hlsPrefixCache.get(videoId);
        if (cached != null) {
            return cached;
        }
        Video dbVideo = videoRepositories.findById(videoId).orElse(null);
        if (dbVideo == null || dbVideo.getFilepath() == null) return null;
        String fp = dbVideo.getFilepath();
        if (!fp.startsWith("raw/")) return null;
        String afterRaw = fp.substring(4);
        int underscoreIdx = afterRaw.indexOf('_');
        if (underscoreIdx <= 0) return null;
        String filepathUuid = afterRaw.substring(0, underscoreIdx);
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(hlsBucketName)
                            .prefix(filepathUuid + "_")
                            .delimiter("/")
                            .maxKeys(1)
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) {
                    String name = item.objectName();
                    String prefix = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                    hlsPrefixCache.put(videoId, prefix);
                    return prefix;
                }
            }
        } catch (Exception e) {
            System.err.println("findHlsPrefixByVideoId failed for " + videoId + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public Resource getm3u8(String videoId) throws IOException {
        String prefix = findHlsPrefixByVideoId(videoId);
        if (prefix == null) {
            return null;
        }
        try {
            return new InputStreamResource(minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(hlsBucketName)
                            .object(prefix + "/master.m3u8")
                            .build()));
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            throw new FileNotFoundException("master.m3u8 not found for video: " + videoId);
        }
    }
    @Override
    public Resource getHlsSubResource(String videoId, String subPath) throws IOException {
        String prefix = findHlsPrefixByVideoId(videoId);
        if (prefix == null) {
            throw new FileNotFoundException("Video not found for id: " + videoId);
        }
        String objectName = prefix + "/" + subPath;
        try {
            return new InputStreamResource(minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(hlsBucketName)
                            .object(objectName)
                            .build()));
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            throw new FileNotFoundException("HLS sub-resource not found: " + subPath + " for video: " + videoId);
        }
    }

    @Override
    public Video findById(String id) {
        return videoRepositories.findById(id).orElse(null);
    }
    @Override
    public Resource getThumbnail(String videoId) throws IOException {
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object("thumbnails/" + videoId + ".jpg")
                            .build()
            );
            return new InputStreamResource(stream);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equalsIgnoreCase(e.errorResponse().code())) {
                throw new FileNotFoundException("Thumbnail not found for video: " + videoId);
            }
            throw new IOException("Failed to get thumbnail", e);
        } catch (Exception e) {
            throw new IOException("Failed to get thumbnail", e);
        }
    }

    @Override
    public List<Video> getAllVideo() {
        List<Video> dbVideos = videoRepositories.findAll();
        if (dbVideos.isEmpty()) {
            return dbVideos;
        }

        Set<String> hlsFolders = getHlsFolderNames();

        for (Video v : dbVideos) {
            String fp = v.getFilepath();
            if (fp != null && fp.startsWith("raw/")) {
                String key = fp.substring(4);
                int dotIdx = key.lastIndexOf('.');
                if (dotIdx > 0) key = key.substring(0, dotIdx);

                if (hlsFolders.contains(key)) {
                    v.setContentType("application/vnd.apple.mpegurl");
                    hlsPrefixCache.put(v.getVideoId(), key);
                }
            }
        }

        return dbVideos;
    }

    private Set<String> getHlsFolderNames() {
        Set<String> folders = new HashSet<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(hlsBucketName)
                            .delimiter("/")
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) continue;
                String name = item.objectName();
                String folder = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                folders.add(folder);
            }
        } catch (Exception e) {
            System.err.println("getHlsFolderNames failed: " + e.getMessage());
        }
        return folders;
    }

    @Override
    public void deleteVideo(String videoId) throws Exception {
        Video video = videoRepositories.findById(videoId).orElse(null);
        if (video == null) {
            throw new FileNotFoundException("Video not found: " + videoId);
        }

        if (video.getFilepath() != null && !video.getFilepath().isBlank()) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(video.getFilepath())
                                .build()
                );
            } catch (Exception e) {
                System.err.println("Failed to delete raw video from MinIO: " + e.getMessage());
            }
        }

        String hlsPrefix = findHlsPrefixByVideoId(videoId);
        if (hlsPrefix != null) {
            try {
                Iterable<Result<Item>> objects = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(hlsBucketName)
                                .prefix(hlsPrefix + "/")
                                .recursive(true)
                                .build()
                );
                List<DeleteObject> deleteObjects = new LinkedList<>();
                for (Result<Item> result : objects) {
                    deleteObjects.add(new DeleteObject(result.get().objectName()));
                }
                if (!deleteObjects.isEmpty()) {
                    Iterable<Result<DeleteError>> errors = minioClient.removeObjects(
                            RemoveObjectsArgs.builder()
                                    .bucket(hlsBucketName)
                                    .objects(deleteObjects)
                                    .build()
                    );
                    for (Result<DeleteError> error : errors) {
                        System.err.println("Failed to delete HLS object: " + error.get().objectName());
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to delete HLS assets from MinIO: " + e.getMessage());
            }
            hlsPrefixCache.remove(videoId);
        }

        videoRepositories.delete(video);
    }

    @Override
    public String processVideo(String videoId){
        Video byId = this.findById(videoId);
        if (byId == null || byId.getFilepath() == null || byId.getFilepath().isBlank()) {
            return null;
        }
        String filepath = byId.getFilepath();
        // When file is already in object storage, processing is expected to happen by worker pipeline.
        if (filepath.startsWith("raw/")) {
            return videoId;
        }
        //path where to store data
        Path videoPath=Paths.get(filepath);
        if (!Files.exists(videoPath)) {
            return null;
        }
        try {
            Path outputPath = Paths.get(HSL_FILE, videoId);
            Files.createDirectories(outputPath);
            String ffmpegCmd = String.format(
                    "ffmpeg -i \"%s\" -c:v libx264 -c:a aac -strict -2 -f hls -hls_time 10 -hls_list_size 0 -hls_segment_filename \"%s/segment_%%3d.ts\"  \"%s/master.m3u8\" ",
                    videoPath, outputPath, outputPath
            );
            System.out.println(ffmpegCmd);
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", ffmpegCmd);
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new RuntimeException("video processing failed!!");
            }
            return videoId;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
