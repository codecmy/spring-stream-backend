package com.example.spring_stream_backend.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "yt_video")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {
    @Id
    private String videoId;
    private String videoName;
    private String title;
    private String videoDescription;
    private String filepath;
    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Courses courses;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    private VideoType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id")
    @JsonIgnoreProperties({"videos", "series"})
    private Playlist playlist;

    @Column(name = "episode_number")
    private Integer episodeNumber;

    private Integer duration;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    private String language;

    @Column(name = "imdb_rating")
    private BigDecimal imdbRating;

    @Column(columnDefinition = "TEXT")
    private String story;

    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath;

    @ElementCollection(targetClass = GenreEnum.class)
    @CollectionTable(name = "videogenres", joinColumns = @JoinColumn(name = "video_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "genre")
    private Set<GenreEnum> genres = new HashSet<>();

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("video")
    private List<VideoCast> cast = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
