package com.example.spring_stream_backend.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;

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
    @ManyToOne
    @JoinColumn(name = "course_id")
    private Courses courses;
}
