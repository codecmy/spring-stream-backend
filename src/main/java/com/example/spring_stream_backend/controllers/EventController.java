package com.example.spring_stream_backend.controllers;

import com.example.spring_stream_backend.Entity.User;
import com.example.spring_stream_backend.payload.VideoEvent;
import com.example.spring_stream_backend.services.impl.KafkaEventProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final KafkaEventProducer kafkaEventProducer;

    public EventController(KafkaEventProducer kafkaEventProducer) {
        this.kafkaEventProducer = kafkaEventProducer;
    }

    @PostMapping
    public ResponseEntity<?> trackEvent(@RequestBody VideoEvent event, Authentication authentication) {
        if (event.getEventType() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventType is required"));
        }
        if (event.getVideoId() == null || event.getVideoId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "videoId is required"));
        }

        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            event.setUserId(user.getId());
        }

        if (event.getTimestamp() == null) {
            event.setTimestamp(java.time.Instant.now());
        }

        kafkaEventProducer.publishEvent(event);
        return ResponseEntity.accepted().body(Map.of("status", "event_accepted"));
    }
}
