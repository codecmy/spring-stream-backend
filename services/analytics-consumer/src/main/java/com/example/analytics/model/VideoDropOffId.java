package com.example.analytics.model;

import java.io.Serializable;
import java.util.Objects;

public class VideoDropOffId implements Serializable {
    private String videoId;
    private int bucketPercent;

    public VideoDropOffId() {}

    public VideoDropOffId(String videoId, int bucketPercent) {
        this.videoId = videoId;
        this.bucketPercent = bucketPercent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoDropOffId that = (VideoDropOffId) o;
        return bucketPercent == that.bucketPercent && Objects.equals(videoId, that.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoId, bucketPercent);
    }
}
