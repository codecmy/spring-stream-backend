package com.example.spring_stream_backend.controllers;

import com.example.spring_stream_backend.Entity.*;
import com.example.spring_stream_backend.payload.AdvancedUploadRequest;
import com.example.spring_stream_backend.payload.CustomMessage;
import com.example.spring_stream_backend.repositories.*;
import com.example.spring_stream_backend.services.AnalyticsService;
import com.example.spring_stream_backend.services.MetadataService;
import com.example.spring_stream_backend.services.VideoServices;
import com.example.spring_stream_backend.services.impl.MinioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final VideoServices videoServices;
    private final VideoRepositories videoRepositories;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final MetadataService metadataService;
    private final PlaylistRepository playlistRepository;
    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

    public AdminController(VideoServices videoServices,
                           VideoRepositories videoRepositories,
                           UserRepository userRepository,
                           MinioService minioService,
                           MetadataService metadataService,
                           PlaylistRepository playlistRepository,
                           ObjectMapper objectMapper,
                           AnalyticsService analyticsService) {
        this.videoServices = videoServices;
        this.videoRepositories = videoRepositories;
        this.userRepository = userRepository;
        this.minioService = minioService;
        this.metadataService = metadataService;
        this.playlistRepository = playlistRepository;
        this.objectMapper = objectMapper;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalVideos", videoRepositories.count());
        stats.put("totalUsers", userRepository.count());
        stats.put("adminCount", userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN).count());
        stats.put("recentVideos", videoServices.getAllVideo().stream()
                .limit(5)
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("videoId", v.getVideoId());
                    m.put("title", v.getTitle());
                    m.put("uploadedAt", v.getUploadedAt());
                    return m;
                })
                .collect(Collectors.toList()));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/videos")
    public ResponseEntity<List<Map<String, Object>>> listVideos() {
        List<Video> videos = videoServices.getAllVideo();
        List<Map<String, Object>> result = videos.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("videoId", v.getVideoId());
            m.put("title", v.getTitle());
            m.put("videoDescription", v.getVideoDescription());
            m.put("videoName", v.getVideoName());
            m.put("contentType", v.getContentType());
            m.put("uploadedAt", v.getUploadedAt());
            m.put("type", v.getType() != null ? v.getType().name() : null);
            if (v.getUploadedBy() != null) {
                m.put("uploadedBy", Map.of(
                        "id", v.getUploadedBy().getId(),
                        "email", v.getUploadedBy().getEmail()
                ));
            }
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/videos")
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file,
                                         @RequestParam("title") String title,
                                         @RequestParam("description") String desc,
                                         Authentication authentication) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(CustomMessage.builder().message("Video file is required").success(false).build());
        }

        User admin = (User) authentication.getPrincipal();
        Video video = new Video();
        video.setVideoId(UUID.randomUUID().toString());
        video.setVideoDescription(desc);
        video.setTitle(title);
        video.setUploadedBy(admin);
        video.setUploadedAt(LocalDateTime.now());

        try {
            videoServices.save(video, file);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("videoId", video.getVideoId(), "message", "Video uploaded successfully"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CustomMessage.builder().message("Video not uploaded: " + e.getMessage()).success(false).build());
        }
    }

    @PostMapping("/videos/advanced")
    public ResponseEntity<?> uploadVideoAdvanced(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String metadataJson,
            Authentication authentication) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(CustomMessage.builder().message("Video file is required").success(false).build());
        }

        AdvancedUploadRequest req;
        try {
            req = objectMapper.readValue(metadataJson, AdvancedUploadRequest.class);
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(CustomMessage.builder().message("Invalid metadata JSON: " + e.getMessage()).success(false).build());
        }

        if (req.getTitle() == null || req.getTitle().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(CustomMessage.builder().message("Title is required").success(false).build());
        }

        User admin = (User) authentication.getPrincipal();

        Video video = new Video();
        video.setVideoId(UUID.randomUUID().toString());
        video.setTitle(req.getTitle());
        video.setVideoDescription(req.getDescription());
        video.setUploadedBy(admin);
        video.setUploadedAt(LocalDateTime.now());

        VideoType type;
        try {
            type = req.getType() != null ? VideoType.valueOf(req.getType().toUpperCase()) : null;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(CustomMessage.builder().message("Invalid type: must be MOVIE or EPISODE").success(false).build());
        }
        video.setType(type);

        if (req.getDuration() != null) video.setDuration(req.getDuration());
        if (req.getReleaseDate() != null && !req.getReleaseDate().isBlank()) {
            try {
                video.setReleaseDate(LocalDate.parse(req.getReleaseDate()));
            } catch (Exception ignored) {}
        }
        if (req.getLanguage() != null) video.setLanguage(req.getLanguage());
        if (req.getImdbRating() != null) video.setImdbRating(req.getImdbRating());
        if (req.getStory() != null) video.setStory(req.getStory());

        if (req.getGenres() != null && !req.getGenres().isEmpty()) {
            video.setGenres(metadataService.resolveGenres(req.getGenres()));
        }

        if (type == VideoType.EPISODE) {
            if (req.getPlaylistId() != null) {
                Playlist playlist = playlistRepository.findById(req.getPlaylistId()).orElse(null);
                if (playlist == null) {
                    return ResponseEntity.badRequest()
                            .body(CustomMessage.builder().message("Playlist not found: " + req.getPlaylistId()).success(false).build());
                }
                video.setPlaylist(playlist);
            }
            if (req.getEpisodeNumber() != null) video.setEpisodeNumber(req.getEpisodeNumber());
        }

        try {
            videoServices.save(video, file);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CustomMessage.builder().message("Video upload failed: " + e.getMessage()).success(false).build());
        }

        if (req.getCast() != null && !req.getCast().isEmpty()) {
            List<VideoCast> castList = metadataService.buildCastList(video, req.getCast());
            video.setCast(castList);
            videoRepositories.save(video);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("videoId", video.getVideoId());
        response.put("title", video.getTitle());
        response.put("type", video.getType() != null ? video.getType().name() : null);
        response.put("message", "Video uploaded successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/videos/{videoId}")
    public ResponseEntity<?> deleteVideo(@PathVariable String videoId) {
        try {
            videoServices.deleteVideo(videoId);
            return ResponseEntity.ok(Map.of("message", "Video deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CustomMessage.builder().message("Failed to delete video: " + e.getMessage()).success(false).build());
        }
    }

    @PatchMapping("/videos/{videoId}")
    public ResponseEntity<?> updateVideo(@PathVariable String videoId,
                                         @RequestBody Map<String, Object> body) {
        Video video = videoServices.findById(videoId);
        if (video == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CustomMessage.builder().message("Video not found").success(false).build());
        }
        if (body.containsKey("title")) video.setTitle((String) body.get("title"));
        if (body.containsKey("description")) video.setVideoDescription((String) body.get("description"));
        if (body.containsKey("story")) video.setStory((String) body.get("story"));
        if (body.containsKey("language")) video.setLanguage((String) body.get("language"));
        if (body.containsKey("duration") && body.get("duration") instanceof Number)
            video.setDuration(((Number) body.get("duration")).intValue());
        if (body.containsKey("imdbRating") && body.get("imdbRating") instanceof Number)
            video.setImdbRating(BigDecimal.valueOf(((Number) body.get("imdbRating")).doubleValue()));
        videoRepositories.save(video);
        return ResponseEntity.ok(Map.of("message", "Video updated successfully"));
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("email", u.getEmail());
            m.put("role", u.getRole().name());
            m.put("createdAt", u.getCreatedAt());
            m.put("lastLoginAt", u.getLastLoginAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable String userId,
                                            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CustomMessage.builder().message("User not found").success(false).build());
        }
        String newRole = body.get("role");
        if (newRole == null || (!newRole.equals("ADMIN") && !newRole.equals("USER"))) {
            return ResponseEntity.badRequest()
                    .body(CustomMessage.builder().message("Role must be ADMIN or USER").success(false).build());
        }
        user.setRole(Role.valueOf(newRole));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User role updated to " + newRole));
    }

    @GetMapping("/analytics/overview")
    public ResponseEntity<Map<String, Object>> analyticsOverview(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getOverview(days));
    }

    @GetMapping("/analytics/videos")
    public ResponseEntity<List<Map<String, Object>>> analyticsVideos() {
        return ResponseEntity.ok(analyticsService.getVideoAnalyticsList());
    }

    @GetMapping("/analytics/videos/{videoId}")
    public ResponseEntity<?> analyticsVideoDetail(
            @PathVariable String videoId,
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> detail = analyticsService.getVideoDetail(videoId, days);
        if (detail.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
        }
        return ResponseEntity.ok(detail);
    }
}
