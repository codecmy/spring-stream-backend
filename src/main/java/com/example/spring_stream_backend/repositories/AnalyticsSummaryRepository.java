package com.example.spring_stream_backend.repositories;

import com.example.spring_stream_backend.Entity.AnalyticsSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalyticsSummaryRepository extends JpaRepository<AnalyticsSummary, String> {
    List<AnalyticsSummary> findAllByOrderByTotalViewsDesc();
}
