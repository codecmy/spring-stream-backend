package com.example.spring_stream_backend.Entity;

import jakarta.persistence.*;

@Entity
@Table(name = "video_analytics_summary")
public class AnalyticsSummary {

    @Id
    @Column(name = "video_id", length = 64)
    private String videoId;

    @Column(name = "total_views")
    private long totalViews;

    @Column(name = "total_plays")
    private long totalPlays;

    @Column(name = "total_pauses")
    private long totalPauses;

    @Column(name = "total_seeks")
    private long totalSeeks;

    @Column(name = "total_completions")
    private long totalCompletions;

    @Column(name = "total_drop_offs")
    private long totalDropOffs;

    @Column(name = "unique_viewers")
    private long uniqueViewers;

    @Column(name = "total_watch_time_seconds")
    private long totalWatchTimeSeconds;

    @Column(name = "avg_watch_time_seconds")
    private double avgWatchTimeSeconds;

    @Column(name = "completion_rate")
    private double completionRate;

    @Column(name = "last_updated_at")
    private java.time.Instant lastUpdatedAt;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public long getTotalViews() { return totalViews; }
    public long getTotalPlays() { return totalPlays; }
    public long getTotalPauses() { return totalPauses; }
    public long getTotalSeeks() { return totalSeeks; }
    public long getTotalCompletions() { return totalCompletions; }
    public long getTotalDropOffs() { return totalDropOffs; }
    public long getUniqueViewers() { return uniqueViewers; }
    public long getTotalWatchTimeSeconds() { return totalWatchTimeSeconds; }
    public double getAvgWatchTimeSeconds() { return avgWatchTimeSeconds; }
    public double getCompletionRate() { return completionRate; }
    public java.time.Instant getLastUpdatedAt() { return lastUpdatedAt; }
}
