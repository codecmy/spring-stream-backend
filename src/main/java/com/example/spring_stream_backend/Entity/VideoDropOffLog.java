package com.example.spring_stream_backend.Entity;

import jakarta.persistence.*;

@Entity
@Table(name = "video_drop_off")
@IdClass(VideoDropOffLogId.class)
public class VideoDropOffLog {

    @Id
    @Column(name = "video_id", nullable = false, length = 64)
    private String videoId;

    @Id
    @Column(name = "bucket_percent", nullable = false)
    private int bucketPercent;

    @Column(name = "session_count")
    private long sessionCount;

    @Column(name = "drop_off_count")
    private long dropOffCount;

    public String getVideoId() { return videoId; }
    public int getBucketPercent() { return bucketPercent; }
    public long getSessionCount() { return sessionCount; }
    public long getDropOffCount() { return dropOffCount; }
}
