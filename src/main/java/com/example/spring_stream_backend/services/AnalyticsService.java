package com.example.spring_stream_backend.services;

import com.example.spring_stream_backend.Entity.*;
import com.example.spring_stream_backend.repositories.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private final VideoEventLogRepository eventRepository;
    private final AnalyticsSummaryRepository summaryRepository;
    private final VideoDropOffLogRepository dropOffRepository;
    private final VideoRepositories videoRepositories;

    public AnalyticsService(VideoEventLogRepository eventRepository,
                            AnalyticsSummaryRepository summaryRepository,
                            VideoDropOffLogRepository dropOffRepository,
                            VideoRepositories videoRepositories) {
        this.eventRepository = eventRepository;
        this.summaryRepository = summaryRepository;
        this.dropOffRepository = dropOffRepository;
        this.videoRepositories = videoRepositories;
    }

    public Map<String, Object> getOverview(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<AnalyticsSummary> summaries = summaryRepository.findAllByOrderByTotalViewsDesc();
        long totalViews = summaries.stream().mapToLong(AnalyticsSummary::getTotalViews).sum();
        long totalWatchTime = summaries.stream().mapToLong(AnalyticsSummary::getTotalWatchTimeSeconds).sum();
        double avgCompletion = summaries.stream()
                .filter(s -> s.getTotalPlays() > 0)
                .mapToDouble(AnalyticsSummary::getCompletionRate)
                .average().orElse(0.0);

        List<Object[]> dailyViews = eventRepository.dailyViewCountsSince(since);
        List<Map<String, Object>> viewsOverTime = dailyViews.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", row[0].toString());
            m.put("views", row[1]);
            return m;
        }).collect(Collectors.toList());

        List<Object[]> topVideosRaw = eventRepository.topViewedVideosSince(since, PageRequest.of(0, 10));
        List<Map<String, Object>> topVideos = topVideosRaw.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String videoId = (String) row[0];
            m.put("videoId", videoId);
            m.put("views", row[1]);
            Video video = videoRepositories.findById(videoId).orElse(null);
            m.put("title", video != null ? video.getTitle() : "Unknown");
            return m;
        }).collect(Collectors.toList());

        List<Object[]> eventBreakdown = eventRepository.countByEventTypeSince(since);
        Map<String, Long> eventCounts = eventBreakdown.stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalViews", totalViews);
        overview.put("totalWatchTimeHours", Math.round(totalWatchTime / 3600.0 * 100.0) / 100.0);
        overview.put("avgCompletionRate", Math.round(avgCompletion * 10000.0) / 100.0);
        overview.put("totalVideos", summaries.size());
        overview.put("viewsOverTime", viewsOverTime);
        overview.put("topVideos", topVideos);
        overview.put("eventBreakdown", eventCounts);
        return overview;
    }

    public List<Map<String, Object>> getVideoAnalyticsList() {
        List<AnalyticsSummary> summaries = summaryRepository.findAllByOrderByTotalViewsDesc();
        return summaries.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("videoId", s.getVideoId());
            m.put("totalViews", s.getTotalViews());
            m.put("totalPlays", s.getTotalPlays());
            m.put("totalCompletions", s.getTotalCompletions());
            m.put("totalDropOffs", s.getTotalDropOffs());
            m.put("uniqueViewers", s.getUniqueViewers());
            m.put("avgWatchTimeSeconds", Math.round(s.getAvgWatchTimeSeconds()));
            m.put("completionRate", Math.round(s.getCompletionRate() * 10000.0) / 100.0);
            m.put("totalWatchTimeSeconds", s.getTotalWatchTimeSeconds());
            Video video = videoRepositories.findById(s.getVideoId()).orElse(null);
            m.put("title", video != null ? video.getTitle() : "Unknown");
            m.put("lastUpdatedAt", s.getLastUpdatedAt());
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getVideoDetail(String videoId, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        AnalyticsSummary summary = summaryRepository.findById(videoId).orElse(null);
        if (summary == null) {
            return Map.of("videoId", videoId, "error", "No analytics data");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("videoId", videoId);
        Video video = videoRepositories.findById(videoId).orElse(null);
        detail.put("title", video != null ? video.getTitle() : "Unknown");
        detail.put("totalViews", summary.getTotalViews());
        detail.put("totalPlays", summary.getTotalPlays());
        detail.put("totalPauses", summary.getTotalPauses());
        detail.put("totalSeeks", summary.getTotalSeeks());
        detail.put("totalCompletions", summary.getTotalCompletions());
        detail.put("totalDropOffs", summary.getTotalDropOffs());
        detail.put("uniqueViewers", summary.getUniqueViewers());
        detail.put("avgWatchTimeSeconds", Math.round(summary.getAvgWatchTimeSeconds()));
        detail.put("completionRate", Math.round(summary.getCompletionRate() * 10000.0) / 100.0);
        detail.put("totalWatchTimeSeconds", summary.getTotalWatchTimeSeconds());

        List<Object[]> dailyViews = eventRepository.dailyViewsForVideoSince(videoId, since);
        detail.put("viewsOverTime", dailyViews.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", row[0].toString());
            m.put("views", row[1]);
            return m;
        }).collect(Collectors.toList()));

        List<Object[]> qualityDist = eventRepository.qualityDistributionByVideoId(videoId);
        detail.put("qualityDistribution", qualityDist.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quality", row[0] != null ? row[0] : "Unknown");
            m.put("count", row[1]);
            return m;
        }).collect(Collectors.toList()));

        List<Object[]> deviceDist = eventRepository.deviceDistributionByVideoId(videoId);
        detail.put("deviceBreakdown", deviceDist.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("device", row[0] != null ? row[0] : "Unknown");
            m.put("count", row[1]);
            return m;
        }).collect(Collectors.toList()));

        List<VideoDropOffLog> dropOffs = dropOffRepository.findByVideoIdOrderByBucketPercentAsc(videoId);
        detail.put("dropOffCurve", dropOffs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bucket", d.getBucketPercent() + "%-" + (d.getBucketPercent() + 10) + "%");
            m.put("dropOffs", d.getDropOffCount());
            return m;
        }).collect(Collectors.toList()));

        return detail;
    }
}
