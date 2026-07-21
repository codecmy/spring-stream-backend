package com.example.spring_stream_backend.services.impl;

import com.example.spring_stream_backend.config.KafkaConfig;
import com.example.spring_stream_backend.payload.VideoEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaEventProducer {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void publishEvent(VideoEvent event) {
        try {
            String key = event.getVideoId();
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaConfig.TOPIC, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Event sent to Kafka: type={} video={} partition={} offset={}",
                                    event.getEventType(), event.getVideoId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error("Failed to send event to Kafka: type={} video={}",
                                    event.getEventType(), event.getVideoId(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize video event", e);
        }
    }
}
