package com.example.analytics.listener;

import com.example.analytics.model.EventType;
import com.example.analytics.service.AnalyticsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class VideoAnalyticsListener {
    private static final Logger log = LoggerFactory.getLogger(VideoAnalyticsListener.class);

    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    public VideoAnalyticsListener(AnalyticsService analyticsService, ObjectMapper objectMapper) {
        this.analyticsService = analyticsService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            id = "analytics-video-events-listener",
            topics = "video.events",
            groupId = "analytics-service"
    )
    public void onEvent(@Payload String message,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {
        try {
            log.info("Received message on partition={} offset={}: {}", partition, offset, message);
            JsonNode node = objectMapper.readTree(message);

            String eventTypeStr = node.get("eventType").asText();
            EventType eventType;
            try {
                eventType = EventType.valueOf(eventTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown event type: {}", eventTypeStr);
                ack.acknowledge();
                return;
            }

            analyticsService.processEvent(
                    node.get("videoId").asText(),
                    node.has("userId") && !node.get("userId").isNull() ? node.get("userId").asText() : null,
                    node.has("sessionId") ? node.get("sessionId").asText() : "unknown",
                    eventType,
                    node.has("position") && !node.get("position").isNull() ? node.get("position").asLong() : null,
                    node.has("duration") && !node.get("duration").isNull() ? node.get("duration").asLong() : null,
                    node.has("seekFrom") && !node.get("seekFrom").isNull() ? node.get("seekFrom").asLong() : null,
                    node.has("seekTo") && !node.get("seekTo").isNull() ? node.get("seekTo").asLong() : null,
                    node.has("deviceType") ? node.get("deviceType").asText() : null,
                    node.has("quality") ? node.get("quality").asText() : null,
                    node.has("timestamp") && !node.get("timestamp").isNull() ? java.time.Instant.parse(node.get("timestamp").asText()).toEpochMilli() : System.currentTimeMillis()
            );

            log.debug("Consumed event: type={} videoId={} partition={} offset={}", eventTypeStr, node.get("videoId").asText(), partition, offset);
        } catch (Exception e) {
            log.error("Failed to process analytics event from topic={} partition={} offset={}", topic, partition, offset, e);
        } finally {
            ack.acknowledge();
        }
    }
}
