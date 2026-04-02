package com.example.spring_stream_backend.services.impl;

import com.example.spring_stream_backend.Entity.Video;
import com.example.spring_stream_backend.config.RabbitConfig;
import com.example.spring_stream_backend.repositories.VideoRepositories;
import com.example.spring_stream_backend.services.VideoServices;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
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
import java.util.Optional;
import java.util.UUID;

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
    @Override
    public Resource getm3u8(String videoId) throws FileNotFoundException {
        Optional<Video> byId = videoRepositories.findById(videoId);
        if (byId.isEmpty()) {
            return null;
        }
        //7378c6ba-c67c-4783-be67-58f1780798fa_15370713_3840_2160_30fps.mp4/master.m3u8
        String filepath = byId.get().getFilepath();
        String objectName = filepath.replaceFirst("raw/","")+"/"+"master.m3u8";
        try {
            return new InputStreamResource(minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(hlsBucketName)
                            .object(objectName)
                            .build()));

        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            throw new FileNotFoundException("No such file or directory");
        }
    }
    @Override
    public InputStream getVideoSegement(String videoId,String segmentNo) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        Optional<Video> byId = videoRepositories.findById(videoId);
        if (byId.isEmpty()) {
            throw new FileNotFoundException("Video not found for id: " + videoId);
        }

        String rawObjectName = byId.get().getFilepath();
        if (rawObjectName == null || rawObjectName.isBlank()) {
            throw new FileNotFoundException("No object path stored for video id: " + videoId);
        }

        String normalizedSegmentName = segmentNo.endsWith(".ts") ? segmentNo : segmentNo + ".ts";
        String hlsBaseObjectName = rawObjectName+"/"+normalizedSegmentName;
        String objectName=hlsBaseObjectName.replaceFirst("raw/","");
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
        return videoRepositories.findAll();
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
