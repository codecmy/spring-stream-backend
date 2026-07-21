# AGENTS.md

## Quick start
```bash
./mvnw clean package -DskipTests                      # build backend
docker build -t spring-stream-backend:latest .         # backend image
docker build -t transcoder-worker:latest ./services/transcoder-worker   # worker image
docker build -t analytics-consumer:latest ./services/analytics-consumer # analytics image
cd streamflix && npm install && npm run build && cd ..  # build frontend
docker compose up -d                                   # full stack (needs .env)
```

## Architecture overview
- **Frontend** (React/Vite → nginx:80) — SPA with HLS player, admin panel, analytics dashboard
- **Backend** (Spring Boot → :8081) — REST API, JWT auth, video CRUD, HLS serving, event ingestion, admin endpoints
- **Analytics Consumer** (Spring Boot) — Kafka consumer, processes video events into aggregated summaries
- **Transcoder Worker** (Spring Boot) — RabbitMQ consumer, multi-pass FFmpeg transcoding
- **nginx** — reverse proxy, SPA routing, HLS segment caching (7d for `.ts`, 60s for `.m3u8`)
- **Kafka** — event bus between backend (producer) and analytics-consumer (consumer)
- **RabbitMQ** — async job queue between backend (producer) and transcoder-worker (consumer)
- **MinIO** — S3-compatible object storage (`videos` raw, `video-hls` transcoded)
- **MySQL** — shared database for backend and analytics-consumer

## JWT auth (self-contained)
- `JwtAuthenticationFilter` extracts `Authorization: Bearer <token>` header
- `JwtAuthenticationProvider` validates JWT locally via `JwtUtil` (jjwt, HMAC-SHA256)
- Login: `POST /api/v1/auth/login` → `{ email, password }` → JWT token
- Register: `POST /api/v1/auth/register` → creates new user
- Current user: `POST /api/v1/auth/me` → `{ id, email, role }`
- Frontend stores JWT in `localStorage`, sends as `Authorization: Bearer` header
- hls.js injects Bearer token via `xhrSetup` on every playlist/segment request
- `AdminSeeder` reads `ADMIN_EMAILS` + `ADMIN_PASSWORD` env vars at startup, creates/updates admin users

## Video transcoding pipeline
1. Admin uploads video via `POST /api/v1/videos` (multipart: file, title, description)
2. Backend runs `ffprobe` to detect audio tracks and language codes (stored in `Video.audioLanguages`)
3. Backend extracts first-frame thumbnail via FFmpeg, stores in MinIO
4. Video pushed to MinIO `videos` bucket under `raw/{uuid}_{filename}`
5. Video metadata stored in MySQL (`yt_video` table)
6. Backend sends `video.uploaded` message to RabbitMQ `video.exchange`
7. Backend publishes `VIEW` event to Kafka `video.events` topic
8. Transcoder worker consumes RabbitMQ message, runs multi-pass FFmpeg transcoding
9. Transcoded HLS assets stored in MinIO `video-hls` bucket
10. Backend serves HLS assets from MinIO; nginx caches segments for 7 days

## Analytics pipeline
### Event flow
```
Frontend (PlayerPage.jsx) → POST /api/v1/events → Backend (EventController)
  → KafkaEventProducer → Kafka [video.events topic, 6 partitions]
  → AnalyticsConsumerListener → AnalyticsServiceImpl → MySQL (video_events + video_analytics_summary + video_drop_off + video_session_state)
```

### Frontend events (`streamflix/src/api/events.js`)
- `VIEW` — once on page mount (no position/duration)
- `PLAY` — on `<video>` play event (position: currentTime)
- `PAUSE` — on `<video>` pause event if not ended (position: currentTime)
- `SEEK` — on `<video>` seeked event if diff > 2s (seekFrom, seekTo)
- `COMPLETE` — on `<video>` ended event (duration: el.duration, no position)
- `DROP_OFF` — on `beforeunload` / `visibilitychange→hidden` if currentTime > 0 (position, duration)

All events include: `videoId`, `sessionId` (random UUID per page load), `deviceType`, `quality`, `timestamp`.

### Watch time calculation (delta-based)
Watch time uses **per-session delta tracking** via `video_session_state` table:
- On each event with position, compute `delta = currentPosition - lastPosition` for same `(videoId, sessionId)`
- Delta capped at 300s, ignored if session gap > 30min
- `COMPLETE` events count the full `duration` and clear session state
- First `DROP_OFF` with no prior state caps at 300s
- Prevents inflated watch time from overlapping DROP_OFF position values

### Drop-off bucketing
- `DROP_OFF` events with `position/duration < 95%` are bucketed into 10% ranges (0, 10, 20... 90) in `video_drop_off` table
- Drop-offs at ≥95% of video duration are not counted (video effectively completed)

### Summary metrics (video_analytics_summary)
- `totalViews` — count of VIEW events
- `totalPlays` / `totalPauses` / `totalSeeks` — count of respective events
- `totalCompletions` — count of COMPLETE events
- `totalDropOffs` — count of DROP_OFF events
- `totalWatchTimeSeconds` — sum of delta-based watch time
- `avgWatchTimeSeconds` — `totalWatchTimeSeconds / totalViews`
- `completionRate` — `totalCompletions / totalViews`

## MinIO HLS bucket structure
```
video-hls/{uuid}_{filename}/
├── master.m3u8              # Main master (4 variants pointing to a0/v*/index.m3u8)
├── a0/                      # Audio track 0
│   ├── master.m3u8          # Per-audio master (4 variants pointing to v*/index.m3u8)
│   ├── v0/index.m3u8        # 360p playlist
│   ├── v0/segment_NNN.ts    # 360p segments
│   ├── v1/index.m3u8        # 480p
│   ├── v2/index.m3u8        # 720p
│   └── v3/index.m3u8        # 1080p
├── a1/                      # Audio track 1 (same structure)
└── a2/                      # Audio track 2 (same structure)
```

Main `master.m3u8` references `a0/v{0-3}/index.m3u8` (defaults to first audio track).
Per-audio `a{N}/master.m3u8` references relative `v{0-3}/index.m3u8`.

## Multi-audio FFmpeg (worker)
`VideoProcessingService.java` runs **one FFmpeg pass per audio track**. Each pass:
1. Selects audio stream via `[0:a:{pass}]` filter selector (NOT `[0:a]` which always picks stream 0)
2. Splits video into 4 resolutions (`split=4`), splits selected audio into 4 copies (`asplit=4`)
3. Maps each `[vResolution]` + `[aN]` pair into 4 HLS variants
4. Output goes to `a{pass}/v{0-3}/index.m3u8` and `a{pass}/v{0-3}/segment_NNN.ts`

```
filter_complex (per pass):
[0:v]split=4[v1][v2][v3][v4];[0:a:{pass}]asplit=4[a1][a2][a3][a4];
[v1]scale=640:-2[v360p]; [v2]scale=854:-2[v480p]; [v3]scale=1280:-2[v720p]; [v4]scale=1920:-2[v1080p];
```

- `-var_stream_map v:0,a:0 v:1,a:1 v:2,a:2 v:3,a:3` (type-relative indices)
- `-c:a:{v} aac -b:a:{v} 128k -metadata:s:a:{v} language={lang}`
- No audio tracks detected → single pass with `anullsrc` silent audio (`-shortest` flag required)
- `writeMasterPlaylist()` always writes `a0/v{0-3}/index.m3u8` paths
- `writePerAudioMasterPlaylists()` writes `a{N}/master.m3u8` for each detected audio track

## Audio probing
- **Backend**: `VideoServiceImpl.save()` runs `ffprobe` at upload time, stores language codes as comma-separated ISO codes in `Video.audioLanguages` (e.g., `"hin,eng,jpn"`)
- **Worker**: probes independently via its own `probeAudioTracks()`

## Frontend audio/quality switching
- **Initial load**: Uses main `master.m3u8` (via `getMasterPlaylistUrl()`) → `a0/v*/index.m3u8`
- **Audio switch**: Destroys hls.js instance, creates new one with `a{N}/master.m3u8` URL, seeks to saved playback position
- **Quality switch**: Sets `hls.currentLevel` to numeric index (0-3); Auto = -1
- `visibilitychange` listener properly cleaned up on unmount (prevents duplicate DROP_OFF events)

## Frontend analytics dashboard (`streamflix/src/pages/AdminAnalyticsPage.jsx`)
- Admin-only page (`/admin/analytics`)
- Overview cards: Total Views, Watch Time (hours), Avg Completion %, Videos Tracked
- Charts: Views Over Time, Top Videos, Per-Video table with expandable details
- Per-video detail: Drop-off funnel, quality distribution, device breakdown, daily views
- Fetches from backend admin analytics endpoints

## nginx
- Regex locations handle audio subdirectories: `(a[0-9]+/)?` optional group
- `.ts` segments cached 7 days (`video_cache`), `.m3u8` playlists cached 60s (`playlist_cache`)
- SPA fallback: `try_files $uri $uri/ /index.html`
- Frontend served from `/var/www/streamflix-dist/` (bind mount from host `streamflix/dist/`)

## Known property key mismatch
`application.properties` defines uppercase keys (`MINIO_URL`, `MINIO_ACCESS_KEY`, etc.) but Java code uses `@Value("${minio.url}")`. Spring Boot does NOT apply relaxed binding to `@Value`. If the app fails to start, convert to lowercase-dotted form (e.g., `minio.url=${MINIO_URL:http://minio:9000}`).

## Docker
- MySQL exposed on **3307** (not 3306).
- nginx reverse proxy with segment caching (7d `.ts`, 60s `.m3u8`).
- `minio-init` service creates buckets at startup via `mc mb --ignore-existing`.
- `kafka-init` service creates `video.events` topic (6 partitions, RF=1) at startup.
- Backend uses pre-built image `spring-stream-backend:latest` (must `docker build -t` first).
- Worker uses pre-built image `transcoder-worker:latest` (must `docker build -t` first).
- Analytics-consumer uses pre-built image `analytics-consumer:latest` (must `docker build -t` first).
- Worker: 3GB memory limit, 4 CPU cores.
- Analytics-consumer: 512MB memory limit.
- Kafka UI on port `8088`.

## Services
| Service | Port | Description |
|---------|------|-------------|
| Frontend (React SPA) | 80 | nginx serving StreamFlix UI |
| Backend API | 8081 | Spring Boot REST API |
| MySQL | 3307 (host) | Database |
| MinIO | 9000 | S3-compatible object storage |
| MinIO Console | 9001 | MinIO admin UI |
| RabbitMQ | 5672 | Message queue |
| RabbitMQ UI | 15672 | RabbitMQ management |
| Kafka | 9092 | Event streaming |
| Kafka UI | 8088 | Kafka cluster UI |
| Worker | — | External transcoder (separate image) |
| Analytics Consumer | — | Kafka event consumer (separate image) |

## Commands
```bash
# clear nginx cache
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx

# rebuild backend
docker build -t spring-stream-backend:latest .
docker compose up -d backend

# rebuild worker
docker build -t transcoder-worker:latest ./services/transcoder-worker
docker compose up -d worker

# rebuild analytics-consumer
docker build -t analytics-consumer:latest ./services/analytics-consumer
docker compose up -d analytics-consumer

# rebuild frontend (bind-mounted to nginx)
cd streamflix && npm run build && cd ..
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx

# reset analytics data
docker exec mysql mysql -u root -proot streamdb -e "UPDATE video_analytics_summary SET total_watch_time_seconds = 0, avg_watch_time_seconds = 0; DELETE FROM video_session_state;"

# check analytics consumer logs
docker logs analytics-consumer --tail 50
```

## Dev server
- Backend: `http://localhost:8081`
- Frontend: `http://localhost:80`

## App structure
```
src/main/java/com/example/spring_stream_backend/
  SpringStreamBackendApplication.java    # @SpringBootApplication entry
  TestController.java                    # GET /health → "test"
  controllers/
    VideoController.java                 # all video REST endpoints (catch-all {subPath} for HLS)
    EventController.java                 # POST /api/v1/events — event ingestion
    AdminController.java                 # admin dashboard, user mgmt, analytics endpoints
    AuthController.java                  # login, register, /me
    PlaylistController.java              # playlist CRUD
    SeriesController.java                # series CRUD
  config/
    MinioConfig.java                     # MinIO client bean
    RabbitConfig.java                    # RabbitMQ exchange/queue setup
    CorsConfig.java                      # CORS configuration
    SecurityConfig.java                  # Spring Security filter chain
    KafkaConfig.java                     # Kafka producer factory, NewTopic bean (video.events, 6 partitions)
  filter/JwtAuthenticationFilter.java    # reads Bearer token, delegates to provider
  auth/
    JwtAuthenticationProvider.java       # validates JWT via JwtUtil, creates User
    JwtUtil.java                         # JWT generation & validation (jjwt, HMAC-SHA256)
    JwtAuthenticationToken.java          # custom AbstractAuthenticationToken
    AdminSeeder.java                     # seeds admin users from ADMIN_EMAILS + ADMIN_PASSWORD
  services/
    VideoServices.java                   # interface
    impl/
      VideoServiceImpl.java             # upload, audio probing (ffprobe), HLS streaming, thumbnail gen
      InputStreamResource.java          # custom AbstractResource wrapping InputStream
      KafkaEventProducer.java           # publishes VideoEvent to Kafka video.events topic
  Entity/
    Video.java                           # @Entity yt_video (includes audioLanguages)
    Courses.java                         # @Entity yt_courses
    VideoEventLog.java                   # @Entity video_events (backend-side event log)
  repositories/
    VideoRepositories.java              # JpaRepository<Video, String>
    VideoEventLogRepository.java         # JpaRepository<VideoEventLog, Long>
  payload/
    VideoEvent.java                      # event DTO (eventType, videoId, position, duration, etc.)
    EventType.java                       # enum: VIEW, PLAY, PAUSE, SEEK, DROP_OFF, COMPLETE
    CustomMessage.java                   # response DTO
  AppConstants.java                      # Chunk_size = 1MB

services/transcoder-worker/src/main/java/com/example/demo/
  VideoWorkerApplication.java            # @SpringBootApplication entry
  listener/VideoProcessingListener.java  # RabbitMQ consumer, receives plain String objectName
  service/VideoProcessingService.java    # multi-pass FFmpeg transcoding, master playlist generation, MinIO upload
  model/AudioTrack.java                  # record: streamIndex, language, codec

services/analytics-consumer/src/main/java/com/example/analytics/
  AnalyticsConsumerApplication.java      # @SpringBootApplication entry
  listener/VideoAnalyticsListener.java   # Kafka consumer, parses JSON events, delegates to service
  service/
    AnalyticsService.java               # interface
    AnalyticsServiceImpl.java            # event processing, delta-based watch time, summary aggregation, drop-off bucketing
  model/
    EventType.java                       # enum: VIEW, PLAY, PAUSE, SEEK, DROP_OFF, COMPLETE
    VideoEventEntity.java               # @Entity video_events (raw event storage)
    VideoAnalyticsSummary.java          # @Entity video_analytics_summary (aggregated per-video metrics)
    VideoDropOff.java                   # @Entity video_drop_off (drop-off histogram by 10% buckets)
    VideoDropOffId.java                 # composite PK (videoId + bucketPercent)
    VideoSessionState.java              # @Entity video_session_state (per-session last position for delta tracking)
    VideoSessionStateId.java            # composite PK (videoId + sessionId)
  repository/
    VideoEventRepository.java           # event queries (counts, distributions, daily views)
    VideoAnalyticsSummaryRepository.java
    VideoDropOffRepository.java
    VideoSessionStateRepository.java

streamflix/src/
  api/
    api.js                              # fetchVideos(), getMasterPlaylistUrl(), getAudioTrackMasterUrl(), searchVideos()
    events.js                           # trackEvent() — fire-and-forget POST /api/v1/events
    analytics.js                        # fetchAnalyticsOverview(), fetchVideoAnalyticsList(), fetchVideoAnalyticsDetail()
    queryClient.js                      # React Query client
  store/useStore.js                     # Zustand store with JWT management
  pages/
    PlayerPage.jsx                      # HLS player, audio/quality switching, event tracking
    HomePage.jsx                        # Video grid
    LoginPage.jsx                       # Auth
    AdminAnalyticsPage.jsx              # Analytics dashboard (admin-only)
  components/
    MovieCard.jsx, MovieCarousel.jsx, Navbar.jsx
    analytics/
      StatsCard.jsx                     # Overview stat card
      ViewsOverTimeChart.jsx            # Line chart of daily views
      TopVideosChart.jsx                # Bar chart of top videos
      DropOffChart.jsx                  # Drop-off funnel histogram
      QualityDistributionChart.jsx      # Quality usage pie chart
      DeviceBreakdownChart.jsx          # Device type pie chart
```

## API endpoints
```
POST   /api/v1/auth/login               → { email, password } → JWT token
POST   /api/v1/auth/register            → register new user
GET    /api/v1/auth/me                   → current user { id, email, role }

GET    /api/v1/videos                    → list all videos (returns Video[])
POST   /api/v1/videos                    → upload video (multipart: file, title, description) [ADMIN]
GET    /api/v1/videos/{id}/thumbnail     → JPEG thumbnail
GET    /api/v1/videos/{id}/master.m3u8   → HLS master playlist
GET    /api/v1/videos/{id}/{*subPath}    → HLS sub-resource (a0/v0/index.m3u8, segments, etc.)
GET    /api/v1/videos/stream/range/{id}  → range-based MP4 streaming

POST   /api/v1/events                    → ingest video event (VIEW/PLAY/PAUSE/SEEK/DROP_OFF/COMPLETE)

GET    /api/v1/admin/dashboard           → admin dashboard stats
GET    /api/v1/admin/videos              → admin video list
POST   /api/v1/admin/videos              → admin video upload
POST   /api/v1/admin/videos/advanced     → advanced video upload
GET    /api/v1/admin/users               → admin user list
GET    /api/v1/admin/analytics/overview   → analytics overview (total views, watch time, top videos, views over time)
GET    /api/v1/admin/analytics/videos     → per-video analytics summary list
GET    /api/v1/admin/analytics/videos/{id} → per-video detail (drop-offs, quality, device, daily views)

GET    /health                           → "test"
```

## Test quirks
- Single test class: `SpringStreamBackendApplicationTests` — a `@SpringBootTest` that calls `processVideo()` with a hardcoded video ID. It runs FFmpeg locally and is closer to a manual integration test.
- No unit tests, no mocking.
- `processVideo()` on Windows runs via `cmd.exe /c ffmpeg ...`. On files stored in MinIO (path starts with `raw/`), it's a no-op (returns videoId).
