package com.example.spring_stream_backend.services.impl;

import com.example.spring_stream_backend.Entity.Video;
import com.example.spring_stream_backend.config.RabbitConfig;
import com.example.spring_stream_backend.repositories.VideoRepositories;
import com.example.spring_stream_backend.services.VideoServices;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.errors.*;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        try (InputStream inputStream = file.getInputStream()) {
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            assert fileName != null;
            String cleanFileName = StringUtils.cleanPath(fileName);
            String objectName = "raw/" + UUID.randomUUID() + "_" + cleanFileName;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
            video.setContentType(contentType);
            video.setFilepath(objectName);
            Video save = videoRepositories.save(video);
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE,RabbitConfig.ROUTING_KEY,objectName);
            return save;
        } catch (IOException | ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            throw new IOException("Failed to upload video to object storage", e);
        }
    }
    private String findHlsPrefixByVideoId(String videoId) {
        String cached = hlsPrefixCache.get(videoId);
        if (cached != null) {
            return cached;
        }
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(hlsBucketName)
                            .prefix(videoId + "_")
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
    public Resource getQualityPlaylist(String videoId, String quality) throws IOException {
        String prefix = findHlsPrefixByVideoId(videoId);
        if (prefix == null) {
            throw new FileNotFoundException("Video not found for id: " + videoId);
        }
        try {
            return new InputStreamResource(minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(hlsBucketName)
                            .object(prefix + "/" + quality + "/index.m3u8")
                            .build()));
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            throw new FileNotFoundException("Quality playlist not found: " + quality + " for video: " + videoId);
        }
    }

    @Override
    public InputStream getVideoSegment(String videoId, String segmentNo, String quality) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String prefix = findHlsPrefixByVideoId(videoId);
        if (prefix == null) {
            throw new FileNotFoundException("Video not found for id: " + videoId);
        }
        String normalizedSegmentName = segmentNo.endsWith(".ts") ? segmentNo : segmentNo + ".ts";
        String objectName = prefix + "/" + quality + "/" + normalizedSegmentName;
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(hlsBucketName)
                            .object(objectName)
                            .build());
        } catch (ErrorResponseException e) {
            if (!"NoSuchKey".equalsIgnoreCase(e.errorResponse().code())) {
                throw e;
            }
        }
        throw new FileNotFoundException("Quality segment not found for video: " + videoId +
                ", quality: " + quality + ", segment: " + normalizedSegmentName);
    }

    @Override
    public InputStream getVideoSegement(String videoId,String segmentNo) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String prefix = findHlsPrefixByVideoId(videoId);
        if (prefix == null) {
            throw new FileNotFoundException("Video not found for id: " + videoId);
        }

        String normalizedSegmentName = segmentNo.endsWith(".ts") ? segmentNo : segmentNo + ".ts";
        String objectName = prefix + "/" + normalizedSegmentName;
        try {
                return minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(hlsBucketName)
                                .object(objectName)
                                .build());
            } catch (ErrorResponseException e) {
                if (!"NoSuchKey".equalsIgnoreCase(e.errorResponse().code())) {
                    throw e;
                }
            }

        throw new FileNotFoundException("Segment not found for video id: " + videoId + ", segment: " + normalizedSegmentName);
    }

    private String buildSegmentObjectNameFromRaw(String rawObjectName, String segmentFileName) {
        int slashIdx = rawObjectName.lastIndexOf('/');
        String folder = slashIdx >= 0 ? rawObjectName.substring(0, slashIdx) : "raw";
        String rawFileName = slashIdx >= 0 ? rawObjectName.substring(slashIdx + 1) : rawObjectName;
        int dotIdx = rawFileName.lastIndexOf('.');
        String stem = dotIdx >= 0 ? rawFileName.substring(0, dotIdx) : rawFileName;
        String hlsFolder = folder.replaceFirst("^raw/?", "video-hls/");
        if (hlsFolder.endsWith("/")) {
            hlsFolder = hlsFolder.substring(0, hlsFolder.length() - 1);
        }
        String prefix = hlsFolder.isBlank() ? stem : hlsFolder + "/" + stem;
        return prefix + "/" + segmentFileName;
    }

    private String buildHlsBaseObjectName(String rawObjectName) {
        if (rawObjectName == null || rawObjectName.isBlank()) {
            return "";
        }
        return rawObjectName.replaceFirst("^raw/", "video-hls/");
    }

    private String buildMasterPlaylistObjectName(String rawObjectName) {
        return buildHlsBaseObjectName(rawObjectName) + "/master.m3u8";
    }

    @Override
    public Video findById(String id) {
        return videoRepositories.findById(id).orElse(null);
    }
    @Override
    public Video findByTitle(String title) {
        return videoRepositories.findByTitle(title).orElse(null);
    }

    @Override
    public List<Video> getAllVideo() {
        List<Video> hlsVideos = listHlsVideos();
        if (!hlsVideos.isEmpty()) {
            return hlsVideos;
        }
        List<Video> rawVideos = listRawVideos();
        if (!rawVideos.isEmpty()) {
            return rawVideos;
        }
        return videoRepositories.findAll();
    }

    private List<Video> listHlsVideos() {
        List<Video> videos = new ArrayList<>();
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
                int underscoreIdx = folder.indexOf('_');
                if (underscoreIdx <= 0) continue;
                String vid = folder.substring(0, underscoreIdx);
                String filename = folder.substring(underscoreIdx + 1);
                int dotIdx = filename.lastIndexOf('.');
                String title = dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
                hlsPrefixCache.put(vid, folder);
                videos.add(Video.builder()
                        .videoId(vid)
                        .title(title)
                        .videoName(filename)
                        .filepath("raw/" + folder)
                        .contentType("application/vnd.apple.mpegurl")
                        .build());
            }
        } catch (Exception e) {
            System.err.println("listHlsVideos failed: " + e.getMessage());
        }
        return videos;
    }

    private List<Video> listRawVideos() {
        List<Video> videos = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix("raw/")
                            .build()
            );
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir()) continue;
                String objectName = item.objectName();
                String stripped = objectName.startsWith("raw/") ? objectName.substring(4) : objectName;
                int underscoreIdx = stripped.indexOf('_');
                if (underscoreIdx <= 0) continue;
                String vid = stripped.substring(0, underscoreIdx);
                String filename = stripped.substring(underscoreIdx + 1);
                int dotIdx = filename.lastIndexOf('.');
                String title = dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
                videos.add(Video.builder()
                        .videoId(vid)
                        .title(title)
                        .videoName(filename)
                        .filepath(objectName)
                        .contentType("video/mp4")
                        .build());
            }
        } catch (Exception e) {
            System.err.println("listRawVideos failed: " + e.getMessage());
        }
        return videos;
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
