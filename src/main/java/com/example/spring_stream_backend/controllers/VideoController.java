package com.example.spring_stream_backend.controllers;

import com.example.spring_stream_backend.AppConstants;
import com.example.spring_stream_backend.Entity.Video;
import com.example.spring_stream_backend.payload.CustomMessage;
import com.example.spring_stream_backend.services.VideoServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/videos")
@CrossOrigin(origins = "http://localhost:63342/")
public class VideoController {
    private final VideoServices videoServices;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    public VideoController(VideoServices videoServices, MinioClient minioClient) {
        this.videoServices = videoServices;
        this.minioClient = minioClient;
    }

    @PostMapping
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("title") String title,
                                                     @RequestParam("description") String desc) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(CustomMessage.builder().message("Video file is required").success(false).build());
        }

        Video video = new Video();
        video.setVideoId(UUID.randomUUID().toString());
        video.setVideoDescription(desc);
        video.setTitle(title);
        try {
            videoServices.save(video, file);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IOException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CustomMessage.builder().message("Video Not uploaded")
                            .success(false)
                            .build());
        }
    }

    @GetMapping
    public List<Video> getAllVideos(){
        return videoServices.getAllVideo();
    }



    //stream HLS file
    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<Resource> hlsStreamVideos(
            @PathVariable String videoId
    ){
        try {
            Resource resource = videoServices.getm3u8(videoId);
            if(resource==null){
                throw new FileNotFoundException("File does not exists");
            }
            return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                    .body(resource);
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    @GetMapping("/{videoId}/{quality}/index.m3u8")
    public ResponseEntity<Resource> getQualityPlaylist(
            @PathVariable String videoId,
            @PathVariable String quality
    ){
        try {
            Resource resource = videoServices.getQualityPlaylist(videoId, quality);
            if (resource == null) {
                throw new FileNotFoundException("Quality playlist not found");
            }
            return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                    .body(resource);
        } catch (FileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{videoId}/{quality}/{segment}.ts")
    public ResponseEntity<Resource> serveQualitySegment(
            @PathVariable String videoId,
            @PathVariable String quality,
            @PathVariable String segment
    ){
        try {
            InputStream videoSegment = videoServices.getVideoSegment(videoId, segment, quality);
            Resource resource = new InputStreamResource(videoSegment);
            return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, "video/mp2t")
                    .body(resource);
        } catch (FileNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{videoId}/{segment}.ts")
    public ResponseEntity<Resource> serveSegments(
            @PathVariable String videoId,
            @PathVariable String segment
    ){
        try {
            InputStream videoSegment = videoServices.getVideoSegement(videoId,segment);
            Resource resource = new InputStreamResource(videoSegment);
            return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE,"video/mp2t")
                    .body(resource);

        }catch (FileNotFoundException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }catch (Exception e){
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping("stream/range/{videoId}")
    public ResponseEntity<Resource> streamVideoRange(
            @PathVariable String videoId,
            @RequestHeader(value = "Range",required = false) String range
    ){
        Video byId = videoServices.findById(videoId);
        if (byId == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        String objectName = byId.getFilepath();
        if (objectName == null || objectName.isBlank()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        String contentType = byId.getContentType();
        if(contentType==null){
            contentType="application/octet-stream";
        }

        long fileLength;
        try {
            StatObjectResponse statObject = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build()
            );
            fileLength = statObject.size();
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if(range==null){
            try {
                InputStream fullStream = minioClient.getObject(
                        GetObjectArgs.builder().bucket(bucketName).object(objectName).build()
                );
                return ResponseEntity.ok()
                        .header("Accept-Ranges", "bytes")
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(new InputStreamResource(fullStream));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        }

        String normalized = range.trim();
        if (normalized.startsWith("bytes=")) {
            normalized = normalized.substring("bytes=".length());
        } else if (normalized.startsWith("bytes-")) {
            normalized = normalized.substring("bytes-".length());
        }
        if (normalized.isBlank()) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileLength)
                    .build();
        }
        String[] split = normalized.split("-");
        if (split.length == 0 || split.length > 2) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileLength)
                    .build();
        }
        long rangeStart;
        long rangeEnd;
        try {
            if (split.length == 1) {
                rangeStart = Long.parseLong(split[0]);
                rangeEnd = rangeStart + AppConstants.Chunk_size - 1;
            } else if (split[0].isBlank()) {
                long suffixLength = Long.parseLong(split[1]);
                if (suffixLength <= 0) {
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .header("Content-Range", "bytes */" + fileLength)
                            .build();
                }
                rangeStart = Math.max(fileLength - suffixLength, 0);
                rangeEnd = fileLength - 1;
            } else {
                rangeStart = Long.parseLong(split[0]);
                rangeEnd = rangeStart + AppConstants.Chunk_size - 1;
                if (!split[1].isBlank()) {
                    long requestedEnd = Long.parseLong(split[1]);
                    rangeEnd = Math.min(rangeEnd, requestedEnd);
                }
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileLength)
                    .build();
        }
        if(rangeEnd>fileLength-1){
            rangeEnd=fileLength-1;
        }
        if (rangeStart < 0 || rangeStart >= fileLength || rangeStart > rangeEnd) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileLength)
                    .build();
        }

        long content=rangeEnd-rangeStart+1;
        InputStream inputStream;
        try{
            inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .offset(rangeStart)
                            .length(content)
                            .build()
            );
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        long actualRangeEnd = rangeEnd;
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Range", "bytes "+rangeStart+"-"+actualRangeEnd+"/"+fileLength);
        headers.add("Accept-Ranges", "bytes");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.setContentLength(content);

        return ResponseEntity
                .status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(inputStream));
    }
}
