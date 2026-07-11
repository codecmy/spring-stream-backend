package com.example.spring_stream_backend.repositories;

import com.example.spring_stream_backend.Entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeriesRepository extends JpaRepository<Series, String> {
}
