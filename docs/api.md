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
  "error": "Bad Request",
  "message": "title is required"
}
```

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "error": "Unauthorized",
  "message": "Invalid or missing token"
}
```

```http
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "error": "Not Found",
  "message": "Track not found"
}
```

### 扫描任务

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/scan-jobs` | 扫描任务列表 |
| POST | `/api/scan-jobs` | 创建 pending 状态扫描任务 |

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

### 扫描任务

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/scan-jobs/{id}` | 扫描任务详情 |

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

分页、搜索、排序参数为规划中能力，当前已实现列表接口暂未提供统一分页。

| 参数 | 说明 |
|------|------|
| `page` | 页码（从 1 开始，默认 1） |
| `size` | 每页条数（默认 20，最大 100） |
| `q` | 关键词搜索 |
| `sort` | 排序字段 |
| `order` | 升序/降序（asc/desc） |

## 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未鉴权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
