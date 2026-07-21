package com.example.spring_stream_backend.repositories;

import com.example.spring_stream_backend.Entity.VideoDropOffLog;
import com.example.spring_stream_backend.Entity.VideoDropOffLogId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoDropOffLogRepository extends JpaRepository<VideoDropOffLog, VideoDropOffLogId> {
    List<VideoDropOffLog> findByVideoIdOrderByBucketPercentAsc(String videoId);
}
