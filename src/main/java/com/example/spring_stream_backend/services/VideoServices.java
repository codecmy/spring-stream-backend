package com.example.spring_stream_backend.services;

import com.example.spring_stream_backend.Entity.Video;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface VideoServices{
    //Save video
    Video save(Video video, MultipartFile file) throws IOException;
    //Get vdo by Id
    Video findById(String id);
    //Get by title
    Video findByTitle(String title);
    //Get all
    List<Video> getAllVideo();
    //video processing
    String processVideo(String videoId) throws IOException;
}
