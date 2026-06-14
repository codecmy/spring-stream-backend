# Spring Stream Backend

A full-stack video streaming application built with Spring Boot, featuring HLS video streaming, JWT authentication, MinIO object storage, and a Netflix-inspired React frontend.

## Features

- **Video Upload & Storage** — Upload videos stored in MinIO (S3-compatible)
- **HLS Streaming** — Adaptive bitrate streaming using HLS (.m3u8 playlists with .ts segments)
- **JWT Authentication** — Self-contained HMAC-SHA256 JWT auth (login/register/me)
- **Thumbnail Extraction** — Auto-generated first-frame thumbnails via FFmpeg on upload
- **Video Playback** — HTML5 video player with hls.js, Bearer token injected on every segment
- **RESTful API** — Full CRUD operations for videos with role-based access (ADMIN/USER)
- **Admin Panel** — Standalone admin HTML (`/admin.html`) with authFetch() helper
- **MySQL Database** — Persistent storage for video and user metadata
- **RabbitMQ** — Message queue for async transcoding by external worker
- **nginx** — Reverse proxy with segment caching for improved performance

## Architecture

### Video Transcoding Pipeline

![Video Transcoding Pipeline](./src/main/resources/static/Screenshot%202026-03-30%20120250.png)

The video transcoding pipeline follows an asynchronous, event-driven architecture:

1. **Upload**: Admin/user uploads video through API
2. **Thumbnail**: Backend extracts first frame via FFmpeg, stores in MinIO
3. **Storage**: Video pushed to MinIO `videos` bucket
4. **Metadata**: Video metadata stored in MySQL database
5. **Queue**: Upload triggers a message to RabbitMQ (decoupled & async)
6. **Transcode**: External worker (separate image) fetches from queue, transcodes using FFmpeg
7. **Output**: Transcoded HLS segments and playlists stored in MinIO `video-hls` bucket

### MinIO Bucket Structure

![MinIO Bucket Structure](./src/main/resources/static/Screenshot%202026-03-30%20131618.png)

MinIO stores objects in two main buckets:

| Bucket | Purpose | Contents |
|--------|---------|----------|
| `videos` | Raw video storage | Source video files, thumbnails (`thumbnails/{videoId}.jpg`) |
| `video-hls` | HLS streaming assets | Transcoded `.m3u8` playlists and `.ts` segments |

Each transcoded video is organized in a folder named after the original file UUID:
```
video-hls/
├── {uuid}_filename.mp4/
│   ├── master.m3u8          # HLS master playlist with quality variants
│   ├── v3/index.m3u8        # 1080p quality playlist
│   ├── v3/segment_000.ts    # 1080p segment
│   ├── v2/index.m3u8        # 720p quality playlist
│   └── v2/segment_000.ts    # 720p segment
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

- JWT stored in `localStorage` and sent as `Authorization: Bearer <token>` header
- hls.js injects Bearer token via `xhrSetup` callback on every playlist/segment request
- Admin users (seeded from `ADMIN_EMAILS` env var) have access to `/api/v1/admin/**`

### Services

| Service | Port | Description |
|---------|------|-------------|
| Frontend (React SPA) | 80 | nginx serving the web UI |
| Frontend (Admin) | 80 | `/admin.html` served by nginx |
| Backend API | 8081 | Spring Boot REST API |
| MySQL | 3306 (internal), 3307 (host) | Database |
| MinIO | 9000 | S3-compatible storage |
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
| Frontend (User) | http://localhost:80 | — |
| Frontend (Admin) | http://localhost:80/admin.html | Admin email + password |
| Backend API | http://localhost:8081 | — |
| MySQL | localhost:3307 | root/root |
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
| POST | `/api/v1/videos` | Upload video | ADMIN |
| GET | `/api/v1/videos/{id}/thumbnail` | Get video thumbnail (JPEG) | No |
| GET | `/api/v1/videos/{id}/master.m3u8` | Get HLS master playlist | Yes |
| GET | `/api/v1/videos/{id}/{quality}/index.m3u8` | Get quality-specific playlist (e.g. `v3`) | Yes |
| GET | `/api/v1/videos/{id}/{quality}/{segment}.ts` | Get quality-specific segment | Yes |
| GET | `/api/v1/videos/{id}/{segment}.ts` | Get default-quality segment | Yes |
| GET | `/api/v1/videos/stream/range/{id}` | Range streaming | Yes |

### Admin Operations

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|----------------|
| GET | `/api/v1/admin/videos` | List all videos (admin) | ADMIN |
| PATCH | `/api/v1/admin/videos/{id}` | Update video title/description | ADMIN |
| DELETE | `/api/v1/admin/videos/{id}` | Delete video | ADMIN |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Backend health check |

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

## Video Streaming Flow

1. **Upload**: Video uploaded via POST `/api/v1/videos`, stored in MinIO `raw/` prefix
2. **Thumbnail**: Backend extracts first frame via FFmpeg, stored as `thumbnails/{videoId}.jpg`
3. **Queue**: RabbitMQ message published with object name
4. **Transcode**: External worker consumes message, transcodes to HLS with quality variants
5. **Storage**: Worker stores HLS assets in MinIO `video-hls` bucket
6. **Playback**: Frontend requests `master.m3u8`, hls.js loads segments, Bearer token injected on every request

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

### Property Key Mismatch

`application.properties` defines uppercase keys (`MINIO_URL`, `MINIO_ACCESS_KEY`, etc.) but Java code uses `@Value("${minio.url}")`. Spring Boot does NOT apply relaxed binding to `@Value`. If the app fails to start, add fallback values:
```properties
minio.url=${MINIO_URL:http://minio:9000}
minio.access-key=${MINIO_ACCESS_KEY:admin}
```

## Development

### Building from Source

```bash
# Build the backend
./mvnw clean package -DskipTests

# Build Docker image
docker build -t spring-stream-backend:latest .

# Build the frontend
cd streamflix
npm install
npm run build
cd ..
```

### Running Tests

```bash
./mvnw test
```

## Project Structure

```
├── src/main/java/com/example/spring_stream_backend/
│   ├── config/               # MinioConfig, RabbitConfig, CorsConfig, SecurityConfig
│   ├── controllers/          # AuthController, VideoController, AdminController
│   ├── services/
│   │   ├── VideoServices.java
│   │   └── impl/
│   │       ├── VideoServiceImpl.java   # Upload, HLS streaming, thumbnail gen
│   │       └── MinioService.java       # Generic MinIO upload helper
│   ├── repositories/         # VideoRepositories, UserRepository
│   ├── filter/               # JwtAuthenticationFilter
│   ├── auth/                 # JwtUtil, JwtAuthenticationProvider, AdminSeeder
│   ├── Entity/               # Video, Courses, User
│   └── payload/              # CustomMessage, LoginRequest, LoginResponse
├── streamflix/               # React frontend (Vite)
│   ├── src/
│   │   ├── api/api.js        # fetchVideos(), URL builders
│   │   ├── store/useStore.js # Zustand store with JWT management
│   │   ├── components/       # MovieCard, MovieCarousel, Navbar
│   │   ├── pages/            # HomePage, PlayerPage, LoginPage
│   │   └── public/admin.html # Standalone admin panel
│   └── dist/                 # Built assets (served by nginx)
├── docker-compose.yml
├── nginx.conf
├── Dockerfile
├── AGENTS.md
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

### Clear nginx cache

```bash
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx
```

## License

This project is for educational purposes.