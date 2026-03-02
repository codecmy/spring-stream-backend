package com.example.spring_stream_backend.controllers;

import com.example.spring_stream_backend.AppConstants;
import com.example.spring_stream_backend.Entity.Video;
import com.example.spring_stream_backend.payload.CustomMessage;
import com.example.spring_stream_backend.services.VideoServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/videos")
@CrossOrigin(origins = "http://localhost:63342/")
public class VideoController {
    @Autowired
    private VideoServices videoServices;
    @PostMapping
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("title") String title,
                                                     @RequestParam("description") String desc) throws IOException {

        Video video = new Video();
        video.setVideoId(UUID.randomUUID().toString());
        video.setVideoDescription(desc);
        video.setTitle(title);
        Video save = videoServices.save(video, file);
        if(save!=null){
            videoServices.processVideo(video.getVideoId());
            return new ResponseEntity<>(HttpStatus.OK);
        }else {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CustomMessage.builder().message("Video Not uploaded")
                            .success(false)
                            .build());
        }
    }

    //Stream Videos
    //URL
    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> stream(@PathVariable String videoId){
        Video byId = videoServices.findById(videoId);
        if (byId == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        String contentType = byId.getContentType();
        String filepath = byId.getFilepath();
        Resource resource = new FileSystemResource(filepath);

        if(contentType==null){
            contentType="application/octet-stream";
        }
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
    @GetMapping
    public List<Video> getAllVideos(){
        return videoServices.getAllVideo();
    }
    //stream HLS file

    @Value("${files.video.hsl}")
    public String HSL_Path;
    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<Resource> hlsStreamVideos(
            @PathVariable String videoId
    ){
        //Create Path
        Path path=Paths.get(HSL_Path,videoId,"master.m3u8");

        if(!Files.exists(path)){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Resource resource=new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE,"application/vnd.apple.mpegurl")
                .body(resource);
    }
    @GetMapping("/{videoId}/{segment}.ts")
    public ResponseEntity<Resource> serveSegments(
            @PathVariable String videoId,
            @PathVariable String segment
    ){
        Path path = Paths.get(HSL_Path, videoId, segment + ".ts");
        FileSystemResource resource = new FileSystemResource(path);
        if(!resource.exists()){
            return  new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        System.out.println("Segments are served API are called");
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE,"video/mp2t")
                .body(resource);
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
        Path filepath = Paths.get(byId.getFilepath());
        Resource resource = new FileSystemResource(filepath);
        String contentType = byId.getContentType();
        if(contentType==null){
            contentType="application/octet-stream";
        }
        long fileLength=filepath.toFile().length();
        if(range==null){
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);
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
        byte[] buffer;
        try(InputStream inputStream = Files.newInputStream(filepath)){
            skipFully(inputStream, rangeStart);
            buffer = inputStream.readNBytes((int) content);
        }catch (IOException e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        if (buffer.length == 0) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileLength)
                    .build();
        }

        long actualRangeEnd = rangeStart + buffer.length - 1;
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Range", "bytes "+rangeStart+"-"+actualRangeEnd+"/"+fileLength);
        headers.add("Accept-Ranges", "bytes");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.setContentLength(buffer.length);

        return ResponseEntity
                .status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .contentType(MediaType.parseMediaType(contentType))
                .body(new ByteArrayResource(buffer));
    }

    private void skipFully(InputStream inputStream, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped <= 0) {
                if (inputStream.read() == -1) {
                    break;
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
