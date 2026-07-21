package com.example.spring_stream_backend.Entity;

import java.io.Serializable;
import java.util.Objects;

public class VideoDropOffLogId implements Serializable {
    private String videoId;
    private int bucketPercent;

    public VideoDropOffLogId() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoDropOffLogId that = (VideoDropOffLogId) o;
        return bucketPercent == that.bucketPercent && Objects.equals(videoId, that.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoId, bucketPercent);
    }
}
