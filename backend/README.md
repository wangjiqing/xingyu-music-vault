# Xingyu Music Vault Backend

## v0.2.1 当前能力

后端 v0.2.1 在 v0.2 基础上增强了扫描稳定性与前端体验：

- **Track CRUD**：曲目的创建、查询、更新、删除
- **本地音乐库扫描**：创建扫描任务后递归扫描本地目录，记录音频文件到 `track_files`
- **扫描稳定性**：重复运行保护（`running`/`completed` 状态任务禁止重复执行，返回 `409`）、扫描路径安全校验（禁止扫描 `/`、`/etc`、`/Users`、`/home` 等危险路径，禁止路径穿越，禁止扫描配置目录外路径）
- **列表分页与筛选**：ScanJob 支持 `page`/`size`/`status` 筛选，TrackFile 支持 `page`/`size`/`ext`/`keyword` 筛选
- **前端 Token 设置**：Web 管理后台内置 API Token 配置页面，Token 存储于浏览器 `localStorage`
- **Health API**：服务健康检查
- **Bearer Token 鉴权**：所有 `/api/*` 接口（除 `/api/health` 外）需要 Authorization header
- **SQLite + Flyway**：数据库自动迁移

**本阶段不包含**：音频内嵌元数据提取（ffprobe）、歌词抓取、封面刮削、MusicBrainz/LRCLIB 等联网匹配、音频指纹、歌手/专辑管理、登录系统、复杂权限系统。

## Requirements

- JDK 21
- Maven 3.9+
- SQLite database file path writable by the app

## Local Start

```bash
cd backend
mvn quarkus:dev
```

In dev mode the SQLite database is stored at `backend/data/music-vault.db`, and Flyway runs automatically at startup. The service listens on `http://localhost:8080`.

The dev profile allows scanning under this local music root by default:

```text
/Users/wangjiqing/Project/Musics
```

For local testing, create scan jobs with directories inside that root, for example `/Users/wangjiqing/Project/Musics/Music`. Production and Docker Compose defaults still use `/music`, and any environment can override the allowed scan roots with `MUSIC_VAULT_MUSIC_DIRS`.

You can override paths with environment variables when needed:

```bash
MUSIC_VAULT_DB_PATH=/tmp/xingyu-music-vault-data/music-vault.db \
MUSIC_VAULT_DATA_DIR=/tmp/xingyu-music-vault-data \
MUSIC_VAULT_MUSIC_DIRS=/path/to/music \
MUSIC_VAULT_API_TOKEN=change-me \
mvn quarkus:dev
```

## Logging

HTTP access logging is enabled and includes the request line, status, and duration. Project business logs use `INFO` by default and `DEBUG` for `com.xingyu.musicvault` in dev mode.

Scan logs include job creation, run requests, allowed roots, resolved scan directories, path validation failures, scan summaries, and scan failures with stack traces. File-level scan details are logged at `DEBUG` to keep normal logs readable. Authorization headers, API tokens, passwords, and secrets are not logged.

## Test

```bash
cd backend
mvn test
```

## Build

```bash
cd backend
mvn package
```

This builds a JVM-mode Quarkus app under `target/quarkus-app`.

## Swagger UI

Swagger UI is always included:

```text
http://localhost:8080/q/swagger-ui
```

## Health Check

`/api/health` is public:

```bash
curl -i http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "ok",
  "service": "xingyu-music-vault",
  "version": "0.1.0"
}
```

## Create Scan Job

All `/api/*` routes except `/api/health` require a bearer token. The default token is `change-me`.

```bash
curl -i -X POST http://localhost:8080/api/scan-jobs \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"jobType":"library_scan","musicDirs":["/Users/wangjiqing/Project/Musics/Music"]}'
```

This only creates a `pending` scan job. Run it explicitly:

```bash
curl -i -X POST http://localhost:8080/api/scan-jobs/1/run \
  -H 'Authorization: Bearer change-me'
```

The scanner records local files with extensions `mp3`, `flac`, `wav`, `m4a`, `aac`, `ogg`, and `opus` into `track_files`. Re-running a scan updates existing rows by `file_path` instead of inserting duplicates. v0.2 does not call ffprobe, extract embedded tags, fetch lyrics, fetch cover art, perform network matching, or modify local music files.

List scan jobs with pagination and optional status:

```bash
curl -i 'http://localhost:8080/api/scan-jobs?page=0&size=20&status=completed' \
  -H 'Authorization: Bearer change-me'
```

List scanned files with pagination:

```bash
curl -i 'http://localhost:8080/api/track-files?page=0&size=20' \
  -H 'Authorization: Bearer change-me'
```

Filter by extension and keyword:

```bash
curl -i 'http://localhost:8080/api/track-files?ext=flac&keyword=live' \
  -H 'Authorization: Bearer change-me'
```

`POST /api/scan-jobs/{id}/run` only runs `pending` and `failed` jobs. `running` and `completed` jobs return `409` with `{ "error": "conflict", "message": "..." }`.

## 本地验证流程

### 1. 启动服务

```bash
cd backend
mvn quarkus:dev
```

### 2. 健康检查

```bash
curl -i http://localhost:8080/api/health
```

### 3. 创建扫描任务

```bash
curl -i -X POST http://localhost:8080/api/scan-jobs \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"jobType":"library_scan","musicDirs":["/Users/wangjiqing/Project/Musics/Music"]}'
```

### 4. 执行扫描

```bash
curl -i -X POST http://localhost:8080/api/scan-jobs/1/run \
  -H 'Authorization: Bearer change-me'
```

### 5. 查看扫描结果

扫描任务列表：
```bash
curl 'http://localhost:8080/api/scan-jobs?page=0&size=20' \
  -H 'Authorization: Bearer change-me'
```

文件记录列表：
```bash
curl 'http://localhost:8080/api/track-files?page=0&size=20' \
  -H 'Authorization: Bearer change-me'
```

按扩展名过滤：
```bash
curl 'http://localhost:8080/api/track-files?ext=flac' \
  -H 'Authorization: Bearer change-me'
```

### 扫描任务状态

| status | 说明 |
|--------|------|
| `pending` | 任务已创建，等待执行 |
| `running` | 扫描进行中 |
| `completed` | 扫描完成（不可再次执行，返回 409） |
| `failed` | 扫描失败（可重新执行） |

### 扫描路径安全规则

扫描目录必须满足以下所有条件，否则任务进入 `failed` 状态：

1. 目录必须存在且为可读目录
2. 目录必须位于 `MUSIC_VAULT_MUSIC_DIRS` 配置的允许路径范围内
3. 禁止扫描危险根路径：`/`、`/etc`、`/Users`、`/home`
4. 禁止路径穿越（不能包含 `..`）
5. 真实路径（经 `toRealPath()` 归一化后）必须在允许范围内

### 前端 Token 设置

前端 Web 管理后台内置 Token 配置页面：

1. 访问 `http://localhost:5173`（或 `VITE_API_BASE_URL` 配置的地址）
2. 进入「系统设置」页面
3. 输入 API Token 并保存
4. Token 存储于浏览器 `localStorage`，有效期为永久，直到手动清除

## Track CRUD

All `/api/tracks` routes require `Authorization: Bearer change-me` by default.

Create a track:

```bash
curl -i -X POST http://localhost:8080/api/tracks \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"title":"First Song"}'
```

List tracks:

```bash
curl -i http://localhost:8080/api/tracks \
  -H 'Authorization: Bearer change-me'
```

Get one track:

```bash
curl -i http://localhost:8080/api/tracks/1 \
  -H 'Authorization: Bearer change-me'
```

Update a track:

```bash
curl -i -X PUT http://localhost:8080/api/tracks/1 \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Updated Song","metadataStatus":"matched"}'
```

Delete a track:

```bash
curl -i -X DELETE http://localhost:8080/api/tracks/1 \
  -H 'Authorization: Bearer change-me'
```

## Configuration

Environment variables:

| Variable | Default |
| --- | --- |
| `MUSIC_VAULT_DATA_DIR` | `/app/data` |
| `MUSIC_VAULT_CONFIG_DIR` | `/app/config` |
| `MUSIC_VAULT_MUSIC_DIRS` | `/music` |
| `MUSIC_VAULT_DB_PATH` | `/app/data/music-vault.db` |
| `MUSIC_VAULT_API_TOKEN` | `change-me` |
| `MUSIC_VAULT_FFPROBE_PATH` | `/usr/bin/ffprobe` |
| `MUSIC_VAULT_FFMPEG_PATH` | `/usr/bin/ffmpeg` |

The custom config prefix is `music-vault`, read by `MusicVaultConfig`.

## SQLite Note

This skeleton uses `quarkus-jdbc-sqlite` with Hibernate ORM Panache and `org.hibernate.community.dialect.SQLiteDialect`. If a future Quarkus or Hibernate release changes SQLite dialect packaging, keep the Flyway schema as the source of truth and adjust the dialect dependency/config first.

## Docker

```bash
cd backend
mvn package
docker build -t xingyu-music-vault-backend .
docker run --rm -p 8080:8080 \
  -e MUSIC_VAULT_DB_PATH=/app/data/music-vault.db \
  -e MUSIC_VAULT_MUSIC_DIRS=/music \
  -e MUSIC_VAULT_API_TOKEN=change-me \
  -v "$PWD/data:/app/data" \
  -v "/path/to/music:/music:ro" \
  xingyu-music-vault-backend
```
