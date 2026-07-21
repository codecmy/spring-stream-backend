# Spring Stream Backend

A full-stack video streaming application built with Spring Boot, featuring multi-audio-track HLS streaming, JWT authentication, MinIO object storage, Kafka-based analytics, and a Netflix-inspired React frontend.

## Features

- **Video Upload & Storage** — Upload videos stored in MinIO (S3-compatible)
- **Multi-Audio-Track HLS Streaming** — Adaptive bitrate streaming with per-audio-track HLS playlists and quality variants
- **Audio Language Detection** — FFmpeg probes uploaded videos for audio tracks at upload time; language codes stored in DB
- **Audio Track Switching** — Frontend player supports switching between audio languages mid-playback without interrupting video
- **JWT Authentication** — Self-contained HMAC-SHA256 JWT auth (login/register/me)
- **Thumbnail Extraction** — Auto-generated first-frame thumbnails via FFmpeg on upload
- **Video Playback** — HTML5 video player with hls.js, Bearer token injected on every request
- **Video Analytics** — Real-time event tracking with Kafka pipeline, delta-based watch time, drop-off funnel, quality/device breakdowns
- **Admin Analytics Dashboard** — Charts for views over time, top videos, per-video detail with drop-off histograms
- **RESTful API** — Full CRUD operations for videos with role-based access (ADMIN/USER)
- **MySQL Database** — Persistent storage for video and user metadata
- **RabbitMQ** — Message queue for async transcoding by external worker
- **Kafka** — Event bus for analytics event streaming (6 partitions, auto-created via init container)
- **nginx** — Reverse proxy with segment caching (7 days for `.ts`, 60s for `.m3u8`)

## Architecture

### System Diagram

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│ Frontend │────▶│  nginx   │────▶│ Backend  │
│ (React)  │     │   :80    │     │  :8081   │
└──────────┘     └──────────┘     └────┬─────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                   │
                    ▼                  ▼                   ▼
              ┌──────────┐      ┌──────────┐       ┌──────────┐
              │  MySQL   │      │  MinIO   │       │  Kafka   │
              │  :3307   │      │  :9000   │       │  :9092   │
              └──────────┘      └──────────┘       └─────┬────┘
                    ▲                                     │
                    │                                     ▼
              ┌──────────┐                      ┌────────────────┐
              │ Analytics│◀─────────────────────│ Kafka Consumer │
              │ Consumer │                      │ (analytics)    │
              └──────────┘                      └────────────────┘
                    │
              ┌──────────┐
              │ RabbitMQ │
              │  :5672   │
              └─────┬────┘
                    ▼
              ┌──────────┐
              │ Transcoder│
              │  Worker   │
              └──────────┘
```

### Video Transcoding Pipeline

1. **Upload** — Admin uploads video through API
2. **Probe** — Backend runs `ffprobe` to detect audio tracks and their language codes
3. **Thumbnail** — Backend extracts first frame via FFmpeg, stores in MinIO
4. **Storage** — Video pushed to MinIO `videos` bucket under `raw/{uuid}_{filename}`
5. **Metadata** — Video metadata (including `audioLanguages`) stored in MySQL
6. **Queue** — Upload triggers a RabbitMQ message (decoupled & async)
7. **Event** — Backend publishes `VIEW` event to Kafka `video.events` topic
8. **Transcode** — External worker consumes RabbitMQ message, runs multi-pass FFmpeg transcoding
9. **Output** — Transcoded HLS segments and playlists stored in MinIO `video-hls` bucket
10. **Serve** — Backend serves HLS assets from MinIO; nginx caches segments for 7 days

### Analytics Pipeline

```
Frontend → POST /api/v1/events → Backend (EventController)
  → KafkaEventProducer → Kafka [video.events topic]
  → AnalyticsConsumerListener → AnalyticsServiceImpl
  → MySQL (video_events + video_analytics_summary + video_drop_off + video_session_state)
```

**Event types tracked:**

| Event | Trigger | Payload |
|-------|---------|---------|
| `VIEW` | Page mount (once) | `videoId`, `quality` |
| `PLAY` | `<video>` play | `videoId`, `position`, `quality` |
| `PAUSE` | `<video>` pause (if not ended) | `videoId`, `position`, `quality` |
| `SEEK` | `<video>` seeked (if diff > 2s) | `videoId`, `seekFrom`, `seekTo`, `quality` |
| `COMPLETE` | `<video>` ended | `videoId`, `duration`, `quality` |
| `DROP_OFF` | `beforeunload` / tab switch | `videoId`, `position`, `duration`, `quality` |

**Watch time calculation:** Uses per-session delta tracking — computes `delta = currentPosition - lastPosition` per `(videoId, sessionId)` pair. Delta capped at 300s, ignored if session gap > 30min. `COMPLETE` counts full `duration`. This prevents inflated watch time from overlapping DROP_OFF position values.

**Drop-off bucketing:** `DROP_OFF` events with `position/duration < 95%` are bucketed into 10% ranges (0, 10, 20... 90).

**Summary metrics per video:** `totalViews`, `totalPlays`, `totalPauses`, `totalSeeks`, `totalCompletions`, `totalDropOffs`, `totalWatchTimeSeconds`, `avgWatchTimeSeconds`, `completionRate`.

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

### MinIO Bucket Structure

| Bucket | Purpose | Contents |
|--------|---------|----------|
| `videos` | Raw video storage | Source files (`raw/{uuid}_{filename}`), thumbnails (`thumbnails/{videoId}.jpg`) |
| `video-hls` | HLS streaming assets | Transcoded `.m3u8` playlists and `.ts` segments |

```
video-hls/{uuid}_{filename}/
├── master.m3u8                 # Main master playlist (points to a0/)
├── a0/                         # Audio track 0 (e.g., Hindi)
│   ├── master.m3u8             # Per-audio master playlist
│   ├── v0/index.m3u8           # 360p quality playlist
│   ├── v0/segment_000.ts       # 360p segment
│   ├── v1/index.m3u8           # 480p quality playlist
│   ├── v2/index.m3u8           # 720p quality playlist
│   └── v3/index.m3u8           # 1080p quality playlist
├── a1/                         # Audio track 1 (e.g., English)
│   ├── master.m3u8
│   └── v0/ ... v3/             # Same 4 quality variants
└── a2/                         # Audio track 2 (e.g., Japanese)
    ├── master.m3u8
    └── v0/ ... v3/
```

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
| Frontend (React SPA) | 80 | nginx serving StreamFlix UI |
| Backend API | 8081 | Spring Boot REST API |
| MySQL | 3307 (host), 3306 (internal) | Database |
| MinIO | 9000 | S3-compatible object storage |
| MinIO Console | 9001 | MinIO admin UI |
| RabbitMQ | 5672 | Message queue |
| RabbitMQ UI | 15672 | RabbitMQ management |
| Kafka | 9092 | Event streaming |
| Kafka UI | 8088 | Kafka cluster UI |
| Worker | — | External transcoder (separate image) |
| Analytics Consumer | — | Kafka event consumer (separate image) |

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+ (for local development)
- Maven (for building)
- Node.js 18+ (for frontend)

### Quick Start

```bash
# Build the backend
./mvnw clean package -DskipTests

# Build Docker images
docker build -t spring-stream-backend:latest .
docker build -t transcoder-worker:latest ./services/transcoder-worker
docker build -t analytics-consumer:latest ./services/analytics-consumer

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
| Kafka UI | http://localhost:8088 | — |

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

### Analytics Events

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| POST | `/api/v1/events` | Ingest video event (VIEW/PLAY/PAUSE/SEEK/DROP_OFF/COMPLETE) | Yes |

### Admin Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| GET | `/api/v1/admin/dashboard` | Admin dashboard stats | ADMIN |
| GET | `/api/v1/admin/videos` | Admin video list | ADMIN |
| POST | `/api/v1/admin/videos` | Admin video upload | ADMIN |
| POST | `/api/v1/admin/videos/advanced` | Advanced video upload | ADMIN |
| GET | `/api/v1/admin/users` | Admin user list | ADMIN |
| GET | `/api/v1/admin/analytics/overview` | Analytics overview (total views, watch time, top videos, views over time) | ADMIN |
| GET | `/api/v1/admin/analytics/videos` | Per-video analytics summary list | ADMIN |
| GET | `/api/v1/admin/analytics/videos/{videoId}` | Per-video detail (drop-offs, quality, device, daily views) | ADMIN |

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

Analytics-consumer and Kafka connect via Docker network hostnames (configured in `docker-compose.yml`).

## Project Structure

```
├── src/main/java/com/example/spring_stream_backend/
│   ├── SpringStreamBackendApplication.java
│   ├── TestController.java                          # GET /health
│   ├── config/                                       # MinioConfig, RabbitConfig, CorsConfig, SecurityConfig, KafkaConfig
│   ├── controllers/
│   │   ├── VideoController.java                     # All video REST endpoints
│   │   ├── EventController.java                     # POST /api/v1/events — event ingestion
│   │   ├── AdminController.java                     # Admin dashboard, user mgmt, analytics endpoints
│   │   ├── AuthController.java                      # login, register, /me
│   │   ├── PlaylistController.java                  # playlist CRUD
│   │   └── SeriesController.java                    # series CRUD
│   ├── filter/JwtAuthenticationFilter.java           # Reads Bearer token
│   ├── auth/                                         # JwtUtil, JwtAuthenticationProvider, AdminSeeder
│   ├── services/
│   │   ├── VideoServices.java                        # Interface
│   │   └── impl/
│   │       ├── VideoServiceImpl.java                # Upload, HLS streaming, audio probing, MinIO operations
│   │       ├── InputStreamResource.java             # Custom InputStream-based Resource
│   │       └── KafkaEventProducer.java              # Publishes events to Kafka video.events topic
│   ├── Entity/
│   │   ├── Video.java                                # @Entity (yt_video) with audioLanguages field
│   │   ├── Courses.java                              # @Entity (yt_courses)
│   │   └── VideoEventLog.java                        # @Entity video_events (backend-side event log)
│   ├── repositories/
│   │   ├── VideoRepositories.java                    # JpaRepository
│   │   └── VideoEventLogRepository.java              # JpaRepository
│   └── payload/
│       ├── VideoEvent.java                           # Event DTO
│       ├── EventType.java                            # enum: VIEW, PLAY, PAUSE, SEEK, DROP_OFF, COMPLETE
│       └── CustomMessage.java                        # Response DTO
├── services/
│   ├── transcoder-worker/
│   │   └── src/main/java/com/example/demo/
│   │       ├── VideoWorkerApplication.java           # Worker entry point
│   │       ├── listener/VideoProcessingListener.java # RabbitMQ consumer
│   │       ├── service/VideoProcessingService.java   # Multi-pass FFmpeg transcoding + HLS upload
│   │       └── model/AudioTrack.java                 # record: streamIndex, language, codec
│   └── analytics-consumer/
│       └── src/main/java/com/example/analytics/
│           ├── AnalyticsConsumerApplication.java     # Consumer entry point
│           ├── listener/VideoAnalyticsListener.java  # Kafka consumer, parses JSON events
│           ├── service/
│           │   ├── AnalyticsService.java             # Interface
│           │   └── AnalyticsServiceImpl.java         # Delta-based watch time, summary aggregation, drop-off bucketing
│           ├── model/
│           │   ├── EventType.java                    # enum: VIEW, PLAY, PAUSE, SEEK, DROP_OFF, COMPLETE
│           │   ├── VideoEventEntity.java             # @Entity video_events
│           │   ├── VideoAnalyticsSummary.java        # @Entity video_analytics_summary
│           │   ├── VideoDropOff.java                 # @Entity video_drop_off
│           │   ├── VideoDropOffId.java               # composite PK (videoId + bucketPercent)
│           │   ├── VideoSessionState.java            # @Entity video_session_state
│           │   └── VideoSessionStateId.java          # composite PK (videoId + sessionId)
│           └── repository/
│               ├── VideoEventRepository.java
│               ├── VideoAnalyticsSummaryRepository.java
│               ├── VideoDropOffRepository.java
│               └── VideoSessionStateRepository.java
├── streamflix/                                       # React frontend (Vite + TailwindCSS)
│   ├── src/
│   │   ├── api/
│   │   │   ├── api.js                                # fetchVideos(), URL builders, searchVideos()
│   │   │   ├── events.js                             # trackEvent() — fire-and-forget POST /api/v1/events
│   │   │   ├── analytics.js                          # fetchAnalyticsOverview(), fetchVideoAnalyticsList(), fetchVideoAnalyticsDetail()
│   │   │   └── queryClient.js                        # React Query client
│   │   ├── store/useStore.js                         # Zustand store with JWT management
│   │   ├── components/
│   │   │   ├── MovieCard.jsx, MovieCarousel.jsx, Navbar.jsx
│   │   │   └── analytics/                            # StatsCard, ViewsOverTimeChart, TopVideosChart, DropOffChart, QualityDistributionChart, DeviceBreakdownChart
│   │   └── pages/
│   │       ├── PlayerPage.jsx                        # HLS player with audio/quality switching + event tracking
│   │       ├── HomePage.jsx                          # Video grid
│   │       ├── LoginPage.jsx                         # Auth
│   │       └── AdminAnalyticsPage.jsx                # Analytics dashboard (admin-only)
│   └── dist/                                         # Built assets (served by nginx)
├── docker-compose.yml
├── nginx.conf                                        # Reverse proxy with segment caching
├── Dockerfile                                        # Multi-stage: Maven build + Alpine JDK + FFmpeg
├── AGENTS.md
└── .env
```

## Troubleshooting

### Services not starting

```bash
docker compose logs
docker compose restart
```

### Kafka topic errors (UNKNOWN_TOPIC_OR_PARTITION)

The `kafka-init` container creates the `video.events` topic at startup. If the backend or analytics-consumer starts before the topic exists, they will log warnings. The `kafka-init` container runs once and exits — check `docker compose ps` to verify it completed successfully.

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

# Analytics consumer
docker build -t analytics-consumer:latest ./services/analytics-consumer
docker compose up -d analytics-consumer

# Frontend (bind-mounted to nginx)
cd streamflix && npm run build && cd ..
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx
```

### Reset analytics data

If watch time values are inflated or incorrect (e.g., from a previous buggy version):

```bash
docker exec mysql mysql -u root -proot streamdb -e \
  "UPDATE video_analytics_summary SET total_watch_time_seconds = 0, avg_watch_time_seconds = 0;
   DELETE FROM video_session_state;"
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

## License

This project is for educational purposes.
