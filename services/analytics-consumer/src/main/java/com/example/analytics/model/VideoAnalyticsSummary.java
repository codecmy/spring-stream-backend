package com.example.analytics.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "video_analytics_summary")
public class VideoAnalyticsSummary {

    @Setter
    @Id
    @Column(length = 64)
    private String videoId;

    @Setter
    private long totalViews;
    @Setter
    private long totalPlays;
    @Setter
    private long totalPauses;
    @Setter
    private long totalSeeks;
    @Setter
    private long totalCompletions;
    @Setter
    private long totalDropOffs;
    private long uniqueViewers;
    @Setter
    private long totalWatchTimeSeconds;
    @Setter
    private double avgWatchTimeSeconds;
    @Setter
    private double completionRate;

    @Column(nullable = false)
    private Instant lastUpdatedAt;

    @PrePersist
    protected void onCreate() {
        lastUpdatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = Instant.now();
    }

    public void setUniqueViewers(long uniqueViewers) { this.uniqueViewers = uniqueViewers; }

}
