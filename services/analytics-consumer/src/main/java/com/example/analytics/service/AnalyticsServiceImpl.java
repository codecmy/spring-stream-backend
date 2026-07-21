package com.example.analytics.service;

import com.example.analytics.model.EventType;
import com.example.analytics.model.VideoAnalyticsSummary;
import com.example.analytics.model.VideoDropOff;
import com.example.analytics.model.VideoDropOffId;
import com.example.analytics.model.VideoEventEntity;
import com.example.analytics.model.VideoSessionState;
import com.example.analytics.model.VideoSessionStateId;
import com.example.analytics.repository.VideoAnalyticsSummaryRepository;
import com.example.analytics.repository.VideoDropOffRepository;
import com.example.analytics.repository.VideoEventRepository;
import com.example.analytics.repository.VideoSessionStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsServiceImpl.class);
    private static final long MAX_DELTA_SECONDS = 300;
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final VideoEventRepository eventRepository;
    private final VideoAnalyticsSummaryRepository summaryRepository;
    private final VideoDropOffRepository dropOffRepository;
    private final VideoSessionStateRepository sessionStateRepository;

    public AnalyticsServiceImpl(VideoEventRepository eventRepository,
                                VideoAnalyticsSummaryRepository summaryRepository,
                                VideoDropOffRepository dropOffRepository,
                                VideoSessionStateRepository sessionStateRepository) {
        this.eventRepository = eventRepository;
        this.summaryRepository = summaryRepository;
        this.dropOffRepository = dropOffRepository;
        this.sessionStateRepository = sessionStateRepository;
    }

    @Override
    @Transactional
    public void processEvent(String videoId, String userId, String sessionId, EventType eventType,
                             Long position, Long duration, Long seekFrom, Long seekTo,
                             String deviceType, String quality, long eventTimestampEpoch) {
        VideoEventEntity entity = new VideoEventEntity();
        entity.setVideoId(videoId);
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setEventType(eventType);
        entity.setPositionSeconds(position);
        entity.setDurationSeconds(duration);
        entity.setSeekFrom(seekFrom);
        entity.setSeekTo(seekTo);
        entity.setDeviceType(deviceType);
        entity.setQuality(quality);
        entity.setEventTimestamp(Instant.ofEpochMilli(eventTimestampEpoch));
        eventRepository.save(entity);

        updateSummary(videoId, userId, sessionId, eventType, position, duration, Instant.ofEpochMilli(eventTimestampEpoch));

        if (eventType == EventType.DROP_OFF && position != null && duration != null && duration > 0) {
            double percentWatched = (double) position / duration;
            if (percentWatched < 0.95) {
                updateDropOff(videoId, position, duration);
            }
        }

        log.debug("Processed event: type={} video={} session={}", eventType, videoId, sessionId);
    }

    private void updateSummary(String videoId, String userId, String sessionId, EventType eventType,
                               Long position, Long duration, Instant eventTimestamp) {
        VideoAnalyticsSummary summary = summaryRepository.findById(videoId)
                .orElseGet(() -> {
                    VideoAnalyticsSummary s = new VideoAnalyticsSummary();
                    s.setVideoId(videoId);
                    return s;
                });

        switch (eventType) {
            case VIEW -> summary.setTotalViews(summary.getTotalViews() + 1);
            case PLAY -> summary.setTotalPlays(summary.getTotalPlays() + 1);
            case PAUSE -> summary.setTotalPauses(summary.getTotalPauses() + 1);
            case SEEK -> summary.setTotalSeeks(summary.getTotalSeeks() + 1);
            case COMPLETE -> summary.setTotalCompletions(summary.getTotalCompletions() + 1);
            case DROP_OFF -> summary.setTotalDropOffs(summary.getTotalDropOffs() + 1);
        }

        long watchTimeDelta = computeWatchTimeDelta(videoId, sessionId, eventType, position, duration, eventTimestamp);

        if (watchTimeDelta > 0) {
            long newTotal = summary.getTotalWatchTimeSeconds() + watchTimeDelta;
            summary.setTotalWatchTimeSeconds(newTotal);
            if (summary.getTotalViews() > 0) {
                summary.setAvgWatchTimeSeconds((double) newTotal / summary.getTotalViews());
            }
        }

        if (summary.getTotalViews() > 0) {
            summary.setCompletionRate((double) summary.getTotalCompletions() / summary.getTotalViews());
        }

        summaryRepository.save(summary);
    }

    private long computeWatchTimeDelta(String videoId, String sessionId, EventType eventType,
                                       Long position, Long duration, Instant eventTimestamp) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "unknown";
        }

        if (eventType == EventType.COMPLETE && duration != null && duration > 0) {
            VideoSessionStateId stateId = new VideoSessionStateId(videoId, sessionId);
            sessionStateRepository.deleteById(stateId);
            return duration;
        }

        if (position == null || position <= 0) {
            return 0;
        }

        VideoSessionStateId stateId = new VideoSessionStateId(videoId, sessionId);
        VideoSessionState state = sessionStateRepository.findById(stateId).orElse(null);

        long delta = 0;

        if (state != null) {
            boolean sessionValid = Duration.between(state.getLastTimestamp(), eventTimestamp).compareTo(SESSION_TIMEOUT) < 0;
            if (sessionValid) {
                long diff = position - state.getLastPositionSeconds();
                if (diff > 0 && diff <= MAX_DELTA_SECONDS) {
                    delta = diff;
                }
            }
        } else if (eventType == EventType.DROP_OFF) {
            delta = Math.min(position, MAX_DELTA_SECONDS);
        }

        VideoSessionState newState = new VideoSessionState();
        newState.setVideoId(videoId);
        newState.setSessionId(sessionId);
        newState.setLastPositionSeconds(position);
        newState.setLastTimestamp(eventTimestamp);
        sessionStateRepository.save(newState);

        return delta;
    }

    private void updateDropOff(String videoId, long position, long duration) {
        if (duration <= 0) return;
        int bucket = (int) Math.min(((position * 100) / duration / 10) * 10, 90);

        VideoDropOffId dropOffId = new VideoDropOffId(videoId, bucket);
        VideoDropOff dropOff = dropOffRepository.findById(dropOffId)
                .orElseGet(() -> {
                    VideoDropOff d = new VideoDropOff();
                    d.setVideoId(videoId);
                    d.setBucketPercent(bucket);
                    return d;
                });

        dropOff.setDropOffCount(dropOff.getDropOffCount() + 1);
        dropOffRepository.save(dropOff);
    }
}
