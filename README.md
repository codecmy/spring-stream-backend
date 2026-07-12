# Spring Stream Backend

A full-stack video streaming application built with Spring Boot, featuring multi-audio-track HLS streaming, JWT authentication, MinIO object storage, and a Netflix-inspired React frontend.

## Features

- **Video Upload & Storage** — Upload videos stored in MinIO (S3-compatible)
- **Multi-Audio-Track HLS Streaming** — Adaptive bitrate streaming with per-audio-track HLS playlists and quality variants
- **Audio Language Detection** — FFmpeg probes uploaded videos for audio tracks at upload time; language codes stored in DB
- **Audio Track Switching** — Frontend player supports switching between audio languages mid-playback without interrupting video
- **JWT Authentication** — Self-contained HMAC-SHA256 JWT auth (login/register/me)
- **Thumbnail Extraction** — Auto-generated first-frame thumbnails via FFmpeg on upload
- **Video Playback** — HTML5 video player with hls.js, Bearer token injected on every request
- **RESTful API** — Full CRUD operations for videos with role-based access (ADMIN/USER)
- **MySQL Database** — Persistent storage for video and user metadata
- **RabbitMQ** — Message queue for async transcoding by external worker
- **nginx** — Reverse proxy with segment caching (7 days for `.ts`, 60s for `.m3u8`)

## Architecture

### Video Transcoding Pipeline

The video transcoding pipeline follows an asynchronous, event-driven architecture:

1. **Upload** — Admin uploads video through API
2. **Probe** — Backend runs `ffprobe` to detect audio tracks and their language codes
3. **Thumbnail** — Backend extracts first frame via FFmpeg, stores in MinIO
4. **Storage** — Video pushed to MinIO `videos` bucket under `raw/{uuid}_{filename}`
5. **Metadata** — Video metadata (including `audioLanguages`) stored in MySQL
6. **Queue** — Upload triggers a RabbitMQ message (decoupled & async)
7. **Transcode** — External worker consumes message, runs multi-pass FFmpeg transcoding
8. **Output** — Transcoded HLS segments and playlists stored in MinIO `video-hls` bucket
9. **Serve** — Backend serves HLS assets from MinIO; nginx caches segments for 7 days

### Multi-Audio Transcoding

The worker runs one FFmpeg pass per detected audio track. Each pass produces 4 video quality variants (360p/480p/720p/1080p) muxed with that specific audio track:

```
FFmpeg filter_complex (per pass):
[0:v]split=4[v1][v2][v3][v4];
[v1]scale=640:-2[v360p]; [v2]scale=854:-2[v480p]; [v3]scale=1280:-2[v720p]; [v4]scale=1920:-2[v1080p];
[0:a:{N}]asplit=4[a1][a2][a3][a4];
-map "[v360p]" -map "[a1]" ...   (360p variant)
-map "[v480p]" -map "[a2]" ...   (480p variant)
-map "[v720p]" -map "[a3]" ...   (720p variant)
-map "[v1080p]" -map "[a4]" ...  (1080p variant)
```

Key: `[0:a:{N}]` selects the Nth audio stream from the source, ensuring each pass encodes the correct language.

### MinIO Bucket Structure

MinIO stores objects in two main buckets:

| Bucket | Purpose | Contents |
|--------|---------|----------|
| `videos` | Raw video storage | Source files (`raw/{uuid}_{filename}`), thumbnails (`thumbnails/{videoId}.jpg`) |
| `video-hls` | HLS streaming assets | Transcoded `.m3u8` playlists and `.ts` segments |

HLS assets are organized with audio subdirectories:

```
video-hls/
├── {uuid}_{filename}/
│   ├── master.m3u8                 # Main master playlist (points to a0/)
│   ├── a0/                         # Audio track 0 (e.g., Hindi)
│   │   ├── master.m3u8             # Per-audio master playlist
│   │   ├── v0/index.m3u8           # 360p quality playlist
│   │   ├── v0/segment_000.ts       # 360p segment
│   │   ├── v1/index.m3u8           # 480p quality playlist
│   │   ├── v2/index.m3u8           # 720p quality playlist
│   │   └── v3/index.m3u8           # 1080p quality playlist
│   ├── a1/                         # Audio track 1 (e.g., English)
│   │   ├── master.m3u8
│   │   ├── v0/ ... v3/             # Same 4 quality variants
│   └── a2/                         # Audio track 2 (e.g., Japanese)
│       ├── master.m3u8
│       └── v0/ ... v3/
```

Main `master.m3u8` format:
```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
a0/v0/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480
a0/v1/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720
a0/v2/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
a0/v3/index.m3u8
```

Per-audio `a{N}/master.m3u8` format:
```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
v0/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480
v1/index.m3u8
...
```

### Audio Switching (Frontend)

Audio track switching is implemented by destroying the current hls.js instance and creating a new one pointed at the target audio track's master playlist:

1. User selects a different audio track from the dropdown
2. Current playback position is saved
3. hls.js instance is destroyed
4. New hls.js instance created with `a{N}/master.m3u8` as source
5. Playback seeks to the saved position

### Auth Flow

```
┌──────────┐   POST /api/v1/auth/login    ┌──────────┐
│ Frontend │   ──────────────────────────▶  │ Backend  │
│  (React) │   { email, password }          │ :8081    │
│          │   ◀──────────────────────────  │          │
│          │   { token, user }              │          │
│          │                                │          │
│          │   GET /api/v1/videos           │          │
│          │   Authorization: Bearer <jwt>  │          │
│          │   ◀──────────────────────────  │          │
│          │   [ video list ]               │          │
└──────────┘                                └──────────┘
```

- JWT stored in `localStorage`, sent as `Authorization: Bearer <token>` header
- hls.js injects Bearer token via `xhrSetup` callback on every playlist/segment request
- Admin users seeded from `ADMIN_EMAILS` env var at startup

### Services

| Service | Port | Description |
|---------|------|-------------|
| Frontend (React SPA) | 80 | nginx serving the StreamFlix UI |
| Backend API | 8081 | Spring Boot REST API |
| MySQL | 3307 (host), 3306 (internal) | Database |
| MinIO | 9000 | S3-compatible object storage |
| MinIO Console | 9001 | MinIO admin UI |
| RabbitMQ | 5672 | Message queue |
| RabbitMQ UI | 15672 | RabbitMQ management |
| Worker | — | External transcoder (separate image) |

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+ (for local development)
- Maven (for building)

### Quick Start

```bash
# Build the backend
./mvnw clean package -DskipTests

# Build Docker image
docker build -t spring-stream-backend:latest .

# Build the worker
docker build -t transcoder-worker:latest ./services/transcoder-worker

# Build the frontend
cd streamflix && npm install && npm run build && cd ..

# Start all services
docker compose up -d

# Check status
docker compose ps
```

### Access URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend | http://localhost:80 | — |
| Backend API | http://localhost:8081 | — |
| MySQL | localhost:3307 | streamuser/streampass |
| MinIO Console | http://localhost:9001 | admin/password |
| RabbitMQ UI | http://localhost:15672 | guest/guest |

## API Endpoints

### Authentication

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| POST | `/api/v1/auth/login` | Login with email + password | No |
| POST | `/api/v1/auth/register` | Register new user | No |
| GET | `/api/v1/auth/me` | Get current user info | Yes |

### Video Operations

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| GET | `/api/v1/videos` | List all videos | Yes |
| POST | `/api/v1/videos` | Upload video (multipart: `file`, `title`, `description`) | ADMIN |
| GET | `/api/v1/videos/{id}/thumbnail` | Get video thumbnail (JPEG) | Yes |
| GET | `/api/v1/videos/{id}/master.m3u8` | Get HLS master playlist | Yes |
| GET | `/api/v1/videos/{id}/{*subPath}` | Get any HLS sub-resource (playlists, segments) | Yes |
| GET | `/api/v1/videos/stream/range/{id}` | Range-based byte streaming (MP4) | Yes |

The `{*subPath}` catch-all handles all HLS sub-resources, including audio-tracked paths:
- `a0/v0/index.m3u8` — 360p playlist for audio track 0
- `a1/v2/segment_001.ts` — 720p segment for audio track 1
- `a0/master.m3u8` — per-audio master playlist

Content-Type is set automatically based on file extension (`.m3u8` → `application/vnd.apple.mpegurl`, `.ts` → `video/mp2t`).

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

# JWT
JWT_SECRET=your-secret-key-change-in-production
JWT_EXPIRATION=86400000

# Admin seeding
ADMIN_EMAILS=admin@example.com
ADMIN_PASSWORD=your-admin-password

# RabbitMQ
SPRING_RABBITMQ_HOST=rabbitmq
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest
```

## Project Structure

```
├── src/main/java/com/example/spring_stream_backend/
│   ├── SpringStreamBackendApplication.java
│   ├── TestController.java                          # GET /health
│   ├── config/                                       # MinioConfig, RabbitConfig, CorsConfig, SecurityConfig
│   ├── controllers/VideoController.java              # All video REST endpoints
│   ├── filter/JwtAuthenticationFilter.java           # Reads Bearer token
│   ├── auth/                                         # JwtUtil, JwtAuthenticationProvider, AdminSeeder
│   ├── services/VideoServices.java                   # Interface
│   ├── services/impl/VideoServiceImpl.java           # Upload, HLS streaming, audio probing, MinIO operations
│   ├── services/impl/InputStreamResource.java        # Custom InputStream-based Resource
│   ├── Entity/Video.java                             # @Entity (yt_video) with audioLanguages field
│   ├── Entity/Courses.java                           # @Entity (yt_courses)
│   ├── repositories/VideoRepositories.java           # JpaRepository
│   ├── payload/CustomMessage.java                    # Response DTO
│   └── AppConstants.java                             # Chunk_size = 1MB
├── services/transcoder-worker/
│   └── src/main/java/com/example/demo/
│       ├── VideoWorkerApplication.java               # Worker entry point
│       ├── listener/VideoProcessingListener.java     # RabbitMQ consumer
│       └── service/VideoProcessingService.java       # Multi-pass FFmpeg transcoding + HLS upload
├── streamflix/                                       # React frontend (Vite + TailwindCSS)
│   ├── src/
│   │   ├── api/api.js                                # fetchVideos(), URL builders, searchVideos()
│   │   ├── store/useStore.js                         # Zustand store with JWT management
│   │   ├── components/                               # MovieCard, MovieCarousel, Navbar
│   │   └── pages/
│   │       ├── PlayerPage.jsx                        # HLS player with audio/quality switching
│   │       ├── HomePage.jsx                          # Video grid
│   │       └── LoginPage.jsx                         # Auth
│   └── dist/                                         # Built assets (served by nginx)
├── docker-compose.yml
├── nginx.conf                                        # Reverse proxy with segment caching
├── Dockerfile                                        # Multi-stage: Maven build + Alpine JDK + FFmpeg
├── AGENTS.md
└── .env
```

## Known Issues

### Property Key Mismatch

`application.properties` defines uppercase keys (`MINIO_URL`, `MINIO_ACCESS_KEY`, etc.) but Java code uses `@Value("${minio.url}")`. Spring Boot does NOT apply relaxed binding to `@Value`. If the app fails to start, add fallback values:
```properties
minio.url=${MINIO_URL:http://minio:9000}
minio.access-key=${MINIO_ACCESS_KEY:admin}
```

### Caching Latency

Initial segment load may take 2-6 seconds on first request (cache miss requires backend → MinIO). After first play, segments are cached by nginx for 7 days.

## Troubleshooting

### Services not starting

```bash
docker compose logs
docker compose restart
```

### Database connection issues

```bash
docker compose logs mysql
docker compose ps
```

### Clear nginx cache

```bash
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx
```

### Rebuild after code changes

```bash
# Backend
docker build -t spring-stream-backend:latest .
docker compose up -d backend

# Worker
docker build -t transcoder-worker:latest ./services/transcoder-worker
docker compose up -d worker

# Frontend
cd streamflix && npm run build && cd ..
docker cp streamflix/dist/. nginx:/usr/share/nginx/html/static/
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx
```

## License

This project is for educational purposes.
