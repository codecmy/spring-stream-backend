package com.example.analytics.model;

import java.io.Serializable;
import java.util.Objects;

public class VideoSessionStateId implements Serializable {
    private String videoId;
    private String sessionId;

    public VideoSessionStateId() {}

    public VideoSessionStateId(String videoId, String sessionId) {
        this.videoId = videoId;
        this.sessionId = sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoSessionStateId that = (VideoSessionStateId) o;
        return Objects.equals(videoId, that.videoId) && Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoId, sessionId);
    }
}
