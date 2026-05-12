# API 文档

## 基础信息

- **前缀**: `/api`
- **鉴权**: `Authorization: Bearer <token>`
- **响应格式**: JSON
- **错误格式**: `{ "error": "..." }`

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
| GET | `/api/tracks/{id}` | 曲目详情 |
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
