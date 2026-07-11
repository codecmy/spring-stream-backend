package com.example.spring_stream_backend.services.impl;

import com.example.spring_stream_backend.Entity.*;
import com.example.spring_stream_backend.payload.AdvancedUploadRequest;
import com.example.spring_stream_backend.repositories.*;
import com.example.spring_stream_backend.services.MetadataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MetadataServiceImpl implements MetadataService {

    private final CastMemberRepository castMemberRepository;
    private final SeriesRepository seriesRepository;
    private final PlaylistRepository playlistRepository;

    public MetadataServiceImpl(CastMemberRepository castMemberRepository,
                               SeriesRepository seriesRepository,
                               PlaylistRepository playlistRepository) {
        this.castMemberRepository = castMemberRepository;
        this.seriesRepository = seriesRepository;
        this.playlistRepository = playlistRepository;
    }

    @Override
    public Set<GenreEnum> resolveGenres(List<String> genreNames) {
        if (genreNames == null || genreNames.isEmpty()) return new HashSet<>();
        Set<GenreEnum> genres = new HashSet<>();
        for (String name : genreNames) {
            try {
                genres.add(GenreEnum.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // skip unknown genre names
            }
        }
        return genres;
    }

    @Override
    public List<VideoCast> buildCastList(Video video, List<AdvancedUploadRequest.CastEntry> entries) {
        List<VideoCast> result = new ArrayList<>();
        if (entries == null || entries.isEmpty()) return result;

        for (AdvancedUploadRequest.CastEntry entry : entries) {
            if (entry.getName() == null || entry.getName().isBlank()) continue;
            CastMember member = castMemberRepository.findByName(entry.getName().trim())
                    .orElseGet(() -> castMemberRepository.save(
                            CastMember.builder()
                                    .id(UUID.randomUUID().toString())
                                    .name(entry.getName().trim())
                                    .build()
                    ));
            VideoCast vc = VideoCast.builder()
                    .video(video)
                    .castMember(member)
                    .roleName(entry.getRole() != null ? entry.getRole().trim() : null)
                    .build();
            result.add(vc);
        }
        return result;
    }

    @Override
    @Transactional
    public Series createSeries(String title, String description, String studios) {
        Series series = Series.builder()
                .id(UUID.randomUUID().toString())
                .title(title)
                .description(description)
                .studios(studios)
                .build();
        return seriesRepository.save(series);
    }

    @Override
    @Transactional
    public Playlist createPlaylist(String seriesId, Integer seasonNumber, String title) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("Series not found: " + seriesId));
        Playlist playlist = Playlist.builder()
                .id(UUID.randomUUID().toString())
                .series(series)
                .seasonNumber(seasonNumber)
                .title(title)
                .build();
        return playlistRepository.save(playlist);
    }
}
