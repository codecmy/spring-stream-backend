package com.example.analytics.repository;

import com.example.analytics.model.VideoSessionState;
import com.example.analytics.model.VideoSessionStateId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoSessionStateRepository extends JpaRepository<VideoSessionState, VideoSessionStateId> {
}
