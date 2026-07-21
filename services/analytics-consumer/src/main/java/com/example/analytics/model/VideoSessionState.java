package com.example.analytics.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "video_session_state")
@IdClass(VideoSessionStateId.class)
public class VideoSessionState {

    @Id
    @Column(nullable = false, length = 64)
    private String videoId;

    @Id
    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false)
    private long lastPositionSeconds;

    @Column(nullable = false)
    private Instant lastTimestamp;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public long getLastPositionSeconds() { return lastPositionSeconds; }
    public void setLastPositionSeconds(long lastPositionSeconds) { this.lastPositionSeconds = lastPositionSeconds; }

    public Instant getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(Instant lastTimestamp) { this.lastTimestamp = lastTimestamp; }
}
