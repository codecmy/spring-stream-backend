package com.example.spring_stream_backend.repositories;

import com.example.spring_stream_backend.Entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoRepositories extends JpaRepository<Video,String>{

}
