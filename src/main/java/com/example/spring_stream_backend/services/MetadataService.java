package com.example.spring_stream_backend.services;

import com.example.spring_stream_backend.Entity.*;
import com.example.spring_stream_backend.payload.AdvancedUploadRequest;

import java.util.List;
import java.util.Set;

public interface MetadataService {
    Set<GenreEnum> resolveGenres(List<String> genreNames);
    List<VideoCast> buildCastList(Video video, List<AdvancedUploadRequest.CastEntry> entries);
    Series createSeries(String title, String description, String studios);
    Playlist createPlaylist(String seriesId, Integer seasonNumber, String title);
}
