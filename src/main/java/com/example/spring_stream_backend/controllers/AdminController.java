package com.example.spring_stream_backend.controllers;

import com.example.spring_stream_backend.Entity.User;
import com.example.spring_stream_backend.Entity.Video;
import com.example.spring_stream_backend.Entity.Role;
import com.example.spring_stream_backend.payload.CustomMessage;
import com.example.spring_stream_backend.repositories.UserRepository;
import com.example.spring_stream_backend.repositories.VideoRepositories;
import com.example.spring_stream_backend.services.VideoServices;
import com.example.spring_stream_backend.services.impl.MinioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    public AdminController(VideoServices videoServices,
                           VideoRepositories videoRepositories,
                           UserRepository userRepository,
                           MinioService minioService) {
        this.videoServices = videoServices;
        this.videoRepositories = videoRepositories;
        this.userRepository = userRepository;
        this.minioService = minioService;
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
                                         @RequestBody Map<String, String> body) {
        Video video = videoServices.findById(videoId);
        if (video == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CustomMessage.builder().message("Video not found").success(false).build());
        }
        if (body.containsKey("title")) {
            video.setTitle(body.get("title"));
        }
        if (body.containsKey("description")) {
            video.setVideoDescription(body.get("description"));
        }
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
}
