package com.example.spring_stream_backend.controllers;

import com.example.spring_stream_backend.Entity.Playlist;
import com.example.spring_stream_backend.Entity.Series;
import com.example.spring_stream_backend.repositories.PlaylistRepository;
import com.example.spring_stream_backend.repositories.SeriesRepository;
import com.example.spring_stream_backend.services.MetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/series")
public class SeriesController {

    private final SeriesRepository seriesRepository;
    private final PlaylistRepository playlistRepository;
    private final MetadataService metadataService;

    public SeriesController(SeriesRepository seriesRepository,
                            PlaylistRepository playlistRepository,
                            MetadataService metadataService) {
        this.seriesRepository = seriesRepository;
        this.playlistRepository = playlistRepository;
        this.metadataService = metadataService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listSeries() {
        List<Series> seriesList = seriesRepository.findAll();
        List<Map<String, Object>> result = seriesList.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("title", s.getTitle());
            m.put("description", s.getDescription());
            m.put("studios", s.getStudios());
            m.put("playlistCount", s.getPlaylists() != null ? s.getPlaylists().size() : 0);
            m.put("createdAt", s.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSeries(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }
        Series series = metadataService.createSeries(
                title,
                body.get("description"),
                body.get("studios")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", series.getId(),
                "title", series.getTitle(),
                "message", "Series created"
        ));
    }

    @GetMapping("/{seriesId}/playlists")
    public ResponseEntity<List<Map<String, Object>>> listPlaylists(@PathVariable String seriesId) {
        Optional<Series> seriesOpt = seriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Playlist> playlists = playlistRepository.findBySeriesId(seriesId);
        List<Map<String, Object>> result = playlists.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("title", p.getTitle());
            m.put("seasonNumber", p.getSeasonNumber());
            m.put("noOfVideos", p.getNoOfVideos() != null ? p.getNoOfVideos() : 0);
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
