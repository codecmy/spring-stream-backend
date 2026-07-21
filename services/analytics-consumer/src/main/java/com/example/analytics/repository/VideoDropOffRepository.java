package com.example.analytics.repository;

import com.example.analytics.model.VideoDropOff;
import com.example.analytics.model.VideoDropOffId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoDropOffRepository extends JpaRepository<VideoDropOff, VideoDropOffId> {

    List<VideoDropOff> findByVideoIdOrderByBucketPercentAsc(String videoId);
}
