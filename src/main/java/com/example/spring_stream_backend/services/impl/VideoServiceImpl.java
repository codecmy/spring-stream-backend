package com.example.spring_stream_backend.services.impl;

import com.example.spring_stream_backend.Entity.Video;
import com.example.spring_stream_backend.repositories.VideoRepositories;
import com.example.spring_stream_backend.services.VideoServices;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
@Service
public class VideoServiceImpl implements VideoServices {
    @Value("${files.video}")
    String DIR;
    @Value("${files.video.hsl}")
    String HSL_FILE;
    @Autowired
    private VideoRepositories videoRepositories;

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
        //folder path
        try {
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            InputStream inputStream= file.getInputStream();
            //folder path with filename
            assert fileName != null;
            String cleanFileName = StringUtils.cleanPath(fileName);
            String cleanFolderName = StringUtils.cleanPath(DIR);
            Path path = Paths.get(cleanFolderName, cleanFileName);
            System.out.println(path);
            //copy file to folder
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
            //video metadata
            video.setContentType(contentType);
            video.setFilepath(String.valueOf(path));
            //save metadata
            return videoRepositories.save(video);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public Video findById(String id) {
        return videoRepositories.findById(id).orElse(null);
    }
    @Override
    public Video findByTitle(String title) {
        return null;
    }

    @Override
    public List<Video> getAllVideo() {
        return videoRepositories.findAll();
    }

    @Override
    public String processVideo(String videoId){
        Video byId = this.findById(videoId);
        String filepath = byId.getFilepath();
        //path where to store data
        Path videoPath=Paths.get(filepath);
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
