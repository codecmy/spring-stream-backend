# Spring Stream Backend

A full-stack video streaming application built with Spring Boot, featuring HLS video streaming, MinIO object storage, and a Netflix-inspired frontend.

## Features

- **Video Upload & Storage** - Upload videos stored in MinIO (S3-compatible)
- **HLS Streaming** - Adaptive bitrate streaming using HLS (.m3u8 playlists with .ts segments)
- **Video Playback** - HTML5 video player with hls.js
- **RESTful API** - Full CRUD operations for videos
- **MySQL Database** - Persistent storage for video metadata
- **RabbitMQ** - Message queue for async processing
- **nginx** - Reverse proxy with caching for improved performance

## Architecture

### Video Transcoding Pipeline

![Video Transcoding Pipeline](./src/main/resources/static/Screenshot%202026-03-30%20120250.png)

The video transcoding pipeline follows an asynchronous, event-driven architecture:

1. **Upload**: Consumer uploads video through API servers
2. **Storage**: Video is pushed to MinIO local bucket
3. **Metadata**: Video metadata is stored in MySQL database
4. **Queue**: Upload triggers a message to RabbitMQ (decoupled & async)
5. **Transcode**: Multiple consumer workers (docker containers) fetch from queue, transcode videos using FFmpeg
6. **Output**: Transcoded HLS segments and playlists are stored in MinIO central storage bucket

### MinIO Bucket Structure

![MinIO Bucket Structure](./src/main/resources/static/Screenshot%202026-03-30%20131618.png)

MinIO stores objects in two main buckets:

| Bucket | Purpose | Contents |
|--------|---------|----------|
| `videos` | Raw video storage | Uploaded source video files |
| `video-hls` | HLS streaming assets | Transcoded `.m3u8` playlists and `.ts` segments |

Each transcoded video is organized in a folder named after the original file UUID:
```
video-hls/
├── {uuid}_filename.mp4/
│   ├── master.m3u8        # HLS master playlist
│   ├── segment_000.ts     # Video segment 1
│   ├── segment_001.ts     # Video segment 2
│   └── ...
```

### System Overview

```
┌─────────────┐     ┌───────────┐     ┌────────────┐
│   Frontend  │────▶│  nginx    │────▶│  Backend   │
│   (HTML/JS) │     │ :80       │     │  :8081     │
└─────────────┘     └───────────┘     └────────────┘
                                               │
                    ┌───────────┐     ┌────────────┤
                    │  MinIO    │◀────│  (API)     │
                    │ :9000    │     └────────────┘
                    └───────────
```
┌─────────────┐     ┌───────────┐     ┌────────────┐
│   Frontend  │────▶│  nginx    │────▶│  Backend   │
│   (HTML/JS) │     │ :80       │     │  :8081     │
└─────────────┘     └───────────┘     └────────────┘
                                              │
                   ┌───────────┐     ┌────────────┤
                   │  MinIO    │◀────│  (API)     │
                   │ :9000    │     └────────────┘
                   └───────────┘
```

### Services

| Service | Port | Description |
|---------|------|-------------|
| Frontend | 80 | nginx serving the web UI |
| Backend API | 8081 | Spring Boot REST API |
| MySQL | 3306 | Database |
| MinIO | 9000 | S3-compatible storage |
| MinIO Console | 9001 | MinIO admin UI |
| RabbitMQ | 5672 | Message queue |
| RabbitMQ UI | 15672 | RabbitMQ management |

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+ (for local development)
- Maven (for building)

### Quick Start

```bash
# Start all services
docker compose up -d

# Check status
docker compose ps
```

### Access URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:80 | - |
| Backend API | http://localhost:8081 | - |
| MySQL | localhost:3307 | root/root |
| MinIO Console | http://localhost:9001 | admin/password |
| RabbitMQ UI | http://localhost:15672 | guest/guest |

## API Endpoints

### Video Operations

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| GET | `/api/v1/videos` | List all videos | No |
| POST | `/api/v1/videos` | Upload video | Yes |
| GET | `/api/v1/videos/{id}/master.m3u8` | Get HLS playlist | No |
| GET | `/api/v1/videos/{id}/{segment}.ts` | Get video segment | No |
| GET | `/api/v1/videos/stream/range/{id}` | Range streaming | No |

### Health Check

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Backend health |
| GET | `/nginx-health` | nginx health |

## Configuration

### Environment Variables

Create a `.env` file in the project root:

```env
# Database
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/streamdb
SPRING_DATASOURCE_USERNAME=streamuser
SPRING_DATASOURCE_PASSWORD=streampass

# MinIO
MINIO_URL=http://minio:9000
MINIO_ACCESS_KEY=admin
MINIO_SECRET_KEY=password
MINIO_BUCKET=videos
MINIO_HLS_BUCKET=video-hls

# Hanko Auth
HANKO_BASE_URL=https://your-hanko-instance.hanko.io
HANKO_COOKIE_NAME=hanko
```

### Hanko Authentication

The backend uses Hanko for authentication. Configure your Hanko instance URL in `.env`:

```
HANKO_BASE_URL=https://your-hanko-instance.hanko.io
```

The Hanko session cookie is validated on protected endpoints.

## Video Streaming Flow

1. **Upload**: Videos are uploaded via POST to `/api/v1/videos` and stored in MinIO
2. **Transcode**: Backend uses FFmpeg to create HLS segments (.ts files) and playlists (.m3u8)
3. **Playback**: Frontend requests HLS playlist, which loads segments progressively
4. **Caching**: nginx caches `.ts` segments for improved performance

## Known Issues & Limitations

### ⚠️ Caching Latency

**Issue**: Even with nginx caching enabled, video segments may take 2-6 seconds to load on first request.

**Cause**: 
- ngrok tunnel adds significant latency when accessing remotely
- Initial cache miss requires fetching from backend → MinIO

**Workarounds**:
1. Access via local network (http://localhost:80) instead of ngrok for lower latency
2. After first play, segments are cached by nginx reducing subsequent load times
3. Consider Cloudflare Tunnels for lower latency remote access

### Public Video Access

Currently, video LISTING and PLAYBACK is public (no auth required). Only UPLOAD requires authentication.

To make all video access private, update the HankoSessionFilter in the backend.

## Development

### Building from Source

```bash
# Build the application
./mvnw clean package -DskipTests

# Build Docker image
docker build -t spring-stream-backend:latest .
```

### Running Tests

```bash
./mvnw test
```

## Project Structure

```
├── src/
│   └── main/
│       ├── java/com/example/spring_stream_backend/
│       │   ├── config/         # Configuration classes
│       │   ├── controllers/  # REST controllers
│       │   ├── services/    # Business logic
│       │   ├── repositories/ # Data access
│       │   ├── filter/      # Security filters
│       │   └── auth/        # Authentication
│       └── resources/
│           └── application.properties
├── static/
│   └── index.html           # Frontend (StreamFlix UI)
├── docker-compose.yml
├── nginx.conf
├── Dockerfile
└── .env
```

## Troubleshooting

### Services not starting

```bash
# Check logs
docker compose logs

# Restart services
docker compose restart
```

### Database connection issues

```bash
# Check MySQL is ready
docker compose logs mysql

# Wait for MySQL to be healthy
docker compose ps
```

### Clear cached data

```bash
# Clear nginx cache
docker compose exec nginx rm -rf /var/cache/nginx/*

# Restart nginx
docker compose restart nginx
```

## License

This project is for educational purposes.