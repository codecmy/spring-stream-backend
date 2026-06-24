package com.example.demo.listener;

import com.example.demo.service.VideoProcessingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class VideoProcessingListener{
    private final VideoProcessingService videoProcessingService;
    public VideoProcessingListener(VideoProcessingService videoProcessingService){
        this.videoProcessingService = videoProcessingService;
    }
    @RabbitListener(queues = "video.processing.queue")
    public void processVideo(String objectName){
        System.out.println("Processing video: " + objectName);
        videoProcessingService.process(objectName);
    }
}
