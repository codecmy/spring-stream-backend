package com.example.spring_stream_backend.payload;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
public class VideoEvent {
    private EventType eventType;
    private String videoId;
    private String userId;
    private String sessionId;
    private Long position;
    private Long duration;
    private Long seekFrom;
    private Long seekTo;
    private String deviceType;
    private String quality;
    private Instant timestamp;

    public VideoEvent() {}

}
