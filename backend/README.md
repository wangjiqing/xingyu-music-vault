# Xingyu Music Vault Backend

## 当前能力

后端当前已完成本地音乐扫描入库、音乐列表查询、v0.5 歌词管理基础能力和 v0.5.2 歌词管理页查询 API：

- **POST /api/music/scan**：异步接受扫描请求，后台执行扫描，返回 `202 Accepted` 与 `scanJobId`
- **GET /api/music**：音乐分页列表，基于 `track_files` + `tracks` 联合视图
- **GET /api/music/{id}**：音乐详情
- **GET /api/music/{id}/file**：音乐文件信息与删除状态
- **DELETE /api/music/{id}**：安全删除音乐文件，移动到音乐库根目录 `.music-vault-trash`
- **GET /api/music/trash**：查看回收站音乐记录
- **POST /api/music/{id}/restore**：从 `.music-vault-trash` 恢复音乐文件到原路径
- **DELETE /api/music/{id}/trash**：彻底删除回收站文件，数据库记录保留为 `deleted`
- **POST /api/lyrics/scan**：扫描本地 LRC 歌词并尝试自动绑定歌曲
- **GET /api/lyrics**：歌词管理页列表查询，支持分页、关键词、绑定状态、解析状态和来源过滤
- **GET /api/lyrics/{id}**：歌词详情查询，返回歌词原文、来源信息和绑定歌曲摘要
- **GET /api/songs/{songId}/lyrics**：获取音乐列表中某首歌的主歌词
- **文件名元数据兜底**：`Artist - Title.flac` 格式自动解析为 `artist`/`title`，其余字段为 `null`
- **重复扫描跳过**：文件大小和修改时间均未变化时跳过（1秒容差）
- **隐藏文件和回收目录跳过**：扫描时跳过以 `.` 开头的路径节点，并显式忽略音乐库根目录下 `.music-vault-trash`
- **默认音乐目录**：`/Users/wangjiqing/Project/Musics/Music`（`music-vault.music-dirs` 配置）
- **默认歌词目录**：`/Users/wangjiqing/Project/Musics/Lyrics`（`music-vault.lyric-dirs` 配置）

- **Track CRUD**：曲目的创建、查询、更新、删除
- **本地音乐库扫描**：创建扫描任务后递归扫描本地目录，记录音频文件到 `track_files`
- **扫描稳定性**：重复运行保护（`running`/`completed` 状态任务禁止重复执行，返回 `409`）、扫描路径安全校验（禁止扫描 `/`、`/etc`、`/Users`、`/home` 等危险路径，禁止路径穿越，禁止扫描配置目录外路径）
- **列表分页与筛选**：ScanJob 支持 `page`/`size`/`status` 筛选，TrackFile 支持 `page`/`size`/`ext`/`keyword` 筛选
- **前端 Token 设置**：Web 管理后台内置 API Token 配置页面，Token 存储于浏览器 `localStorage`
- **Health API**：服务健康检查
- **Bearer Token 鉴权**：所有 `/api/*` 接口（除 `/api/health` 外）需要 Authorization header
- **SQLite + Flyway**：数据库自动迁移

**本阶段不包含**：音频内嵌元数据提取（ffprobe）、在线歌词抓取、封面刮削、MusicBrainz/LRCLIB 等联网匹配、音频指纹、歌手/专辑管理、登录系统、复杂权限系统。

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
/Users/wangjiqing/Project/Musics/Music
```

The dev profile uses this local lyric root by default:

```text
/Users/wangjiqing/Project/Musics/Lyrics
```

Production and Docker Compose defaults still use `/music`, and any environment can override the allowed scan roots with `MUSIC_VAULT_MUSIC_DIRS` and `MUSIC_VAULT_LYRIC_DIRS`.

You can override paths with environment variables when needed:

```bash
MUSIC_VAULT_DB_PATH=/tmp/xingyu-music-vault-data/music-vault.db \
MUSIC_VAULT_DATA_DIR=/tmp/xingyu-music-vault-data \
MUSIC_VAULT_MUSIC_DIRS=/path/to/music \
MUSIC_VAULT_LYRIC_DIRS=/path/to/lyrics \
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

The scanner records local files with extensions `mp3`, `flac`, `wav`, `m4a`, `aac`, `ogg`, and `opus` into `track_files`. Re-running a scan updates existing rows by `file_path` instead of inserting duplicates. It does not call ffprobe, extract embedded tags, fetch online lyrics, fetch cover art, perform network matching, or modify local music files.

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

## Music Scan API (v0.3)

`POST /api/music/scan` 是 v0.3 新增的快捷扫描入口，等价于「创建 ScanJob + 立即执行」，返回 `202 Accepted` 后台异步执行：

```bash
# 使用默认音乐目录扫描（/Users/wangjiqing/Project/Musics/Music）
curl -i -X POST http://localhost:8080/api/music/scan \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{}'

# 指定目录扫描
curl -i -X POST http://localhost:8080/api/music/scan \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"path": "/Users/wangjiqing/Project/Musics/Music"}'
```

响应：

```json
{
  "accepted": true,
  "scanJobId": 2,
  "message": "Scan accepted"
}
```

查询音乐列表（分页，按入库时间倒序）：

```bash
curl 'http://localhost:8080/api/music?page=0&size=20' \
  -H 'Authorization: Bearer change-me'
```

查询单条音乐详情：

```bash
curl 'http://localhost:8080/api/music/1' \
  -H 'Authorization: Bearer change-me'
```

响应示例：

```json
{
  "id": 1,
  "title": "晴天",
  "artist": "周杰伦",
  "album": null,
  "albumArtist": "周杰伦",
  "duration": null,
  "lyricStatus": "BOUND",
  "lyricId": 1,
  "filePath": "/Users/wangjiqing/Project/Musics/周杰伦 - 晴天.flac",
  "fileName": "周杰伦 - 晴天.flac",
  "fileExtension": "flac",
  "fileSize": 12345678,
  "lastModifiedTime": "2026-05-14T06:40:00",
  "createdAt": "2026-05-14T06:40:00",
  "updatedAt": "2026-05-14T06:40:00"
}
```

`page` 从 0 开始，默认 `size=20`，最大 `size=100`。

## Lyrics API (v0.5 / v0.5.2)

`POST /api/lyrics/scan` 扫描本地 LRC 歌词目录，按内容 SHA-256 去重，并基于 LRC 标签或文件名中的 `歌手 - 歌名` 自动绑定音乐列表中的歌曲：

```bash
curl -i -X POST http://localhost:8080/api/lyrics/scan \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"path": "/Users/wangjiqing/Project/Musics/Lyrics"}'
```

响应示例：

```json
{
  "path": "/Users/wangjiqing/Project/Musics/Lyrics",
  "totalFiles": 278,
  "imported": 0,
  "duplicateFiles": 278,
  "matched": 274,
  "unmatched": 4,
  "skippedBindings": 0,
  "failed": 0
}
```

查询歌词列表（分页，按入库时间倒序）：

```bash
curl 'http://localhost:8080/api/lyrics?page=0&size=20&keyword=晴天&bindStatus=BOUND&parseStatus=SUCCESS&sourceType=LOCAL_FILE' \
  -H 'Authorization: Bearer change-me'
```

响应示例：

```json
{
  "items": [
    {
      "id": 1,
      "title": "晴天",
      "artist": "周杰伦",
      "album": "叶惠美",
      "sourceType": "LOCAL_FILE",
      "sourcePath": "/Users/wangjiqing/Project/Musics/Lyrics/周杰伦 - 晴天.lrc",
      "format": "LRC",
      "parseStatus": "SUCCESS",
      "parseMessage": null,
      "bindStatus": "BOUND",
      "boundSongId": 1,
      "boundSongTitle": "晴天",
      "boundSongArtist": "周杰伦",
      "matchType": "TITLE_ARTIST",
      "matchScore": 100,
      "isPrimary": true,
      "createdAt": "2026-05-15T06:40:00",
      "updatedAt": "2026-05-15T06:40:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

查询歌词详情：

```bash
curl 'http://localhost:8080/api/lyrics/1' \
  -H 'Authorization: Bearer change-me'
```

详情会返回 `content`、`contentHash`、`boundSong` 和 `boundSongs`。当前页面可优先使用 `boundSong` 展示主绑定，后续多绑定视图可使用 `boundSongs`。

查询歌曲主歌词：

```bash
curl 'http://localhost:8080/api/songs/1/lyrics' \
  -H 'Authorization: Bearer change-me'
```

首次导入或旧库升级时，请先跑 `POST /api/music/scan`，等扫描任务完成后再跑 `POST /api/lyrics/scan`。歌词绑定依赖音乐扫描生成的 `tracks` 元数据；如果历史 `track_files.track_id` 为空，重复音乐扫描会补齐它。

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
