package com.example.spring_stream_backend.controllers;

import com.example.spring_stream_backend.Entity.Playlist;
import com.example.spring_stream_backend.services.MetadataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/playlists")
public class PlaylistController {

    private final MetadataService metadataService;

    public PlaylistController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPlaylist(@RequestBody Map<String, Object> body) {
        String seriesId = (String) body.get("seriesId");
        Integer seasonNumber = body.get("seasonNumber") instanceof Integer
                ? (Integer) body.get("seasonNumber")
                : null;
        String title = (String) body.get("title");

        if (seriesId == null || seriesId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "seriesId is required"));
        }
        if (seasonNumber == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "seasonNumber is required"));
        }

        try {
            Playlist playlist = metadataService.createPlaylist(seriesId, seasonNumber, title);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", playlist.getId(),
                    "seasonNumber", playlist.getSeasonNumber(),
                    "title", playlist.getTitle() != null ? playlist.getTitle() : "",
                    "message", "Playlist created"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
