# OpenAPI 客户端接入指南

> 本文档面向播放器客户端开发者，说明如何通过星语音库 OpenAPI 接入音乐库。

## 概述

星语音库 OpenAPI v1.2.2 提供面向播放器客户端的只读音乐库查询接口，
在 v0.9.0 / v0.9.1 基础上补强缓存、增量同步、安全与访问控制能力。
客户端可通过本 API 查询曲目、歌词、封面、歌手、专辑等元数据，
并建立本地音乐与服务端元数据的关联。

**v0.9.5 已基于星语音乐盒局域网真机联调收口资源状态契约，但仍不做：公网 HTTPS、CI/CD、音频流、客户端写入元数据、WebSocket 推送。**

**当前版本为只读增强版本，不支持音频流、不支持客户端修改元数据、不支持上传音乐。**

**认证方式：** OpenAPI `/api/open/v1/*` 必须使用 AK/SK + HMAC-SHA256。部署方在管理后台创建 OpenAPI 凭证，创建响应中的 Secret Key 只显示一次。后台管理 API 仍使用账号密码登录 + Session Cookie，两者不混用。

## 访问风险提示

当前 OpenAPI 主要服务于本地 / 局域网内的星语音乐盒联调和访问，不建议将 `/api/open/v1/*` 裸露到公网。

当前版本的 OpenAPI 仍以只读访问为主；HMAC 不能替代 HTTPS，不建议将 `/api/open/v1/*` 裸露到公网。星语音乐盒需要后续版本适配新认证方式；适配前可继续使用上一版本星语音库进行联调。

在未来加入候选确认、移动端确认、AI 辅助整理等写操作前，必须先建立清晰的安全边界。当前说明为风险提示，不代表登录、公网 HTTPS、反向代理模板或完整权限模型已经实现。

## 安全配置

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `xingyu.openapi.credential.master-key` | 未配置 | Secret Key AES-GCM 加密 master key，生产环境必须配置 |
| `xingyu.openapi.hmac.timestamp-window-seconds` | `300` | timestamp 允许偏差窗口 |
| `xingyu.openapi.hmac.nonce-ttl-seconds` | `600` | nonce 防重放记录有效期 |
| `xingyu.openapi.auth.enabled` | `false` | 旧静态 Token 开关，v1.1.3 起废弃 |
| `xingyu.openapi.auth.token` | 未配置 | 旧静态 Token，v1.1.3 起废弃 |
| `xingyu.openapi.rate-limit.enabled` | `false` | 是否启用 OpenAPI 简单 IP 限流 |
| `xingyu.openapi.rate-limit.requests-per-minute` | `120` | 每个客户端 IP 每分钟请求数 |
| `xingyu.openapi.access-log.enabled` | `true` | 是否记录 OpenAPI 访问日志 |

认证失败返回 `401 OPENAPI_UNAUTHORIZED`。scope 不足返回 `403 OPENAPI_FORBIDDEN`。限流超过阈值时返回 `429 OPENAPI_RATE_LIMITED`。

## HMAC 签名

请求头：

```http
X-Xingyu-Access-Key: xmv_ak_xxx
X-Xingyu-Timestamp: 1717890000000
X-Xingyu-Nonce: 550e8400-e29b-41d4-a716-446655440000
X-Xingyu-Signature-Version: v1
X-Xingyu-Signature: <lowercase-hex-hmac-sha256>
```

签名原文固定 5 行：

```text
METHOD
PATH_WITH_CANONICAL_QUERY
SHA256_HEX_BODY
TIMESTAMP
NONCE
```

query 按参数名升序、同名参数按值升序，key/value 使用统一 URL 编码；无 query 时只使用 path。GET / DELETE 无 body 时使用空字符串的 SHA-256，POST / PUT / PATCH 使用原始 body 字节。当前读接口要求 `OPENAPI_READ`，未来写接口预留 `OPENAPI_WRITE`。

## 路径前缀

`/api/open/v1`

所有接口均以此前缀开始。

## baseUrl 配置

客户端应把服务根地址配置为 `musicVaultBaseUrl`，再拼接 OpenAPI 相对路径，不要写死具体主机、端口或完整接口地址。

| 场景 | `musicVaultBaseUrl` 示例 |
|------|--------------------------|
| 本机 | `http://localhost:8080` |
| Mac mini 局域网 | `http://<Mac-mini-LAN-IP>:8080` |
| NAS | `http://<NAS-LAN-IP>:8080` |

例如服务信息接口应拼接为：

```text
{musicVaultBaseUrl}/api/open/v1/server/info
```

## 接入流程

### 第一步：验证服务可用性

客户端启动时，优先调用服务信息接口，验证服务端可用且版本兼容：

```http
GET /api/open/v1/server/info
```

```json
{
  "serviceName": "xingyu-music-vault",
  "serviceVersion": "1.2.2",
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

`readOnly: true` 表示当前版本所有 OpenAPI 均为只读。
`features` 中 `true` 表示该功能已实现，`false` 表示未实现。
若 `apiVersion` 不符合客户端预期，或 `features` 中需要的功能为 `false`，
应给出明确提示，避免后续调用失败。

### 第二步：检查音乐库是否变化

调用音乐库状态接口，获取当前 `libraryVersion`：

```http
GET /api/open/v1/sync/state
```

```json
{
  "libraryVersion": 128,
  "trackCount": 529,
  "artistCount": 42,
  "albumCount": 38,
  "lyricsCount": 487,
  "artworkCount": 510,
  "lastUpdatedAt": "2026-05-26T06:00:00",
  "lastChangedAt": "2026-05-27T10:00:00+08:00",
  "changesAvailable": true
}
```

`libraryVersion` 是 OpenAPI 同步版本号，
每次记录歌曲、歌词或封面变更时递增，初始值为 1。
`lastUpdatedAt` 保留用于兼容旧客户端，
它表示活跃歌曲 `track_files.updatedAt` 与关联 `tracks.updatedAt` 中的最大值（取较新者）。
`changesAvailable` 表示服务端支持 `/sync/changes` 增量同步能力，
固定返回 `true`，不表示特定客户端是否存在待同步变更。
客户端应保存 `libraryVersion`，下次启动后与本地值比对，决定是否需要同步。
各计数字段均统计当前活跃曲目及其主绑定资源，不含已删除曲目。

**增量同步流程：**

1. 获取 `libraryVersion` 后，若与本地保存的版本相同，则跳过同步，直接使用本地缓存
2. 若 `libraryVersion` 变化，调用增量变更接口：

```http
GET /api/open/v1/sync/changes?sinceVersion=<localVersion>&limit=500
```

```json
{
  "fromVersion": 120,
  "toVersion": 128,
  "hasMore": false,
  "items": [
    {
      "version": 121,
      "entityType": "track",
      "entityId": 1001,
      "changeType": "updated",
      "changedFields": ["metadata", "lyrics"],
      "updatedAt": "2026-05-27T10:00:00+08:00"
    }
  ]
}
```

3. `sinceVersion` 默认 0，`limit` 默认 500、最大 1000。结果按 `version` 升序返回
4. `hasMore=true` 时继续用最后一条变更的 `version` 作为下一次 `sinceVersion`，分批拉取直至 `hasMore=false`
5. 对 `created` / `updated` 歌曲刷新本地缓存；对 `deleted` 歌曲移除本地缓存
6. 客户端保存最新 `libraryVersion`，用于下次启动时的增量判断

`changedFields` 可能的值：`metadata`（元数据更新）、`lyrics`（歌词更新）、`artwork`（封面更新）；
`changeType` 可能的值：`created`（新增）、`updated`（更新）、`deleted`（删除）。

### 第三步：拉取曲目列表

使用分页参数逐页拉取，不要一次请求全量：

```http
GET /api/open/v1/tracks?page=0&pageSize=50&keyword=周杰伦
```

可用过滤参数：

| 参数 | 说明 |
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
| `updatedAfter` | ISO-8601 日期时间，返回 `updatedAt` 严格晚于该时间的曲目（`updatedAt > updatedAfter`） |
| `sort` | 排序字段：`updatedAt`（默认）、`title`、`artist`、`album`、`year`、`durationMs`、`trackNo`、`metadataStatus`、`lyricsStatus`、`artworkStatus`、`fileName`、`createdAt` |
| `order` | 排序方向：`asc` 或 `desc`（默认 `desc`） |

`lyricsStatus=BOUND` 与 `artworkStatus=BOUND` 过滤器用于查找存在数据库主绑定关系的曲目；客户端是否应该继续请求歌词 / 封面资源，应以响应中的 `lyricsAvailable` / `artworkAvailable` 为准。

翻页时递增 `page`，直至 `items` 为空或达到预期总量。

**列表项中的 `artworkUrl` 是相对路径（如 `/api/open/v1/tracks/1/artwork`），不是 base64，也不是完整 URL。**
客户端使用服务端 base URL 拼接该相对路径获取图片。不要将封面 data URL 嵌入列表项。

### 第四步：按需拉取歌词和封面

歌词和封面正文不随列表接口返回，需要时单独调用：

```http
# 歌词原文
GET /api/open/v1/tracks/{id}/lyrics

# 封面文件（二进制流）
GET /api/open/v1/tracks/{id}/artwork
```

封面接口直接返回图片二进制流，`Content-Type` 为图片 MIME 类型，
支持 PNG/JPEG/WebP，含 HTTP 缓存头（`Cache-Control: max-age=3600`）和 ETag。
歌词接口返回 JSON，也会设置 ETag。
列表项中的 `artworkUrl` 字段返回相对路径，客户端使用服务端 base URL 拼接后获取图片。

无歌词或无封面时正文接口返回 `404`，客户端应做好降级处理。`404` 表示资源不存在或当前不可提供，不是致命错误。

歌词元数据和封面元数据接口可用于快速判断，无需下载正文：

```http
# 歌词元数据（available、format、hash、etag，不含 language/hasTimeTag）
GET /api/open/v1/tracks/{id}/lyrics/meta

# 封面元数据（宽高、MIME、文件大小、hash、etag，不含 fileName）
GET /api/open/v1/tracks/{id}/artwork/meta
```

### 资源可用状态契约

`available=true` 表示客户端可以继续请求对应资源，且预期可用。`available=false` 表示资源不存在、未绑定、文件缺失、路径不可访问或当前不可提供，客户端应降级为无歌词 / 无封面。

`tracks` 列表和详情中的 `lyricsAvailable` / `artworkAvailable` 与 meta 接口保持一致：

| 字段 | 语义 |
|------|------|
| `lyricsAvailable=true` | `/lyrics/meta` 返回 `available=true`，客户端可请求 `/lyrics` |
| `lyricsAvailable=false` | 客户端不应继续请求 `/lyrics`，可直接进入无歌词状态 |
| `artworkAvailable=true` | `/artwork/meta` 返回 `available=true`，客户端可请求 `/artwork` |
| `artworkAvailable=false` | 客户端不应继续请求 `/artwork`，可直接进入无封面状态 |

资源正文接口的 `404` 是可降级业务状态；`500` 表示服务端异常，客户端应降级并可记录错误。

星语音乐盒当前歌词来源策略为：本地已有缓存歌词 → 星语音库 OpenAPI 歌词 → 无歌词状态。客户端不再假设会请求公网 LRCLIB。

### 第五步：本地缓存与条件请求

歌词和封面正文接口都支持 `If-None-Match` 条件请求。
客户端可先读取 meta 接口中的 `hash` 和 `etag`，
若本地已有相同资源则跳过正文下载，或在正文请求中携带 `If-None-Match`：

```http
GET /api/open/v1/tracks/{id}/lyrics
If-None-Match: "lyrics-1-a3f5e2d..."
```

```http
GET /api/open/v1/tracks/{id}/artwork
If-None-Match: "artwork-1-b4c3d2e..."
```

命中当前 ETag 时返回 `304 Not Modified`；
未命中时返回 `200` 和完整歌词 JSON 或封面图片流。
歌词 hash 基于歌词内容、歌词格式、歌词语言三者按固定顺序组合后计算 SHA-256
（`null` 按空字符串处理，编码为 UTF-8）；封面 hash 基于图片二进制内容计算 SHA-256。

### 第六步：关联本地音乐

若客户端持有本地音乐文件，可通过匹配接口查询服务端对应曲目 ID：

```http
GET /api/open/v1/match/track?title=晴天&artist=周杰伦&album=叶惠美&durationMs=268000
```

参数：

| 参数 | 说明 |
|------|------|
| `title` | 歌曲标题（必填，且必须完全匹配） |
| `artist` | 歌手（选填，完全匹配加 15 分） |
| `album` | 专辑（选填，完全匹配加 10 分） |
| `durationMs` | 时长毫秒数（选填，与库内时长差值 ≤ ±3000ms 加 5 分） |

匹配规则：title 必填且必须完全匹配（基础分 70），
artist 完全匹配加 15 分，album 完全匹配加 10 分，
durationMs 与库内时长差值 ≤ ±3000ms 加 5 分。
返回最高分候选，分数上限 100；无 title 完全匹配时返回 `matched: false`。

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
BASE_URL="http://localhost:8080"
ACCESS_KEY="xmv_ak_xxx"
SECRET_KEY="xmv_sk_xxx"

openapi_get() {
  local path_with_query="$1"
  local output="${2:-}"
  local timestamp nonce body_hash canonical signature
  timestamp="$(date +%s000)"
  nonce="$(uuidgen)"
  body_hash="$(printf '' | shasum -a 256 | awk '{print $1}')"
  canonical="$(printf 'GET\n%s\n%s\n%s\n%s' "$path_with_query" "$body_hash" "$timestamp" "$nonce")"
  signature="$(printf '%s' "$canonical" | openssl dgst -sha256 -hmac "$SECRET_KEY" -binary | xxd -p -c 256)"
  headers=(-H "X-Xingyu-Access-Key: $ACCESS_KEY" -H "X-Xingyu-Timestamp: $timestamp" -H "X-Xingyu-Nonce: $nonce" -H "X-Xingyu-Signature-Version: v1" -H "X-Xingyu-Signature: $signature")
  if [ -n "$output" ]; then
    curl "$BASE_URL$path_with_query" "${headers[@]}" -o "$output"
  else
    curl "$BASE_URL$path_with_query" "${headers[@]}"
  fi
}

# 1. 验证服务可用性
openapi_get "/api/open/v1/server/info"

# 2. 检查音乐库版本
openapi_get "/api/open/v1/sync/state"

# 3. 拉取曲目列表（第 1 页，每页 50 条）
openapi_get "/api/open/v1/tracks?page=0&pageSize=50"

# 4. 按需拉取歌词（曲目 id=1）
openapi_get "/api/open/v1/tracks/1/lyrics"

# 5. 按需拉取封面（曲目 id=1）
openapi_get "/api/open/v1/tracks/1/artwork" "artwork.jpg"

# 6. 本地音乐匹配
openapi_get "/api/open/v1/match/track?artist=%E5%91%A8%E6%9D%B0%E4%BC%A6&title=%E6%99%B4%E5%A4%A9"
```

## 客户端不应尝试的功能

v0.9.5 是只读契约稳定版本，以下能力不提供，客户端不应调用：

- **音频流播放** — `GET /api/open/v1/tracks/{id}/stream` 不存在
- **客户端修改元数据** — `PUT /api/open/v1/tracks/{id}` 不存在
- **上传音乐** — 文件上传接口不存在
- **OAuth / JWT** — 不需要也没实现 OAuth/JWT，OpenAPI 使用可选 API Token
- **WebSocket 推送** — 不支持实时同步
- **远程扫描音乐库** — 不支持从客户端触发扫描
- **网络刮削** — 不支持在线获取元数据
- **AI 元数据补全** — 不支持 AI 生成或补全元数据
- **反向代理配置** — 不做反向代理配置测试

## 星语音乐盒联调反馈

局域网真机联调已验证以下接口范围：

```text
GET /api/open/v1/server/info
GET /api/open/v1/sync/state
GET /api/open/v1/match/track
GET /api/open/v1/tracks
GET /api/open/v1/tracks/{id}
GET /api/open/v1/tracks/{id}/lyrics/meta
GET /api/open/v1/tracks/{id}/lyrics
GET /api/open/v1/tracks/{id}/artwork/meta
GET /api/open/v1/tracks/{id}/artwork
```

星语音库控制面修改元数据、歌词或封面后，音乐盒刷新即可通过 OpenAPI 获取最新元数据 / 歌词 / 封面。

**注意：** v1.1.3 起 OpenAPI 请求必须携带 AK/SK + HMAC 签名；旧 `Authorization: Bearer <token>` 与 `X-Xingyu-Api-Token` 不再可用。

## 接口清单

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/open/v1/server/info` | 服务信息（含 features 功能映射） |
| GET | `/api/open/v1/sync/state` | 音乐库状态（版本号、各维度统计、lastChangedAt） |
| GET | `/api/open/v1/sync/changes` | 增量变更日志 |
| GET | `/api/open/v1/tracks` | 曲目列表（分页、多维过滤、排序） |
| GET | `/api/open/v1/tracks/{id}` | 曲目详情 |
| GET | `/api/open/v1/tracks/{id}/lyrics` | 歌词原文 |
| GET | `/api/open/v1/tracks/{id}/lyrics/meta` | 歌词元数据（含 available、format、hash、etag） |
| GET | `/api/open/v1/tracks/{id}/artwork` | 封面图片（二进制流） |
| GET | `/api/open/v1/tracks/{id}/artwork/meta` | 封面元数据（含 available、mimeType、width、height、fileSize、hash、etag，不含 fileName） |
| GET | `/api/open/v1/artists` | 歌手列表（全量，无分页） |
| GET | `/api/open/v1/artists/{artistName}/tracks` | 歌手曲目（分页） |
| GET | `/api/open/v1/albums` | 专辑列表（全量，无分页） |
| GET | `/api/open/v1/albums/tracks` | 专辑曲目（分页，参数为 album/artist） |
| GET | `/api/open/v1/match/track` | 本地音乐匹配（GET，title 必填） |

## 错误响应

所有接口错误响应格式统一：

```json
{
  "code": "OPENAPI_TRACK_NOT_FOUND",
  "message": "Track not found",
  "traceId": "a1b2c3d4-e5f6-...",
  "details": {}
}
```

`traceId` 用于问题追踪。常见状态码：

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误（如分页参数超范围） |
| 401 | 未提供或无效的 AK/SK + HMAC 签名，或凭证被禁用 / 过期 |
| 404 | 资源不存在（如曲目无歌词/封面） |
| 429 | 请求超出限流阈值 |
| 500 | 服务器内部错误 |
