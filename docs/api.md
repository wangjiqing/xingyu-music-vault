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

扫描支持扩展名：`mp3`、`flac`、`wav`、`m4a`、`aac`、`ogg`、`opus`。重复扫描相同 `file_path` 会更新 `file_size`、`last_modified_at`、`scan_job_id` 与 `updated_at`，不会重复插入。只有 `pending` 和 `failed` 任务可以执行；`running` 或 `completed` 任务再次执行会返回 `409`：

```json
{
  "error": "conflict",
  "message": "Completed scan job cannot be run again"
}
```

扫描目录必须位于配置允许的 `MUSIC_VAULT_MUSIC_DIRS` 范围内，并会经过真实路径归一化校验。路径穿越、非允许目录、根目录类危险路径、目录不存在、不可读或不是目录都会使任务进入 `failed`，并记录 `errorMessage`。

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

### 歌词

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/tracks/{id}/lyrics` | 获取曲目歌词 |
| POST | `/api/tracks/{id}/lyrics` | 提交/更新歌词 |

### 封面

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/tracks/{id}/artwork` | 获取曲目封面 |
| POST | `/api/tracks/{id}/artwork` | 上传/更新封面 |

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

`GET /api/scan-jobs` 和 `GET /api/track-files` 返回统一分页结构：

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
