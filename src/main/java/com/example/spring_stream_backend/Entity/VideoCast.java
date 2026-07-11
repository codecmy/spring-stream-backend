package com.example.spring_stream_backend.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "video_cast")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoCast {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    @JsonIgnore
    private Video video;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cast_member_id", nullable = false)
    private CastMember castMember;

    @Column(name = "role_name")
    private String roleName;
}
