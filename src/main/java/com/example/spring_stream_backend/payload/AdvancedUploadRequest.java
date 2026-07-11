package com.example.spring_stream_backend.payload;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedUploadRequest {
    private String type;
    private String title;
    private String description;
    private String story;
    private Integer duration;
    private String releaseDate;
    private String language;
    private BigDecimal imdbRating;
    private List<String> genres;
    private List<CastEntry> cast;
    private String seriesId;
    private String playlistId;
    private Integer episodeNumber;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CastEntry {
        private String name;
        private String role;
    }
}
