package com.example.analytics.repository;

import com.example.analytics.model.VideoEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VideoEventRepository extends JpaRepository<VideoEventEntity, Long> {

    long countByVideoIdAndEventType(String videoId, com.example.analytics.model.EventType eventType);

    @Query("SELECT COUNT(DISTINCT e.userId) FROM VideoEventEntity e WHERE e.videoId = :videoId AND e.userId IS NOT NULL")
    long countDistinctViewersByVideoId(@Param("videoId") String videoId);

    @Query("SELECT COALESCE(SUM(e.durationSeconds), 0) FROM VideoEventEntity e WHERE e.videoId = :videoId AND e.durationSeconds IS NOT NULL")
    long sumDurationByVideoId(@Param("videoId") String videoId);

    @Query("SELECT e.eventType, COUNT(e) FROM VideoEventEntity e WHERE e.eventTimestamp >= :since GROUP BY e.eventType")
    List<Object[]> countByEventTypeSince(@Param("since") Instant since);

    @Query("SELECT e.videoId, COUNT(e) FROM VideoEventEntity e WHERE e.eventType = 'VIEW' AND e.eventTimestamp >= :since GROUP BY e.videoId ORDER BY COUNT(e) DESC")
    List<Object[]> topViewedVideosSince(@Param("since") Instant since, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT FUNCTION('DATE', e.eventTimestamp), COUNT(e) FROM VideoEventEntity e WHERE e.eventType = 'VIEW' AND e.eventTimestamp >= :since GROUP BY FUNCTION('DATE', e.eventTimestamp) ORDER BY FUNCTION('DATE', e.eventTimestamp)")
    List<Object[]> dailyViewCountsSince(@Param("since") Instant since);

    @Query("SELECT e.quality, COUNT(e) FROM VideoEventEntity e WHERE e.videoId = :videoId AND e.eventType = 'PLAY' GROUP BY e.quality")
    List<Object[]> qualityDistributionByVideoId(@Param("videoId") String videoId);

    @Query("SELECT e.deviceType, COUNT(e) FROM VideoEventEntity e WHERE e.videoId = :videoId GROUP BY e.deviceType")
    List<Object[]> deviceDistributionByVideoId(@Param("videoId") String videoId);

    @Query("SELECT FUNCTION('DATE', e.eventTimestamp), COUNT(e) FROM VideoEventEntity e WHERE e.videoId = :videoId AND e.eventType = 'VIEW' AND e.eventTimestamp >= :since GROUP BY FUNCTION('DATE', e.eventTimestamp) ORDER BY FUNCTION('DATE', e.eventTimestamp)")
    List<Object[]> dailyViewsForVideoSince(@Param("videoId") String videoId, @Param("since") Instant since);

    @Query("SELECT e.positionSeconds, COUNT(DISTINCT e.sessionId) FROM VideoEventEntity e WHERE e.videoId = :videoId AND e.eventType = 'DROP_OFF' GROUP BY e.positionSeconds ORDER BY e.positionSeconds")
    List<Object[]> dropOffPositionsByVideoId(@Param("videoId") String videoId);

    long countByEventTimestampAfter(Instant since);
}
