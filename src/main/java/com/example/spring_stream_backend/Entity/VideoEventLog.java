package com.example.spring_stream_backend.Entity;

import jakarta.persistence.*;

@Entity
@Table(name = "video_events")
public class VideoEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "video_id", nullable = false, length = 64)
    private String videoId;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "position_seconds")
    private Long positionSeconds;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "seek_from")
    private Long seekFrom;

    @Column(name = "seek_to")
    private Long seekTo;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "quality", length = 20)
    private String quality;

    @Column(name = "event_timestamp", nullable = false)
    private java.time.Instant eventTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getVideoId() { return videoId; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public Long getPositionSeconds() { return positionSeconds; }
    public Long getDurationSeconds() { return durationSeconds; }
    public String getDeviceType() { return deviceType; }
    public String getQuality() { return quality; }
    public java.time.Instant getEventTimestamp() { return eventTimestamp; }
}
