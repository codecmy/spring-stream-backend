# AGENTS.md

## Quick start
```bash
./mvnw clean package -DskipTests   # build
./mvnw test                         # run tests
docker compose up -d                # full stack (needs .env)
```

## Architecture notes
- **JWT auth (self-contained)** — `JwtAuthenticationFilter` extracts `Authorization: Bearer <token>` header, delegates to `JwtAuthenticationProvider` which validates the JWT locally via `JwtUtil` (jjwt, HMAC-SHA256). No external auth provider.
- **Login flow** — `POST /api/v1/auth/login` accepts `{ email, password }`, validates against DB (BCrypt), returns JWT. `POST /api/v1/auth/register` creates a new user. `GET /api/v1/auth/me` returns `{ id, email, role }`.
- **Admin seeding** — `AdminSeeder` reads `ADMIN_EMAILS` + `ADMIN_PASSWORD` env vars at startup, creates or updates admin users.
- **Frontend stores JWT in `localStorage`**, sends as `Authorization: Bearer` header. Logout clears localStorage.
- **Video worker is external** — `RabbitConfig` sets up `video.exchange` / `video.processing.queue` (`video.uploaded` routing key). Backend only **sends** messages after upload. The `/worker` service in `docker-compose.yml` consumes them (separate `transcoder-worker:latest` image).
- **Two MinIO buckets**: `videos` (raw uploads) and `video-hls` (transcoded HLS assets). Raw objects stored under `raw/{uuid}_{filename}`. HLS objects stored as `{uuid}_{filename}/master.m3u8` and `{uuid}_{filename}/{quality}/index.m3u8` / `{uuid}_{filename}/{quality}/{segment}.ts`.
- **No course API** — `Video` has `@ManyToOne` to `Courses` entity, but there are no course endpoints.

## Known property key mismatch (likely a bug)
`application.properties` defines uppercase keys (`MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_BUCKET`, etc.) but Java code uses `@Value("${minio.url}")`, `@Value("${minio.bucket}")`, etc. Spring Boot does NOT apply relaxed binding to `@Value`. If the app fails to start, convert the properties to lowercase-dotted form (e.g., `minio.url=${MINIO_URL:http://minio:9000}`).

## Test quirks
- Single test class: `SpringStreamBackendApplicationTests` — a `@SpringBootTest` that calls `processVideo()` with a hardcoded video ID. It runs FFmpeg locally and is closer to a manual integration test.
- No unit tests, no mocking.
- `processVideo()` on Windows runs via `cmd.exe /c ffmpeg ...`. On files stored in MinIO (path starts with `raw/`), it's a no-op (returns videoId).

## Docker
- MySQL exposed on **3307** (not 3306).
- nginx is a reverse proxy with segment caching (7d for `.ts`, 60s for `.m3u8`).
- The `/minio-init` service creates buckets at startup via curl.

## Commands
```bash
# clear nginx cache
docker compose exec nginx rm -rf /var/cache/nginx/*
docker compose restart nginx
```

## Dev server
- Backend: `http://localhost:8081`
- Frontend: `http://localhost:80`
- Test HTML client: `video-stream-tester.html` (open in browser, points at nginx)

## Notable files
- `video-stream-tester.html` — standalone HTML page for testing upload/playback without the StreamFlix UI.
- `static/index.html` — Netflix-style StreamFlix frontend (1513 lines, served by nginx).
- `src/main/java/.../services/impl/InputStreamResource.java` — custom `AbstractResource` wrapping an `InputStream` (used for streaming responses).

## App structure
```
src/main/java/com/example/spring_stream_backend/
  SpringStreamBackendApplication.java    # @SpringBootApplication entry
  TestController.java                    # GET /health → "test"
  controllers/VideoController.java       # all video REST endpoints
  config/                                  # MinioConfig, RabbitConfig, CorsConfig, SecurityConfig, RestClientConfig
  filter/JwtAuthenticationFilter.java     # reads Bearer token, delegates to provider
  auth/JwtAuthenticationProvider.java     # validates JWT via JwtUtil, creates User
  auth/JwtUtil.java                       # JWT generation & validation (jjwt, HMAC-SHA256)
  auth/JwtAuthenticationToken.java        # custom AbstractAuthenticationToken
  auth/AdminSeeder.java                   # seeds admin users from ADMIN_EMAILS + ADMIN_PASSWORD
  services/VideoServices.java             # interface
  services/impl/VideoServiceImpl.java     # upload, HLS streaming, local FFmpeg processing
  services/impl/MinioService.java         # generic MinIO upload helper
  Entity/Video.java                       # @Entity mapped to yt_video
  Entity/Courses.java                     # @Entity mapped to yt_courses
  repositories/VideoRepositories.java     # JpaRepository<Video, String>
  payload/CustomMessage.java              # response DTO
  AppConstants.java                       # Chunk_size = 1MB
```
