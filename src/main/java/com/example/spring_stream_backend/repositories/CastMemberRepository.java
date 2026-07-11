package com.example.spring_stream_backend.repositories;

import com.example.spring_stream_backend.Entity.CastMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CastMemberRepository extends JpaRepository<CastMember, String> {
    Optional<CastMember> findByName(String name);
}
