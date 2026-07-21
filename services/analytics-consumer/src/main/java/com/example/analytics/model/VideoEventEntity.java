package com.example.analytics.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "video_events", indexes = {
        @Index(name = "idx_video_event_type", columnList = "eventType"),
        @Index(name = "idx_video_event_timestamp", columnList = "eventTimestamp"),
        @Index(name = "idx_video_event_video_type", columnList = "videoId, eventType")
})
public class VideoEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType eventType;

    @Column(nullable = false, length = 64)
    private String videoId;

    @Column(length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String sessionId;

    private Long positionSeconds;

    private Long durationSeconds;

    private Long seekFrom;

    private Long seekTo;

    @Column(length = 20)
    private String deviceType;

    @Column(length = 20)
    private String quality;

    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getPositionSeconds() { return positionSeconds; }
    public void setPositionSeconds(Long positionSeconds) { this.positionSeconds = positionSeconds; }

    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

    public Long getSeekFrom() { return seekFrom; }
    public void setSeekFrom(Long seekFrom) { this.seekFrom = seekFrom; }

    public Long getSeekTo() { return seekTo; }
    public void setSeekTo(Long seekTo) { this.seekTo = seekTo; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }

    public Instant getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }

    public Instant getCreatedAt() { return createdAt; }
}
