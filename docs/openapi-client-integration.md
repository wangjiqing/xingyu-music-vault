# OpenAPI 客户端接入指南

> 本文档面向播放器客户端开发者，说明如何通过星语音库 OpenAPI 接入音乐库。

## 概述

星语音库 OpenAPI v0.9.0 提供面向播放器客户端的只读音乐库查询接口。客户端可通过本 API 查询曲目、歌词、封面、歌手、专辑等元数据，并建立本地音乐与服务端元数据的关联。

**当前版本为只读 MVP，不支持音频流、不支持客户端修改元数据、不支持上传音乐。**

**认证方式：** OpenAPI `/api/open/v1/*` 路径受后端全局 Bearer Token 保护，所有请求需要在请求头中携带 `Authorization: Bearer <token>`。例外公开接口包括 `/api/health`、`/q/*`、旧版 `GET /api/artworks/{id}/file`。

## 路径前缀

`/api/open/v1`

所有接口均以此前缀开始。

## 接入流程

### 第一步：验证服务可用性

客户端启动时，优先调用服务信息接口，验证服务端可用且版本兼容：

```http
GET /api/open/v1/server/info
```

```json
{
  "serviceName": "xingyu-music-vault",
  "serviceVersion": "0.9.0",
  "apiVersion": "v1",
  "readOnly": true,
  "features": {
    "tracks": true,
    "lyrics": true,
    "artwork": true,
    "artists": true,
    "albums": true,
    "matchTrack": true,
    "streaming": false,
    "writeBack": false,
    "scanTrigger": false
  }
}
```

`readOnly: true` 表示当前版本所有 OpenAPI 均为只读。`features` 中 `true` 表示该功能已实现，`false` 表示未实现。若 `apiVersion` 不符合客户端预期，或 `features` 中需要的功能为 `false`，应给出明确提示，避免后续调用失败。

### 第二步：检查音乐库是否变化

调用音乐库状态接口，获取当前版本号：

```http
GET /api/open/v1/sync/state
```

```json
{
  "trackCount": 529,
  "artistCount": 42,
  "albumCount": 38,
  "lyricsCount": 487,
  "artworkCount": 510,
  "lastUpdatedAt": "2026-05-26T06:00:00"
}
```

`lastUpdatedAt` 是活跃歌曲 `track_files.updatedAt` 与关联 `tracks.updatedAt` 中的最大值（取较新者）。客户端应将 `lastUpdatedAt` 存储在本地。若与服务端返回值一致，说明音乐库未变化，可跳过全量拉取；若不一致，按需重新拉取曲目列表。本接口不返回 `lastScanAt`。

### 第三步：拉取曲目列表

使用分页参数逐页拉取，不要一次请求全量：

```http
GET /api/open/v1/tracks?page=0&pageSize=50&keyword=周杰伦
```

可用过滤参数：

|| 参数 | 说明 |
|------|------|
| `page` | 页码，从 0 开始 |
| `pageSize` | 每页条数，默认 20，最大 100 |
| `keyword` | 模糊匹配文件名、标题、歌手、专辑、流派 |
| `artist` | 按歌手名精确过滤（小写 trim 后比较） |
| `album` | 按专辑名精确过滤（小写 trim 后比较） |
| `year` | 按年份精确过滤 |
| `genre` | 按流派精确过滤 |
| `metadataStatus` | 按元数据状态过滤，允许值：`pending`、`matched`、`missing`、`ignored` |
| `lyricsStatus` | 按歌词状态过滤，允许值：`BOUND`（含 `AVAILABLE`）、`NO_LYRIC`（含 `MISSING`） |
| `artworkStatus` | 按封面状态过滤，允许值：`BOUND`（含 `AVAILABLE`）、`MISSING` |
| `updatedAfter` | ISO-8601 日期时间，过滤指定时间后有变更的曲目 |
| `sort` | 排序字段：`updatedAt`（默认）、`title`、`artist`、`album`、`year`、`durationMs`、`trackNo`、`metadataStatus`、`lyricsStatus`、`artworkStatus`、`fileName`、`createdAt` |
| `order` | 排序方向：`asc` 或 `desc`（默认 `desc`） |

翻页时递增 `page`，直至 `items` 为空或达到预期总量。

**列表项中的 `artworkUrl` 是相对路径（如 `/api/open/v1/tracks/1/artwork`），不是 base64，也不是完整 URL。** 客户端使用服务端 base URL 拼接该相对路径获取图片。不要将封面 data URL 嵌入列表项。

### 第四步：按需拉取歌词和封面

歌词和封面正文不随列表接口返回，需要时单独调用：

```http
# 歌词原文
GET /api/open/v1/tracks/{id}/lyrics

# 封面文件（二进制流）
GET /api/open/v1/tracks/{id}/artwork
```

封面接口直接返回图片二进制流，`Content-Type` 为图片 MIME 类型，支持 PNG/JPEG/WebP，含 HTTP 缓存头（`Cache-Control: max-age=3600`）和 ETag。列表项中的 `artworkUrl` 字段返回相对路径，客户端使用服务端 base URL 拼接后获取图片。

无歌词或无封面时接口返回 `404`，客户端应做好降级处理。

歌词元数据和封面元数据接口可用于快速判断，无需下载正文：

```http
# 歌词元数据（available、format、hash，不含 language/hasTimeTag）
GET /api/open/v1/tracks/{id}/lyrics/meta

# 封面元数据（宽高、MIME、文件大小、hash，不含 fileName）
GET /api/open/v1/tracks/{id}/artwork/meta
```

### 第五步：本地缓存与条件请求

封面接口（`/api/open/v1/tracks/{id}/artwork`）支持 HTTP 缓存机制，设置 `Cache-Control: max-age=3600` 和 ETag，客户端可利用条件请求（`If-None-Match`/`If-Modified-Since`）避免重复下载。

歌词接口（`/api/open/v1/tracks/{id}/lyrics`）**不设置 HTTP 缓存头**，建议客户端直接本地缓存歌词内容。

### 第六步：关联本地音乐

若客户端持有本地音乐文件，可通过匹配接口查询服务端对应曲目 ID：

```http
GET /api/open/v1/match/track?title=晴天&artist=周杰伦&album=叶惠美&durationMs=268000
```

参数：

|| 参数 | 说明 |
|------|------|
| `title` | 歌曲标题（必填，且必须完全匹配） |
| `artist` | 歌手（选填，完全匹配加 15 分） |
| `album` | 专辑（选填，完全匹配加 10 分） |
| `durationMs` | 时长毫秒数（选填，与库内时长差值 ≤ ±3000ms 加 5 分） |

匹配规则：title 必填且必须完全匹配（基础分 70），artist 完全匹配加 15 分，album 完全匹配加 10 分，durationMs 与库内时长差值 ≤ ±3000ms 加 5 分。返回最高分候选，分数上限 100；无 title 完全匹配时返回 `matched: false`。

```json
{
  "matched": true,
  "score": 100,
  "reason": "title exact match; artist exact match; duration within +/-3000ms",
  "track": {
    "id": 1,
    "title": "晴天",
    "artist": "周杰伦",
    "album": "叶惠美",
    "albumArtist": "周杰伦",
    "durationMs": 268000,
    "year": 2003,
    "trackNo": 1,
    "genre": "流行",
    "metadataStatus": "matched",
    "lyricsStatus": "BOUND",
    "artworkStatus": "BOUND",
    "fileName": "周杰伦 - 晴天.flac",
    "fileExtension": "flac",
    "fileSize": 12345678,
    "lyricsAvailable": true,
    "lyricId": 1,
    "artworkAvailable": true,
    "artworkId": 1,
    "artworkUrl": "/api/open/v1/tracks/1/artwork",
    "createdAt": "2026-05-14T06:40:00",
    "updatedAt": "2026-05-14T06:40:00"
  }
}
```

无可匹配曲目时返回 `200` 且 `matched: false`，`score` 为 0，`reason` 为 `"No exact title match"`，`track` 为 `null`。客户端应在建立关联后再使用其他接口。

## 歌手与专辑

### 歌手列表

```http
GET /api/open/v1/artists
```

无分页参数，返回全量歌手列表，按歌手名字升序排列。响应为数组而非分页对象：

```json
[
  {
    "artistName": "周杰伦",
    "trackCount": 15,
    "albumCount": 3,
    "lyricsCount": 14,
    "artworkCount": 12
  }
]
```

### 歌手曲目

```http
GET /api/open/v1/artists/{artistName}/tracks?page=0&pageSize=20
```

`artistName` 为歌手名 URL-safe 编码。

### 专辑列表

```http
GET /api/open/v1/albums
```

无分页参数，返回全量专辑列表，按专辑名升序排列（同名专辑按专辑歌手升序）。响应为数组而非分页对象：

```json
[
  {
    "album": "叶惠美",
    "albumArtist": "周杰伦",
    "year": 2003,
    "trackCount": 5,
    "lyricsCount": 5,
    "artworkCount": 4
  }
]
```

### 专辑曲目

```http
GET /api/open/v1/albums/tracks?album=叶惠美&artist=周杰伦&page=0&pageSize=20
```

参数 `album` 和 `artist` 均为原始字符串，无需 URL-safe 编码。按 trackNo 升序排列。

## 完整请求示例

```bash
# 1. 验证服务可用性
curl http://localhost:8080/api/open/v1/server/info \
  -H "Authorization: Bearer <token>"

# 2. 检查音乐库版本
curl http://localhost:8080/api/open/v1/sync/state \
  -H "Authorization: Bearer <token>"

# 3. 拉取曲目列表（第 1 页，每页 50 条）
curl "http://localhost:8080/api/open/v1/tracks?page=0&pageSize=50" \
  -H "Authorization: Bearer <token>"

# 4. 按需拉取歌词（曲目 id=1）
curl http://localhost:8080/api/open/v1/tracks/1/lyrics \
  -H "Authorization: Bearer <token>"

# 5. 按需拉取封面（曲目 id=1）
curl http://localhost:8080/api/open/v1/tracks/1/artwork \
  -H "Authorization: Bearer <token>" \
  -o artwork.jpg

# 6. 本地音乐匹配
curl "http://localhost:8080/api/open/v1/match/track?title=晴天&artist=周杰伦" \
  -H "Authorization: Bearer <token>"
```

## 客户端不应尝试的功能

v0.9.0 是只读 MVP，以下能力不提供，客户端不应调用：

- **音频流播放** — `GET /api/open/v1/tracks/{id}/stream` 不存在
- **客户端修改元数据** — `PUT /api/open/v1/tracks/{id}` 不存在
- **上传音乐** — 文件上传接口不存在
- **OAuth / JWT** — 不需要也没实现 OAuth/JWT，OpenAPI 使用后端全局 Bearer Token
- **WebSocket 推送** — 不支持实时同步
- **远程扫描音乐库** — 不支持从客户端触发扫描
- **网络刮削** — 不支持在线获取元数据
- **AI 元数据补全** — 不支持 AI 生成或补全元数据

**注意：** 所有 OpenAPI 请求必须携带 `Authorization: Bearer <token>` 请求头。

## 接口清单

|| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/open/v1/server/info` | 服务信息（含 features 功能映射） |
| GET | `/api/open/v1/sync/state` | 音乐库状态（各维度统计 + lastUpdatedAt） |
| GET | `/api/open/v1/tracks` | 曲目列表（分页、多维过滤、排序） |
| GET | `/api/open/v1/tracks/{id}` | 曲目详情 |
| GET | `/api/open/v1/tracks/{id}/lyrics` | 歌词原文 |
| GET | `/api/open/v1/tracks/{id}/lyrics/meta` | 歌词元数据（含 available、format、hash） |
| GET | `/api/open/v1/tracks/{id}/artwork` | 封面图片（二进制流） |
| GET | `/api/open/v1/tracks/{id}/artwork/meta` | 封面元数据（含 available、mimeType、width、height、fileSize、hash，不含 fileName） |
| GET | `/api/open/v1/artists` | 歌手列表（全量，无分页） |
| GET | `/api/open/v1/artists/{artistName}/tracks` | 歌手曲目（分页） |
| GET | `/api/open/v1/albums` | 专辑列表（全量，无分页） |
| GET | `/api/open/v1/albums/tracks` | 专辑曲目（分页，参数为 album/artist） |
| GET | `/api/open/v1/match/track` | 本地音乐匹配（GET，title 必填） |

## 错误响应

所有接口错误响应格式统一：

```json
{
  "code": "TRACK_NOT_FOUND",
  "message": "Track not found",
  "traceId": "a1b2c3d4-e5f6-...",
  "details": {}
}
```

`traceId` 用于问题追踪。常见状态码：

|| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误（如分页参数超范围） |
| 401 | 未提供或无效的 Bearer Token |
| 404 | 资源不存在（如曲目无歌词/封面） |
| 500 | 服务器内部错误 |
