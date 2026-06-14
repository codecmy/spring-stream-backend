package com.example.spring_stream_backend.services;

import com.example.spring_stream_backend.Entity.Video;
import io.minio.errors.*;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public interface VideoServices{
    Video save(Video video, MultipartFile file) throws IOException;
    Video findById(String id);
    Video findByTitle(String title);
    List<Video> getAllVideo();
    String processVideo(String videoId) throws IOException;
    InputStream getVideoSegement(String videoId, String segmentNo) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException;
    Resource getm3u8(String videoId) throws IOException;
    Resource getQualityPlaylist(String videoId, String quality) throws IOException;
    InputStream getVideoSegment(String videoId, String segmentNo, String quality) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException;
    void deleteVideo(String videoId) throws Exception;
    Resource getThumbnail(String videoId) throws IOException;
}
