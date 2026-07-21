# AGENTS.md

## Quick start
```bash
./mvnw clean package -DskipTests   # build backend
docker build -t spring-stream-backend:latest .   # docker image
docker build -t transcoder-worker:latest ./services/transcoder-worker   # worker image
cd streamflix && npm install && npm run build && cd ..   # build frontend
docker compose up -d                # full stack (needs .env)
```

## Architecture notes
- **JWT auth (self-contained)** — `JwtAuthenticationFilter` extracts `Authorization: Bearer <token>` header, delegates to `JwtAuthenticationProvider` which validates the JWT locally via `JwtUtil` (jjwt, HMAC-SHA256). No external auth provider.
- **Login flow** — `POST /api/v1/auth/login` accepts `{ email, password }`, validates against DB (BCrypt), returns JWT. `POST /api/v1/auth/register` creates a new user. `GET /api/v1/auth/me` returns `{ id, email, role }`.
- **Admin seeding** — `AdminSeeder` reads `ADMIN_EMAILS` + `ADMIN_PASSWORD` env vars at startup, creates or updates admin users.
- **Frontend stores JWT in `localStorage`**, sends as `Authorization: Bearer` header. Logout clears localStorage.
- **Video worker is external** — `RabbitConfig` sets up `video.exchange` / `video.processing.queue` (`video.uploaded` routing key). Backend only **sends** messages after upload. The `/worker` service in `docker-compose.yml` consumes them (separate `transcoder-worker:latest` image).
- **Two MinIO buckets**: `videos` (raw uploads) and `video-hls` (transcoded HLS assets). Raw objects stored under `raw/{uuid}_{filename}`. HLS objects stored under `{uuid}_{filename}/a{N}/v{M}/...` (see MinIO bucket structure below).
- **No course API** — `Video` has `@ManyToOne` to `Courses` entity, but there are no course endpoints.

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

Main `master.m3u8` references `a0/v{0-3}/index.m3u8` (always defaults to first audio track).
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

- `-var_stream_map v:0,a:0 v:1,a:1 v:2,a:2 v:3,a:3` (type-relative indices: a:0..a:3 not a:4..a:7)
- `-c:a:{v} aac -b:a:{v} 128k -metadata:s:a:{v} language={lang}` (codec indices 0-3 matching audio type)
- No audio tracks detected → single pass with `anullsrc` silent audio (`-shortest` flag required to prevent infinite loop)
- `writeMasterPlaylist()` always writes `a0/v{0-3}/index.m3u8` paths (even for audio-less videos since output always goes under `a0/`)
- `writePerAudioMasterPlaylists()` writes `a{N}/master.m3u8` for each detected audio track

## Audio probing (backend)
`VideoServiceImpl.save()` runs `ffprobe` at upload time to detect audio streams and their language tags. Results stored as comma-separated ISO codes in `Video.audioLanguages` (e.g., `"hin,eng,jpn"`). Worker also probes independently via its own `probeAudioTracks()`.

## Frontend audio/quality switching
- **Initial load**: Uses main `master.m3u8` (via `getMasterPlaylistUrl()`) which references `a0/v*/index.m3u8`
- **Audio switch**: Destroys hls.js instance, creates new one with `a{N}/master.m3u8` URL, seeks to saved playback position
- **Quality switch**: Sets `hls.currentLevel` to numeric index (0-3); Auto = -1
- `PlayerPage.jsx` has no pLoader filter (was removed — caused issues with single-segment videos)

## nginx
- Regex locations handle audio subdirectories: `(a[0-9]+/)?` optional group
- `.ts` segments cached 7 days (`video_cache`), `.m3u8` playlists cached 60s (`playlist_cache`)
- SPA fallback: `try_files $uri $uri/ /index.html`

## Known property key mismatch (likely a bug)
`application.properties` defines uppercase keys (`MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_BUCKET`, etc.) but Java code uses `@Value("${minio.url}")`, `@Value("${minio.bucket}")`, etc. Spring Boot does NOT apply relaxed binding to `@Value`. If the app fails to start, convert the properties to lowercase-dotted form (e.g., `minio.url=${MINIO_URL:http://minio:9000}`).

## Test quirks
- Single test class: `SpringStreamBackendApplicationTests` — a `@SpringBootTest` that calls `processVideo()` with a hardcoded video ID. It runs FFmpeg locally and is closer to a manual integration test.
- No unit tests, no mocking.
- `processVideo()` on Windows runs via `cmd.exe /c ffmpeg ...`. On files stored in MinIO (path starts with `raw/`), it's a no-op (returns videoId).

## Docker
- MySQL exposed on **3307** (not 3306).
- nginx is a reverse proxy with segment caching (7d for `.ts`, 60s for `.m3u8`).
- The `/minio-init` service creates buckets at startup via `mc mb --ignore-existing`.
- Backend uses pre-built image `spring-stream-backend:latest` (must `docker build -t` before `docker compose up`).
- Worker uses pre-built image `transcoder-worker:latest` (must `docker build -t` before `docker compose up`).
- Worker: 3GB memory limit, 4 CPU cores.

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

# rebuild frontend
cd streamflix && npm run build && cd ..
docker cp streamflix/dist/. nginx:/usr/share/nginx/html/static/
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx

# re-upload a video (fix wrong master.m3u8)
docker compose exec minio mc cat local/video-hls/{prefix}/master.m3u8
```

## Dev server
- Backend: `http://localhost:8081`
- Frontend: `http://localhost:80`

## Notable files
- `src/main/java/.../services/impl/InputStreamResource.java` — custom `AbstractResource` wrapping an `InputStream` (used for streaming responses).
- `streamflix/src/pages/PlayerPage.jsx` — HLS player with audio/quality switching, segment zero filter removed.
- `streamflix/src/api/api.js` — `fetchVideos()`, `getMasterPlaylistUrl()`, `getAudioTrackMasterUrl()`, `searchVideos()`.
- `services/transcoder-worker/src/main/java/com/example/demo/service/VideoProcessingService.java` — multi-pass FFmpeg, master playlist generation.

## App structure
```
src/main/java/com/example/spring_stream_backend/
  SpringStreamBackendApplication.java    # @SpringBootApplication entry
  TestController.java                    # GET /health → "test"
  controllers/VideoController.java       # all video REST endpoints (catch-all {subPath} for HLS)
  config/                                # MinioConfig, RabbitConfig, CorsConfig, SecurityConfig
  filter/JwtAuthenticationFilter.java    # reads Bearer token, delegates to provider
  auth/JwtAuthenticationProvider.java    # validates JWT via JwtUtil, creates User
  auth/JwtUtil.java                      # JWT generation & validation (jjwt, HMAC-SHA256)
  auth/JwtAuthenticationToken.java       # custom AbstractAuthenticationToken
  auth/AdminSeeder.java                  # seeds admin users from ADMIN_EMAILS + ADMIN_PASSWORD
  services/VideoServices.java            # interface (save, findById, getAllVideo, getm3u8, getHlsSubResource, getThumbnail, deleteVideo, processVideo)
  services/impl/VideoServiceImpl.java    # upload, audio probing (ffprobe), HLS streaming from MinIO, thumbnail gen
  services/impl/InputStreamResource.java # custom AbstractResource wrapping InputStream
  Entity/Video.java                      # @Entity mapped to yt_video (includes audioLanguages field)
  Entity/Courses.java                    # @Entity mapped to yt_courses
  repositories/VideoRepositories.java    # JpaRepository<Video, String>
  payload/CustomMessage.java             # response DTO
  AppConstants.java                      # Chunk_size = 1MB

services/transcoder-worker/src/main/java/com/example/demo/
  VideoWorkerApplication.java            # @SpringBootApplication entry
  listener/VideoProcessingListener.java  # RabbitMQ consumer, receives plain String objectName
  service/VideoProcessingService.java    # multi-pass FFmpeg transcoding, master playlist generation, MinIO upload
  model/AudioTrack.java                  # record: streamIndex, language, codec

streamflix/src/
  api/api.js                             # fetchVideos(), getMasterPlaylistUrl(), getAudioTrackMasterUrl(), searchVideos()
  store/useStore.js                      # Zustand store with JWT management
  pages/PlayerPage.jsx                   # HLS player, audio/quality switching
  pages/HomePage.jsx                     # Video grid
  pages/LoginPage.jsx                    # Auth
  components/                            # MovieCard, MovieCarousel, Navbar
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
GET    /health                           → "test"
```
