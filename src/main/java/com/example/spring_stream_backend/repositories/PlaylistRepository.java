package com.example.spring_stream_backend.repositories;

import com.example.spring_stream_backend.Entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, String> {
    List<Playlist> findBySeriesId(String seriesId);
}
