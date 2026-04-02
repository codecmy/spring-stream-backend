package com.example.spring_stream_backend.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "yt_courses")
public class Courses{
    @Id
    private String id;

    private String title;
    @OneToMany(mappedBy = "courses", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Video> videos = new ArrayList<>();
}
// video-hls/f41a59fa-456b-4e3a-9648-b3002f70dcfa_13739696_540_960_30fps.mp4
//raw/f41a59fa-456b-4e3a-9648-b3002f70dcfa_13739696_540_960_30fps.mp4