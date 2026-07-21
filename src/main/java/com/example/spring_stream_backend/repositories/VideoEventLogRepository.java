package com.example.spring_stream_backend.repositories;

import com.example.spring_stream_backend.Entity.VideoEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VideoEventLogRepository extends JpaRepository<VideoEventLog, Long> {

    long countByVideoIdAndEventType(String videoId, String eventType);

    long countByEventTimestampAfter(Instant since);

    @Query("SELECT e.eventType, COUNT(e) FROM VideoEventLog e WHERE e.eventTimestamp >= :since GROUP BY e.eventType")
    List<Object[]> countByEventTypeSince(@Param("since") Instant since);

    @Query("SELECT e.videoId, COUNT(e) FROM VideoEventLog e WHERE e.eventType = 'VIEW' AND e.eventTimestamp >= :since GROUP BY e.videoId ORDER BY COUNT(e) DESC")
    List<Object[]> topViewedVideosSince(@Param("since") Instant since, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT CAST(e.eventTimestamp AS LocalDate), COUNT(e) FROM VideoEventLog e WHERE e.eventType = 'VIEW' AND e.eventTimestamp >= :since GROUP BY CAST(e.eventTimestamp AS LocalDate) ORDER BY CAST(e.eventTimestamp AS LocalDate)")
    List<Object[]> dailyViewCountsSince(@Param("since") Instant since);

    @Query("SELECT e.quality, COUNT(e) FROM VideoEventLog e WHERE e.videoId = :videoId AND e.eventType = 'PLAY' GROUP BY e.quality")
    List<Object[]> qualityDistributionByVideoId(@Param("videoId") String videoId);

    @Query("SELECT e.deviceType, COUNT(e) FROM VideoEventLog e WHERE e.videoId = :videoId GROUP BY e.deviceType")
    List<Object[]> deviceDistributionByVideoId(@Param("videoId") String videoId);

    @Query("SELECT CAST(e.eventTimestamp AS LocalDate), COUNT(e) FROM VideoEventLog e WHERE e.videoId = :videoId AND e.eventType = 'VIEW' AND e.eventTimestamp >= :since GROUP BY CAST(e.eventTimestamp AS LocalDate) ORDER BY CAST(e.eventTimestamp AS LocalDate)")
    List<Object[]> dailyViewsForVideoSince(@Param("videoId") String videoId, @Param("since") Instant since);
}
