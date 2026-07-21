package com.example.analytics.service;

import com.example.analytics.model.EventType;

public interface AnalyticsService {
    void processEvent(String videoId, String userId, String sessionId, EventType eventType,
                      Long position, Long duration, Long seekFrom, Long seekTo,
                      String deviceType, String quality, long eventTimestampEpoch);
}
