# API 文档

## 基础信息

- **前缀**: `/api`
- **鉴权**: `Authorization: Bearer <token>`
- **响应格式**: JSON
- **错误格式**: `{ "error": "...", "message": "..." }`

`/api/health` 为公开接口。其他已实现的 `/api/*` 接口需要 Bearer Token。

## 已实现接口

### 健康检查

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/health` | 服务健康状态 |

### 曲目

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/tracks` | 曲目列表 |
| GET | `/api/tracks/{id}` | 曲目详情 |
| POST | `/api/tracks` | 创建曲目 |
| PUT | `/api/tracks/{id}` | 更新曲目 |
| DELETE | `/api/tracks/{id}` | 删除曲目 |

#### 创建曲目

请求：

```http
POST /api/tracks
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "title": "First Song"
}
```

响应：

```json
{
  "id": 1,
  "title": "First Song",
  "normalizedTitle": "first song",
  "artist": null,
  "album": null,
  "albumArtist": null,
  "duration": null,
  "metadataStatus": "pending",
  "lyricsStatus": "pending",
  "artworkStatus": "pending",
  "createdAt": "2026-05-12T14:20:00",
  "updatedAt": "2026-05-12T14:20:00"
}
```

`title` 必填，去除首尾空白后不能为空。`metadataStatus`、`lyricsStatus`、`artworkStatus` 可选，允许值为 `pending`、`matched`、`missing`、`ignored`，默认 `pending`。

#### 查询曲目列表

```http
GET /api/tracks
Authorization: Bearer change-me
```

```json
[
  {
    "id": 1,
    "title": "First Song",
    "normalizedTitle": "first song",
    "artist": null,
    "album": null,
    "albumArtist": null,
    "duration": null,
    "metadataStatus": "pending",
    "lyricsStatus": "pending",
    "artworkStatus": "pending",
    "createdAt": "2026-05-12T14:20:00",
    "updatedAt": "2026-05-12T14:20:00"
  }
]
```

#### 查询、更新、删除单个曲目

```http
GET /api/tracks/1
Authorization: Bearer change-me
```

```http
PUT /api/tracks/1
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "title": "Updated Song",
  "metadataStatus": "matched"
}
```

```http
DELETE /api/tracks/1
Authorization: Bearer change-me
```

删除成功返回 `204 No Content`。

#### 错误响应

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "bad_request",
  "message": "title is required"
}
```

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "error": "unauthorized",
  "message": "Missing or invalid bearer token"
}
```

```http
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "error": "not_found",
  "message": "Track not found"
}
```

### 扫描任务

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/scan-jobs?page=0&size=20&status=completed` | 扫描任务分页列表，可按状态过滤 |
| POST | `/api/scan-jobs` | 创建 pending 状态扫描任务 |
| GET | `/api/scan-jobs/{id}` | 扫描任务详情 |
| POST | `/api/scan-jobs/{id}/run` | 执行扫描任务 |

v0.2 扫描只做本地文件发现和 `track_files` 记录。它不会提取音频内嵌元数据，不会抓歌词，不会抓封面，不会访问 MusicBrainz、LRCLIB 或其他外部网络服务，也不会做音频指纹。

#### 创建扫描任务

```http
POST /api/scan-jobs
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "jobType": "library_scan",
  "musicDirs": ["/music"]
}
```

响应：

```json
{
  "id": 1,
  "jobType": "library_scan",
  "status": "pending",
  "musicDirs": ["/music"],
  "totalFiles": 0,
  "scannedFiles": 0,
  "newFiles": 0,
  "updatedFiles": 0,
  "skippedFiles": 0,
  "errorFiles": 0,
  "errorMessage": null,
  "startedAt": null,
  "finishedAt": null,
  "createdAt": "2026-05-13T07:30:00",
  "updatedAt": "2026-05-13T07:30:00"
}
```

`POST /api/scan-jobs` 只创建任务，不立即扫描。未传 `musicDirs` 时使用 `MUSIC_VAULT_MUSIC_DIRS` 配置。

#### 查询扫描任务列表

```http
GET /api/scan-jobs?page=0&size=20&status=completed
Authorization: Bearer change-me
```

`status` 可选，允许值：`pending`、`running`、`completed`、`failed`。非法状态返回 `400`。

响应：

```json
{
  "items": [
    {
      "id": 1,
      "jobType": "library_scan",
      "status": "completed",
      "musicDirs": ["/music"],
      "totalFiles": 3,
      "scannedFiles": 2,
      "newFiles": 2,
      "updatedFiles": 0,
      "skippedFiles": 1,
      "errorFiles": 0,
      "errorMessage": null,
      "startedAt": "2026-05-13T07:31:00",
      "finishedAt": "2026-05-13T07:31:01",
      "createdAt": "2026-05-13T07:30:00",
      "updatedAt": "2026-05-13T07:31:01"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

#### 执行扫描任务

```http
POST /api/scan-jobs/1/run
Authorization: Bearer change-me
```

扫描支持扩展名：`mp3`、`flac`、`wav`、`m4a`、`aac`、`ogg`、`opus`。重复扫描相同 `file_path` 不会重复插入；当 `file_size` 和 `last_modified_at` 未变化时会跳过，变化时会更新 `file_size`、`last_modified_at`、`scan_job_id`、兜底元数据与 `updated_at`。只有 `pending` 和 `failed` 任务可以执行；`running` 或 `completed` 任务再次执行会返回 `409`：

```json
{
  "error": "conflict",
  "message": "Completed scan job cannot be run again"
}
```

扫描目录必须位于配置允许的 `MUSIC_VAULT_MUSIC_DIRS` 范围内，并会经过真实路径归一化校验。路径穿越、非允许目录、根目录类危险路径、目录不存在、不可读或不是目录都会使任务进入 `failed`，并记录 `errorMessage`。

### 音乐库

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/music/scan` | 接受一次后台本地音乐扫描任务，默认扫描 `music.scan.default-path` |
| GET | `/api/music?page=0&size=20` | 音乐分页列表 |
| GET | `/api/music/{id}` | 音乐详情 |
| PUT | `/api/music/{musicId}/artwork` | 绑定音乐主封面 |
| DELETE | `/api/music/{musicId}/artwork` | 取消音乐主封面绑定 |

`POST /api/music/scan` 可传空对象使用默认目录，也可传入 `path` 覆盖本次扫描目录：

```http
POST /api/music/scan
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "path": "/Users/wangjiqing/Project/Musics"
}
```

响应为 `202 Accepted`，扫描在后台执行。客户端可用返回的 `scanJobId` 轮询 `GET /api/scan-jobs/{id}` 获取 `status`、统计数量和失败原因。

```json
{
  "accepted": true,
  "scanJobId": 1,
  "message": "Scan accepted"
}
```

扫描会递归读取目录，跳过隐藏文件和非音乐文件。当前不引入音频标签解析依赖，元数据采用文件名兜底：`周杰伦 - 晴天.flac` 会解析为 `artist = 周杰伦`、`title = 晴天`；无法解析时 `artist = Unknown`、`title = 文件名去后缀`。如果历史 `track_files` 行缺少 `track_id`，重复音乐扫描会补建 `tracks` 元数据，供歌词匹配使用。

查询列表：

```http
GET /api/music?page=0&size=20
Authorization: Bearer change-me
```

```json
{
  "items": [
    {
      "id": 1,
      "title": "晴天",
      "artist": "周杰伦",
      "album": null,
      "albumArtist": "周杰伦",
      "duration": null,
      "lyricStatus": "BOUND",
      "lyricId": 1,
      "artworkStatus": "BOUND",
      "artworkId": 1,
      "artworkPreviewUrl": "/api/artworks/1/file",
      "artworkFileName": "周杰伦 - 晴天.png",
      "filePath": "/Users/wangjiqing/Project/Musics/周杰伦 - 晴天.flac",
      "fileName": "周杰伦 - 晴天.flac",
      "fileExtension": "flac",
      "fileSize": 123456,
      "lastModifiedTime": "2026-05-14T06:40:00",
      "createdAt": "2026-05-14T06:40:00",
      "updatedAt": "2026-05-14T06:40:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

查询详情：

```http
GET /api/music/1
Authorization: Bearer change-me
```

不存在时返回 `404`。扫描目录不存在、不可读或不在允许根目录下时，后台扫描任务会进入 `failed`，失败原因写入 `/api/scan-jobs/{id}` 的 `errorMessage`。

绑定音乐主封面：

```http
PUT /api/music/1/artwork
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "artworkId": 1
}
```

响应：

```json
{
  "musicId": 1,
  "artworkStatus": "BOUND",
  "artworkId": 1,
  "artworkPreviewUrl": "/api/artworks/1/file",
  "artworkFileName": "周杰伦 - 晴天.png"
}
```

取消绑定：

```http
DELETE /api/music/1/artwork
Authorization: Bearer change-me
```

响应：

```json
{
  "musicId": 1,
  "artworkStatus": "MISSING",
  "artworkId": null,
  "artworkPreviewUrl": null,
  "artworkFileName": null
}
```

#### curl 验证

```bash
curl -X POST http://localhost:8080/api/music/scan \
  -H "Authorization: Bearer change-me" \
  -H "Content-Type: application/json" \
  -d '{}'

curl "http://localhost:8080/api/music?page=0&size=20" \
  -H "Authorization: Bearer change-me"

curl "http://localhost:8080/api/music/1" \
  -H "Authorization: Bearer change-me"

curl "http://localhost:8080/api/scan-jobs/1" \
  -H "Authorization: Bearer change-me"
```

### 音频文件记录

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/track-files?page=0&size=20` | 文件记录分页列表 |
| GET | `/api/track-files?ext=flac&keyword=live` | 按扩展名和文件名/路径关键词过滤 |
| GET | `/api/track-files/{id}` | 文件记录详情 |

响应示例：

```json
{
  "items": [
    {
      "id": 1,
      "trackId": null,
      "filePath": "/music/a.flac",
      "fileName": "a.flac",
      "fileExt": "flac",
      "fileSize": 123456,
      "lastModifiedAt": "2026-05-13T07:31:00",
      "scanJobId": 1,
      "createdAt": "2026-05-13T07:31:00",
      "updatedAt": "2026-05-13T07:31:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

## 封面管理 API（v0.6）

v0.6 只支持本地封面文件扫描、去重、查询、文件访问和音乐绑定。不做在线封面刮削、AI 生成、复杂审核、多版本封面管理、用户系统或云同步。

默认扫描目录配置为 `app.artwork.scan-dir`，本地开发默认值：

```yaml
app:
  artwork:
    scan-dir: /Users/wangjiqing/Project/Musics/Artworks
```

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/artworks?page=0&size=20` | 封面列表分页查询 |
| GET | `/api/artworks/{id}` | 封面详情 |
| GET | `/api/artworks/{id}/file` | 封面文件访问，供 `<img>` 使用 |
| POST | `/api/artworks/scan` | 扫描本地封面目录 |

### 封面列表查询

```http
GET /api/artworks?page=0&size=20
Authorization: Bearer change-me
```

响应示例：

```json
{
  "items": [
    {
      "id": 1,
      "fileName": "周杰伦 - 晴天.png",
      "fileExt": "png",
      "mimeType": "image/png",
      "fileSize": 123456,
      "width": 600,
      "height": 600,
      "hash": "a3f5e2d...",
      "sourceType": "local",
      "sourcePath": "/Users/wangjiqing/Project/Musics/Artworks/周杰伦 - 晴天.png",
      "title": "周杰伦 - 晴天",
      "description": null,
      "previewUrl": "/api/artworks/1/file",
      "createdAt": "2026-05-15T22:40:00",
      "updatedAt": "2026-05-15T22:40:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

### 封面详情

```http
GET /api/artworks/1
Authorization: Bearer change-me
```

响应字段同列表项。

### 封面文件访问

```http
GET /api/artworks/1/file
Authorization: Bearer change-me
```

成功时返回图片二进制，`Content-Type` 为入库时记录的 MIME 类型。该接口不会接受任意文件路径，只能通过已入库的 `artwork.id` 访问，并会校验真实文件路径仍位于 `app.artwork.scan-dir` 根目录内。

### 扫描封面

```http
POST /api/artworks/scan
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "path": "/Users/wangjiqing/Project/Musics/Artworks"
}
```

`path` 可省略，省略时使用 `app.artwork.scan-dir`。扫描支持 `jpg`、`jpeg`、`png`、`webp`，按真实文件路径和 SHA-256 文件哈希去重。扫描目录和扫描到的真实文件路径都必须位于配置根目录内，目录穿越和指向根目录外的符号链接文件会被拒绝或计入失败。

扫描入库后会尝试按文件名自动绑定：封面文件名去扩展名后，与 `track_files.file_name` 或 `track_files.file_path` 文件名去扩展名完全相同，则写入 `music_artwork_bindings`。当前 `music_artwork_bindings.music_id` 使用 `track_files.id`，`relation_type` 使用 `track_cover`。已有主封面绑定时不会覆盖。

响应示例：

```json
{
  "path": "/Users/wangjiqing/Project/Musics/Artworks",
  "totalFiles": 278,
  "imported": 200,
  "duplicateFiles": 78,
  "autoBound": 196,
  "unmatched": 4,
  "failed": 0
}
```

## 歌词管理 API（v0.5 / v0.5.2）

v0.5 / v0.5.2 只支持本地 LRC 导入、去重、自动匹配和查询，不做在线歌词刮削、歌词编辑器、多版本审核或播放器逐句滚动。

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/lyrics` | 歌词列表分页查询（v0.5.2） |
| GET | `/api/lyrics/{id}` | 歌词详情查询，包含歌词原文和绑定歌曲（v0.5.2） |
| POST | `/api/lyrics/scan` | 扫描本地 LRC 歌词目录并尝试绑定歌曲 |
| GET | `/api/songs/{songId}/lyrics` | 获取音乐列表中某首歌的主歌词 |

`songId` 当前对应 `track_files.id`，也就是 `GET /api/music` 返回的 `id`。

### 歌词列表查询

```http
GET /api/lyrics?page=0&size=20&bindStatus=BOUND&parseStatus=SUCCESS&sourceType=LOCAL_FILE
Authorization: Bearer change-me
```

`page` 从 0 开始。`keyword` 支持标题、歌手、专辑、文件名的模糊搜索。

响应示例：

```json
{
  "items": [
    {
      "id": 1,
      "title": "晴天",
      "artist": "周杰伦",
      "album": "叶惠美",
      "language": null,
      "releaseYear": null,
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
      "createdAt": "2026-05-14T06:40:00",
      "updatedAt": "2026-05-14T06:40:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

`parseStatus` 页面展示值为 `SUCCESS`（内部存储 `PARSED`）/ `FAILED`（`PARSE_FAILED`）/ `UNKNOWN`（未解析或其他）。
`bindStatus` 为 `BOUND`（已绑定歌曲）或 `UNBOUND`（未绑定）。
`sourceType` 当前固定为 `LOCAL_FILE`，`MANUAL` 和 `ONLINE` 为后续预留。
列表接口不返回 `content`，查看歌词原文请调用 `GET /api/lyrics/{id}`。

### 歌词详情查询

```http
GET /api/lyrics/1
Authorization: Bearer change-me
```

响应示例：

```json
{
  "id": 1,
  "title": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "language": null,
  "releaseYear": null,
  "sourceType": "LOCAL_FILE",
  "sourcePath": "/Users/wangjiqing/Project/Musics/Lyrics/周杰伦 - 晴天.lrc",
  "format": "LRC",
  "content": "[ti:晴天]\n[ar:周杰伦]\n[al:叶惠美]\n[00:00.00] 作曲 : 周杰伦\n[00:05.00] 作词 : 方文山\n[00:10.00] 故事的小黄花\n...",
  "contentHash": "a3f5e2d...",
  "parseStatus": "SUCCESS",
  "parseMessage": null,
  "bindStatus": "BOUND",
  "boundSong": {
    "songId": 1,
    "title": "晴天",
    "artist": "周杰伦",
    "album": "叶惠美",
    "fileName": "周杰伦 - 晴天.flac",
    "matchType": "TITLE_ARTIST",
    "matchScore": 100,
    "isPrimary": true
  },
  "boundSongs": [
    {
      "songId": 1,
      "title": "晴天",
      "artist": "周杰伦",
      "album": "叶惠美",
      "fileName": "周杰伦 - 晴天.flac",
      "matchType": "TITLE_ARTIST",
      "matchScore": 100,
      "isPrimary": true
    }
  ],
  "createdAt": "2026-05-14T06:40:00",
  "updatedAt": "2026-05-14T06:40:00"
}
```

`boundSong` 为当前主绑定歌曲（无主绑定时为第一条绑定），`boundSongs` 为该歌词的所有绑定记录列表。
若歌词未绑定任何歌曲，`boundStatus` 返回 `UNBOUND`，`boundSong` 和 `boundSongs` 均为空。

### 扫描歌词

```http
POST /api/lyrics/scan
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "path": "/Users/wangjiqing/Project/Musics/Lyrics",
  "overwritePrimary": false
}
```

`path` 为空时使用 `music-vault.lyric-dirs` 的第一个目录。扫描目录必须位于 `MUSIC_VAULT_LYRIC_DIRS` 允许范围内。`overwritePrimary` 默认为 `false`，不会覆盖已有主歌词绑定。

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

歌词扫描会递归查找 `.lrc` 文件，读取 `[ti:]`、`[ar:]`、`[al:]` 标签和文件名中的 `歌手 - 歌名` 作为基础元数据，使用内容 SHA-256 去重。自动绑定依赖音乐扫描生成的 `tracks.normalized_title`，因此首次导入或旧库升级时推荐顺序是：

1. 先执行 `POST /api/music/scan`，补齐 `track_files.track_id` 和 `tracks` 元数据。
2. 等扫描任务 `completed` 后，再执行 `POST /api/lyrics/scan`。
3. 刷新 `GET /api/music`，已绑定歌曲会返回 `lyricStatus = BOUND` 和 `lyricId`。

### 查询歌曲歌词

```http
GET /api/songs/1/lyrics
Authorization: Bearer change-me
```

响应示例：

```json
{
  "songId": 1,
  "lyricStatus": "BOUND",
  "lyricId": 1,
  "title": "晴天",
  "artist": "周杰伦",
  "album": "叶惠美",
  "language": null,
  "releaseYear": null,
  "sourceType": "LOCAL_FILE",
  "sourcePath": "/Users/wangjiqing/Project/Musics/Lyrics/周杰伦 - 晴天.lrc",
  "format": "LRC",
  "parseStatus": "PARSED",
  "parseMessage": null,
  "content": "[ti:晴天]\n[ar:周杰伦]\n...",
  "createdAt": "2026-05-14T06:40:00",
  "updatedAt": "2026-05-14T06:40:00"
}
```

当前歌曲歌词状态包括：`BOUND`、`NO_LYRIC`、`PARSE_FAILED`、`MISSING_FILE`。`UNMATCHED` 是歌词扫描统计语义，表示导入了歌词文件但未找到歌曲候选。

## 规划中接口

以下接口仅为规划，当前版本尚未实现。

### 曲目

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/tracks/match` | 发起曲目匹配 |

### 歌手

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/artists` | 歌手列表 |
| GET | `/api/artists/{id}` | 歌手详情（含专辑列表） |

### 专辑

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/albums` | 专辑列表 |
| GET | `/api/albums/{id}` | 专辑详情 |

### 歌词后续能力

| Method | Path | 说明 |
|--------|------|------|
| PUT | `/api/lyrics/{id}` | 编辑歌词内容 |
| GET | `/api/lyrics/{id}/versions` | 获取歌词版本 |

### 审核

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/review-items` | 待审核项列表 |

### 设置

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/settings` | 获取系统配置 |
| PUT | `/api/settings` | 更新系统配置 |

## 分页与过滤

`GET /api/music`、`GET /api/scan-jobs` 和 `GET /api/track-files` 返回统一分页结构：

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "total": 0
}
```

| 参数 | 说明 |
|------|------|
| `page` | 页码，从 0 开始，默认 0 |
| `size` | 每页条数，默认 20，最大 100 |
| `status` | `scan-jobs` 可用，允许 `pending`、`running`、`completed`、`failed` |
| `ext` | `track-files` 可用，匹配 `fileExt` |
| `keyword` | `track-files` 可用，模糊匹配 `fileName` 或 `filePath` |

## 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未鉴权 |
| 409 | 资源状态冲突 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
