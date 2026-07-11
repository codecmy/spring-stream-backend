package com.example.spring_stream_backend.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cast_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CastMember {
    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @OneToMany(mappedBy = "castMember")
    @JsonIgnoreProperties("castMember")
    private List<VideoCast> videoCast = new ArrayList<>();
}
