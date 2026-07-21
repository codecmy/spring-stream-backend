package com.example.analytics.repository;

import com.example.analytics.model.VideoAnalyticsSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoAnalyticsSummaryRepository extends JpaRepository<VideoAnalyticsSummary, String> {

    List<VideoAnalyticsSummary> findAllByOrderByTotalViewsDesc();
}
