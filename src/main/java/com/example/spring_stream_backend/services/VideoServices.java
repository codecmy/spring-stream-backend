package com.example.spring_stream_backend.services;

import com.example.spring_stream_backend.Entity.Video;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface VideoServices{
    Video save(Video video, MultipartFile file) throws IOException;
    Video findById(String id);
    List<Video> getAllVideo();
    String processVideo(String videoId) throws IOException;
    Resource getm3u8(String videoId) throws IOException;
    void deleteVideo(String videoId) throws Exception;
    Resource getThumbnail(String videoId) throws IOException;
    Resource getHlsSubResource(String videoId, String subPath) throws IOException;
}
