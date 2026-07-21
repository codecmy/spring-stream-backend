package com.example.analytics.model;

import jakarta.persistence.*;

@Entity
@Table(name = "video_drop_off")
@IdClass(VideoDropOffId.class)
public class VideoDropOff {

    @Id
    @Column(nullable = false, length = 64)
    private String videoId;

    @Id
    @Column(nullable = false)
    private int bucketPercent;

    private long sessionCount;
    private long dropOffCount;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public int getBucketPercent() { return bucketPercent; }
    public void setBucketPercent(int bucketPercent) { this.bucketPercent = bucketPercent; }

    public long getSessionCount() { return sessionCount; }
    public void setSessionCount(long sessionCount) { this.sessionCount = sessionCount; }

    public long getDropOffCount() { return dropOffCount; }
    public void setDropOffCount(long dropOffCount) { this.dropOffCount = dropOffCount; }
}
