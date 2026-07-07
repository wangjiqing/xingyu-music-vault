# API 文档

## 基础信息

- **前缀**: `/api`
- **鉴权**: 管理端 Session Cookie；OpenAPI AK/SK + HMAC-SHA256
- **响应格式**: JSON
- **错误格式**: `{ "error": "...", "message": "..." }`

`/api/health`、管理端认证接口、静态资源与 `/api/open/v1/*` OpenAPI 接口按各自规则开放。其他管理端 `/api/*` 接口需要管理员登录 Session。v1.1.3 起 OpenAPI 使用独立 AK/SK + HMAC-SHA256，不接受旧静态 Token，也不与管理端 Session 混用。

## 已实现接口

### 健康检查

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/health` | 服务健康状态 |

### 管理端认证（v1.1.2）

当前版本只支持单管理员账号，不支持开放注册、找回密码、OAuth2 / OIDC、NAS 第三方登录或细粒度 RBAC。

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/admin/auth/setup-status` | 查询是否已初始化管理员账号 |
| POST | `/api/admin/auth/setup` | 初始化第一个管理员账号，仅未初始化时可用 |
| POST | `/api/admin/auth/login` | 管理员登录，成功后下发 HttpOnly Session Cookie |
| POST | `/api/admin/auth/logout` | 登出并失效当前 Session |
| GET | `/api/admin/auth/me` | 查询当前登录用户，未登录返回 401 |

#### 查询初始化状态

```http
GET /api/admin/auth/setup-status
```

```json
{
  "initialized": false
}
```

#### 初始化管理员账号

```http
POST /api/admin/auth/setup
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin-password"
}
```

仅当数据库中不存在管理员账号时允许调用。密码至少 8 位，服务端仅保存 PBKDF2 哈希值。创建成功后不会自动登录，前端应跳转登录流程。

#### 登录 / 登出 / 当前用户

```http
POST /api/admin/auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin-password"
}
```

登录成功返回当前用户基础信息，并通过 `XINGYU_MUSIC_VAULT_SESSION` 下发 HttpOnly Cookie：

```json
{
  "id": 1,
  "username": "admin",
  "role": "ADMIN"
}
```

响应不会包含 `passwordHash`。登录失败返回通用 401，避免泄露账号或密码细节。

```http
GET /api/admin/auth/me
POST /api/admin/auth/logout
```

未登录或 Session 失效时，受保护管理接口返回：

```json
{
  "error": "unauthorized",
  "message": "未登录或登录已过期"
}
```

### 歌词草稿提取与手工草稿（v1.3.0 / v1.3.2）

歌词草稿提取任务用于“音频 → 未对齐候选歌词文本 → 人工校对 → 确认为可信歌词资产”。它不会自动把 ASR 草稿当作可信歌词，不会自动创建逐字对齐任务，也不会替换当前生效 LRC / SWLRC。

v1.3.2 起，管理员也可以跳过 Worker 草稿提取，直接粘贴歌词文本创建手工草稿。手工草稿使用 `LYRIC_DRAFT_MANUAL` 任务类型和 `MANUAL_PASTE` 来源语义，不会伪装为 Worker 提取结果；确认后仍生成 `DRAFT_CONFIRMED` 来源可信歌词资产，并继续复用既有逐字对齐任务链路。

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/admin/music/{musicId}/lyric-draft-jobs` | 创建 `LYRIC_DRAFT_EXTRACTION` 草稿提取任务，写入 v2 `request.json` 并最后创建 `READY` |
| POST | `/api/admin/music/{musicId}/lyric-drafts/manual` | 创建 `LYRIC_DRAFT_MANUAL` 手工草稿，不创建 Worker READY 信号 |
| GET | `/api/admin/lyric-draft-jobs/{jobId}/draft` | 获取草稿原始文本快照、可编辑文本、状态和报告摘要 |
| PUT | `/api/admin/lyric-draft-jobs/{jobId}/draft` | 保存人工校对后的草稿文本 |
| POST | `/api/admin/lyric-draft-jobs/{jobId}/confirm` | 确认草稿，生成 `DRAFT_CONFIRMED` 来源可信歌词资产 |
| POST | `/api/admin/lyric-draft-jobs/{jobId}/reject` | 驳回草稿，必须填写原因 |
| POST | `/api/admin/lyric-draft-jobs/{jobId}/sources` | 关联候选来源元信息到当前草稿 |
| GET | `/api/admin/lyric-draft-jobs/{jobId}/artifacts/cleaned` | 读取 Worker 输出的 `transcript.cleaned.txt` |
| GET | `/api/admin/lyric-draft-jobs/{jobId}/artifacts/raw` | 读取 Worker 输出的 `transcript.raw.txt` |
| GET | `/api/admin/lyric-draft-jobs/{jobId}/artifacts/segments` | 读取 Worker 输出的 `transcript.segments.json` |
| GET | `/api/admin/lyric-draft-jobs/{jobId}/artifacts/report` | 读取 Worker 输出的 `report.json` |

创建请求：

```json
{
  "language": "zh",
  "asrModel": "medium",
  "skipSeparation": false,
  "vadFilter": true,
  "conditionOnPreviousText": false,
  "keepSuspectedMetadata": false,
  "retainIntermediate": false,
  "createdBy": "admin"
}
```

音库写入的 v2 `request.json`：

```json
{
  "schemaVersion": 2,
  "jobId": "uuid",
  "taskType": "LYRIC_DRAFT_EXTRACTION",
  "audioPath": "/music/Artist/Song.flac",
  "outputDir": "/jobs/{jobId}/result",
  "language": "zh",
  "device": "cpu",
  "asrModel": "medium",
  "skipSeparation": false,
  "vadFilter": true,
  "conditionOnPreviousText": false,
  "keepSuspectedMetadata": false,
  "retainIntermediate": false,
  "createdAt": "2026-07-04T17:00:00"
}
```

Worker 成功后音库读取 `result/transcript.cleaned.txt` 并保存为 `lyric_drafts.originalText` 原始快照，同时初始化 `editableText`。草稿状态为 `PENDING_REVIEW`、`EDITING`、`CONFIRMED`、`REJECTED`。确认后创建 `lyrics.sourceType=DRAFT_CONFIRMED` 的可信歌词资产，保存 `sourceTaskId`、`sourceDraftId`、`sourceTextHash`、`confirmedAt`、`confirmedBy`；不会绑定为当前主歌词。之后创建逐字对齐任务时，可通过 `sourceLyricsAssetId` 明确选择这份可信歌词资产。

#### 手工创建草稿

```http
POST /api/admin/music/1/lyric-drafts/manual
Content-Type: application/json
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

```json
{
  "text": "[00:01.00]第一句歌词\n[00:05.00]第二句歌词",
  "createdBy": "admin"
}
```

响应为 `LyricDraftResponse`，核心字段示例：

```json
{
  "jobId": "uuid",
  "musicId": 1,
  "executionStatus": "COMPLETED",
  "draftStatus": "EDITING",
  "originalText": "[00:01.00]第一句歌词\n[00:05.00]第二句歌词",
  "editableText": "[00:01.00]第一句歌词\n[00:05.00]第二句歌词",
  "sourceType": "MANUAL_PASTE",
  "sourceMetadata": {
    "sourceType": "MANUAL_PASTE"
  },
  "sources": []
}
```

服务端会清理 UTF-8 BOM、统一换行并去除首尾空白；不会改写、翻译、补全或自动修复歌词内容。空文本、超过 `ALIGNMENT_DRAFT_MAX_TEXT_BYTES` 的文本或非法状态会返回 `400 bad_request`。手工草稿不生成 Worker `READY` 文件，也不会被状态同步器当作 Worker 提取任务处理。

### 歌词覆盖率与推荐（v1.3.3）

歌词状态由后端统一计算，前端不自行组合判断。状态值：

| 状态 | 含义 |
|------|------|
| `SWLRC_READY` | 已有有效 SWLRC |
| `LRC_READY` | 仅有有效 LRC |
| `NO_LYRICS` | 无有效歌词 |
| `ALIGNMENT_RUNNING` | 当前存在运行中的歌词任务 |
| `DRAFT_PENDING` | 当前存在待人工确认的歌词草稿 |
| `FAILED` | 最近歌词制作任务失败，可重新发起 |

优先级：制作中 > 待确认 > 已有 SWLRC > 仅有 LRC > 无歌词 > 制作异常。有效 LRC / SWLRC 会校验当前绑定和文件可读性；历史数据库记录存在但源文件缺失时不作为已有歌词。

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/admin/lyrics/overview` | 首页歌词资产进度 |
| GET | `/api/admin/lyrics/recommendations/daily` | 获取当天可见稳定推荐 |
| POST | `/api/admin/lyrics/recommendations/{id}/start` | 标记开始制作并由前端跳转工作台 |
| POST | `/api/admin/lyrics/recommendations/{id}/skip` | 今天跳过，不补位 |
| POST | `/api/admin/lyrics/recommendations/{id}/replace` | 换一首，保留原推荐并持久化补位 |
| POST | `/api/admin/lyrics/recommendations/random` | 随机挑选待制作歌曲，`count` 为 1-20 |

概览响应：

```json
{
  "totalSongs": 1238,
  "songsWithLyrics": 860,
  "lyricsCoverageRate": 0.6947,
  "songsWithSwlrc": 412,
  "swlrcCoverageRate": 0.3328,
  "songsWithLrcOnly": 448,
  "songsWithoutLyrics": 378,
  "alignmentRunning": 3,
  "draftPending": 8
}
```

随机候选请求：

```json
{ "count": 5 }
```

每日推荐和随机候选只面向当前缺少有效 SWLRC、且不处于制作中或待确认的歌曲。它们不会自动创建草稿任务、对齐任务或批量制作歌词。

`GET /api/music` 响应新增字段：

```json
{
  "lyricStatus": "LRC_READY",
  "hasLrc": true,
  "hasSwlrc": false
}
```

`GET /api/music` 新增 `lyricStatus` 筛选参数，支持 `MISSING_SWLRC`、`SWLRC_READY`、`LRC_READY`、`NO_LYRICS`、`ALIGNMENT_RUNNING`、`DRAFT_PENDING`、`FAILED`。其中 `MISSING_SWLRC` 等价于 `LRC_READY + NO_LYRICS`。

#### 关联草稿来源

```http
POST /api/admin/lyric-draft-jobs/{jobId}/sources
Content-Type: application/json
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

```json
{
  "provider": "BRAVE_SEARCH",
  "query": "歌手 歌名 歌词",
  "title": "候选来源标题",
  "url": "<candidate-source-url>",
  "domain": "<candidate-source-domain>",
  "selectedBy": "admin"
}
```

响应：

```json
{
  "id": 1,
  "provider": "BRAVE_SEARCH",
  "query": "歌手 歌名 歌词",
  "title": "候选来源标题",
  "url": "<candidate-source-url>",
  "domain": "<candidate-source-domain>",
  "selectedBy": "admin",
  "selectedAt": "2026-07-05T14:00:00"
}
```

来源关联只保存候选来源元信息，不写入歌词正文。同一草稿和 URL 重复关联时返回既有记录，避免重复来源行。关联 Brave 来源后草稿 `sourceType` 可变为 `BRAVE_ASSISTED`，表示该草稿整理过程参考过候选来源；这仍不代表系统抓取了来源网页正文。

### Brave Search 管理接口（v1.3.2）

Brave Search 接口仅供管理员使用，统一位于 `/api/admin/brave-search`，需要管理端 Session Cookie。API Key 不会在任何响应中返回；错误响应也不应包含完整 Key、密文或内部加密材料。

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/admin/brave-search/status` | 查询 Brave Search 当前配置状态 |
| POST | `/api/admin/brave-search/key` | 保存或替换控制台托管 Brave API Key |
| PATCH | `/api/admin/brave-search/enabled` | 启用或暂停控制台托管 Brave Search |
| POST | `/api/admin/brave-search/test` | 使用当前有效配置测试连接 |
| POST | `/api/admin/brave-search/search` | 后端代理调用 Brave Search，返回候选来源结果 |

配置优先级：

1. `MUSIC_VAULT_BRAVE_SEARCH_API_KEY` 环境变量存在时为 `ENV` 模式，实际搜索使用环境变量 Key。
2. 未配置环境变量时，可使用控制台托管 Key；托管 Key 需要 `MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY` 支持服务端加密保存。
3. 两种方式同时存在时，环境变量优先；控制台保存、启用或暂停不会改变 ENV 模式的实际搜索行为。

#### 查询状态

```http
GET /api/admin/brave-search/status
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

```json
{
  "configured": true,
  "enabled": true,
  "searchable": true,
  "mode": "ENV",
  "message": "Brave Search is configured by environment variable",
  "encryptionAvailable": true,
  "updatedAt": null,
  "lastCheckedAt": null,
  "lastError": null
}
```

`mode` 可能为 `ENV`、`MANAGED`、`NONE` 等服务端状态语义。前端应以 `searchable` 判断搜索按钮是否可用，以 `message` / `lastError` 展示可读提示；不要依赖或展示任何 Key 内容。

#### 保存或替换控制台托管 Key

```http
POST /api/admin/brave-search/key
Content-Type: application/json
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

```json
{
  "apiKey": "不要在文档、Compose 或 Git 中写真实 Key",
  "updatedBy": "admin"
}
```

响应为状态对象，不返回 `apiKey`。未配置 `MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY` 时返回 `400 bad_request`，因为控制台托管 Key 不能明文保存。ENV 模式下保存请求会被拒绝，提示环境变量配置已接管。

#### 启用 / 暂停

```http
PATCH /api/admin/brave-search/enabled
Content-Type: application/json
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

```json
{
  "enabled": false,
  "updatedBy": "admin"
}
```

该接口只作用于控制台托管配置。ENV 模式下启用 / 暂停请求会被拒绝，避免制造“暂停已生效但实际仍使用环境变量 Key”的误解。

#### 测试连接

```http
POST /api/admin/brave-search/test
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

服务端使用当前有效 Key 发起测试查询，并更新状态中的 `lastCheckedAt` / `lastError`。鉴权失败、限流、超时和上游错误会映射为受控错误，不泄露 Key。

#### 搜索候选来源

```http
POST /api/admin/brave-search/search
Content-Type: application/json
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

```json
{
  "query": "歌手 歌名 歌词",
  "count": 5
}
```

响应：

```json
{
  "query": "歌手 歌名 歌词",
  "results": [
    {
      "title": "候选来源标题",
      "url": "<candidate-source-url>",
      "domain": "<candidate-source-domain>",
      "description": "搜索结果摘要"
    }
  ]
}
```

搜索接口只返回 Brave Search API 的候选结果摘要，不抓取结果页面，不返回第三方网页全文，不缓存第三方歌词正文，也不会把 `description` 写入草稿正文。查询长度、结果数量、超时、401/403 鉴权失败、429 限流和 5xx 上游异常均由服务端限制和映射。

### 歌词对齐任务（v1.3.0）

歌词对齐任务接口仅供管理员使用。音库只写入共享任务目录、读取 Worker 状态与结果，并在管理员人工审核后确认导入；不会自动覆盖原始可信歌词，也不会在音库容器内安装或调用 Python、WhisperX、PyTorch、Docker Socket、HTTP 回调或消息队列。

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/lyric-alignment/jobs` | 创建对齐任务，写入 `request.json`、`trusted-lyrics.txt`、可选 `sections.json`，最后创建 `READY` |
| GET | `/api/lyric-alignment/jobs` | 查询任务列表；不返回完整可信歌词或完整结果文件 |
| GET | `/api/lyric-alignment/jobs/{id}` | 查询任务详情 |
| GET | `/api/lyric-alignment/jobs/{id}/artifacts/lrc` | 读取 Worker 生成的 LRC 结果 |
| GET | `/api/lyric-alignment/jobs/{id}/artifacts/swlrc` | 读取 Worker 生成的 SWLRC 结果 |
| GET | `/api/lyric-alignment/jobs/{id}/artifacts/report` | 读取 Worker 生成的 report.json |
| GET | `/api/lyric-alignment/jobs/{id}/artifacts/alignment` | 读取 Worker 生成的 alignment.json |
| POST | `/api/admin/lyric-alignment/jobs/{id}/approve` | 管理员审核通过 |
| POST | `/api/admin/lyric-alignment/jobs/{id}/reject` | 管理员审核驳回 |
| POST | `/api/admin/lyric-alignment/jobs/{id}/import` | 将审核通过的 LRC / SWLRC 确认导入为受控歌词资产 |

`/api/admin/lyric-alignment/jobs/**` 是管理端规范路径；`/api/lyric-alignment/jobs/**` 保留任务创建、查询和 artifact 读取兼容入口，仍受管理员鉴权保护。

创建对齐任务时可选传入 `sourceLyricsAssetId`，用于选择当前歌曲关联的歌词资产或同歌曲草稿确认生成的 `DRAFT_CONFIRMED` 可信歌词资产。未传时仍使用当前歌曲主绑定歌词。

#### 审核

```http
POST /api/admin/lyric-alignment/jobs/{jobId}/approve
Content-Type: application/json
```

```json
{
  "reviewNote": "副歌和主歌位置可接受，允许导入。",
  "reviewedBy": "admin"
}
```

仅 `status=COMPLETED` 且 `reviewStatus=PENDING` 的任务可审核。通过后 `reviewStatus=APPROVED`，驳回后 `reviewStatus=REJECTED`，并记录 `reviewedBy`、`reviewedAt`、`reviewNote`。重复审核、未完成任务、`FAILED` 或 `ABANDONED` 任务返回 `409 conflict`。Worker 的 `NEEDS_REVIEW` 只表示执行结论，不能替代人工审核状态。

#### 确认导入

```http
POST /api/admin/lyric-alignment/jobs/{jobId}/import
Content-Type: application/json
```

```json
{
  "importedBy": "admin"
}
```

导入前校验任务已完成、已审核通过、结果目录存在、`lyrics.lrc` 与 `lyrics.swlrc` 均存在、当前文件 hash 与同步时记录一致、关联歌曲存在、`music-vault.alignment-lyrics-root` 已配置且属于歌词扫描根目录。导入会把结果发布到 `{alignmentLyricsRoot}/alignment/{songId}/{jobId}`，创建或复用 `sourceType=ALIGNMENT` 的 `lyrics` 记录，保存 `sourceTaskId`、`parentLyricsId`、`sourcePath`、`swlrcPath`、`contentHash`、`swlrcHash`、`confirmedAt`、`confirmedBy`，并将新 LRC 绑定为当前歌曲主歌词。该目录已经脱离 Worker jobs 中间产物，普通歌词扫描会排除受控 alignment 子目录。

已导入任务再次导入会幂等返回同一个 `importedLyricId`，不会重复生成资产或破坏绑定。导入失败时任务 `importStatus=IMPORT_FAILED` 并记录 `importErrorMessage`；原始可信歌词和既有正式歌词资产保持不变。

响应：

```json
{
  "jobId": "f5283726-42c4-4915-8df5-32e8df94d79a",
  "songId": 1,
  "lyricId": 10,
  "importedLyricId": 11,
  "importStatus": "IMPORTED",
  "lrcHash": "4a1b...",
  "swlrcHash": "5b2c...",
  "importedAt": "2026-07-03T21:57:39.979",
  "importedBy": "admin"
}
```

#### 任务状态

执行状态：`CREATING`、`QUEUED`、`RUNNING`、`COMPLETED`、`FAILED`、`ABANDONED`。

审核状态：`NOT_AVAILABLE`、`PENDING`、`APPROVED`、`REJECTED`。

导入状态：`NOT_IMPORTED`、`IMPORTED`、`IMPORT_FAILED`。

每次审核通过、审核驳回、导入成功、导入失败都会写入 `lyric_alignment_job_events`，包含任务、歌曲、动作、操作者、状态变化、备注或错误信息。

### 歌曲工作台（v1.2.1）

歌曲工作台接口仅供管理端登录后使用，面向本地 / 局域网可信整理场景。接口只读，不修改元数据、歌词、封面或音频文件，不影响 `/api/open/v1/**` 的 AK/SK + HMAC 认证策略。

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/admin/music/{id}/workbench` | 获取工作台聚合数据：数据库歌曲信息、歌词、封面、OpenAPI 输出预览 |
| GET | `/api/admin/music/{id}/openapi-preview` | 获取当前歌曲面向客户端的 OpenAPI 输出预览 JSON |
| GET | `/api/admin/music/{id}/audio` | 管理端受保护音频播放接口，按 music id 读取已入库本地文件 |

#### 工作台聚合数据

```http
GET /api/admin/music/1/workbench
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

响应结构：

```json
{
  "music": { "...": "数据库歌曲字段，与 /api/music 列表项保持一致" },
  "lyrics": {
    "available": true,
    "lyricId": 10,
    "format": "lrc",
    "content": "[00:00.00]...",
    "updatedAt": "2026-06-14T20:00:00"
  },
  "wordLyrics": {
    "available": true,
    "format": "swlrc",
    "content": "[swlrc:1]\n[00:03.300,00:04.100]\n<00:03.300,00:03.520>你",
    "contentHash": "sha256...",
    "updatedAt": "2026-07-05T14:00:00"
  },
  "artwork": {
    "available": true,
    "artworkId": 20,
    "mimeType": "image/jpeg",
    "fileName": "cover.jpg",
    "previewUrl": "/api/artworks/20/file",
    "updatedAt": "2026-06-14T20:00:00"
  },
  "openApiPreview": {
    "track": { "...": "OpenAPI OpenTrackResponse" },
    "lyrics": { "...": "OpenAPI lyrics meta" },
    "artwork": { "...": "OpenAPI artwork meta" },
    "resourceUrls": {
      "track": "/api/open/v1/tracks/1",
      "lyricsMeta": "/api/open/v1/tracks/1/lyrics/meta",
      "artworkMeta": "/api/open/v1/tracks/1/artwork/meta"
    }
  }
}
```

无歌词或无封面时，`available` 为 `false`，正文或预览 URL 为空。`wordLyrics` 为管理端工作台逐字试听字段：当当前歌词记录有可读 SWLRC 时返回 `available=true` 和 SWLRC 内容；文件缺失、超出大小限制、读取失败或不可用时返回 `available=false`。前端会在 SWLRC 解析失败时回退使用 `lyrics` 中的 LRC 行级歌词。

#### OpenAPI 输出预览

```http
GET /api/admin/music/1/openapi-preview
Cookie: XINGYU_MUSIC_VAULT_SESSION=...
```

该接口返回的内容等同于工作台聚合响应中的 `openApiPreview` 字段，便于管理端单独调试当前歌曲面向客户端的输出结构。接口使用管理端登录态，不需要 AK/SK 签名；公开 OpenAPI 认证策略不受影响。

```json
{
  "track": {
    "...": "OpenAPI OpenTrackResponse"
  },
  "lyrics": {
    "...": "OpenAPI lyrics meta"
  },
  "artwork": {
    "...": "OpenAPI artwork meta"
  },
  "resourceUrls": {
    "track": "/api/open/v1/tracks/1",
    "lyricsMeta": "/api/open/v1/tracks/1/lyrics/meta",
    "lyrics": "/api/open/v1/tracks/1/lyrics",
    "artworkMeta": "/api/open/v1/tracks/1/artwork/meta",
    "artwork": "/api/open/v1/tracks/1/artwork"
  }
}
```

#### 管理端音频播放

```http
GET /api/admin/music/1/audio
Range: bytes=0-
```

音频接口必须携带管理端登录态。服务端只根据数据库中的 music id 定位文件，不接受任意路径参数；文件真实路径必须位于 `music-vault.music-dirs` 配置目录内。文件不存在、不可读、越界或歌曲不存在时返回 404。支持 `Range` / `Content-Range` / `Accept-Ranges: bytes`，便于浏览器 audio 拖动进度。

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

### 歌手（v0.8.1 / v0.8.2）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/music/artists` | 歌手聚合列表（支持搜索、排序、分页） |
| GET | `/api/music/artists/{artistKey}` | 歌手详情（含统计概览和专辑分组）（v0.8.2） |

#### 歌手列表

```http
GET /api/music/artists?page=1&pageSize=20&keyword=周杰伦&sort=trackCountDesc
Authorization: Bearer change-me
```

参数：

| 参数 | 说明 |
|------|------|
| `page` | 页码，从 1 开始，默认 1 |
| `pageSize` | 每页条数，默认 20，最大 100 |
| `keyword` | 按歌手名模糊搜索（可选） |
| `sort` | 排序方式：`trackCountDesc`（歌曲数降序，默认）、`nameAsc`（名称升序）、`albumCountDesc`（专辑数降序）、`metadataIncompleteDesc`（待整理数量降序） |

响应示例：

```json
{
  "items": [
    {
      "artist": "周杰伦",
      "artistKey": "%E5%91%A8%E6%9D%B0%E4%BC%A6",
      "trackCount": 15,
      "albumCount": 3,
      "lyricsCount": 14,
      "artworkCount": 12,
      "metadataIncompleteCount": 1
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

`artist` 为歌手名称，`artistKey` 为 URL-safe 编码标识，`metadataIncompleteCount` 为该歌手歌曲中元数据不完整的数量。

#### curl 验证

```bash
curl "http://localhost:8080/api/music/artists?page=1&pageSize=20" \
  -H "Authorization: Bearer change-me"
```

#### 歌手详情

```http
GET /api/music/artists/%E5%91%A8%E6%9D%B0%E4%BC%A6
Authorization: Bearer change-me
```

路径参数 `artistKey` 为歌手名的 URL-safe 编码标识，规则为：`trim` → `lowerCase(Locale.ROOT)` → `URLEncoder.encode(..., UTF-8)`。例如：

- 周杰伦 → `%E5%91%A8%E6%9D%B0%E4%BC%A6`
- AC/DC → `ac%2Fdc`
- Aimer feat. chelly → `aimer+feat.+chelly`
- 空歌手（Unknown）→ `__unknown__`

歌手不存在时返回 `404`。

响应示例：

```json
{
  "artist": "周杰伦",
  "artistKey": "%E5%91%A8%E6%9D%B0%E4%BC%A6",
  "trackCount": 15,
  "albumCount": 3,
  "lyricsCount": 14,
  "artworkCount": 12,
  "metadataIncompleteCount": 1,
  "albums": [
    {
      "album": "叶惠美",
      "albumKey": "%E5%8F%B6%E6%83%A0%E7%BE%8E",
      "year": 2003,
      "trackCount": 5,
      "lyricsCount": 5,
      "artworkCount": 4,
      "metadataIncompleteCount": 0,
      "coverMusicId": 123,
      "sampleMusicId": 45
    }
  ]
}
```

`artist` 为歌手原名，`artistKey` 为 URL-safe 编码标识，
`metadataIncompleteCount` 为该歌手歌曲中元数据不完整的数量。
`albums` 为该歌手的专辑分组列表，按专辑名升序排列，
每项含年份（取分组内第一条歌曲的年份，为空则 null）、曲目数、歌词数、封面数、
待整理数量、`coverMusicId`（专辑封面歌曲 ID）、`sampleMusicId`（示例歌曲 ID）。

### 专辑（v0.8.3）

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/music/albums` | 专辑聚合列表（支持搜索、排序、分页，可按歌手过滤） |
| GET | `/api/music/albums/detail` | 专辑详情（含统计概览和曲目列表）（v0.8.3） |

#### 专辑列表

```http
GET /api/music/albums?page=1&pageSize=20&keyword=叶惠美&artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6&sort=trackCountDesc
Authorization: Bearer change-me
```

参数：

| 参数 | 说明 |
|------|------|
| `page` | 页码，从 1 开始，默认 1 |
| `pageSize` | 每页条数，默认 20，最大 100 |
| `keyword` | 按专辑名或专辑歌手名模糊搜索（可选） |
| `artistKey` | 按歌手过滤，值为歌手名的 URL-safe 编码（可选） |
| `sort` | 排序方式：`trackCountDesc`（歌曲数降序，默认）、`nameAsc`（名称升序）、`yearDesc`（年份降序）、`metadataIncompleteDesc`（待整理数量降序） |

响应示例：

```json
{
  "items": [
    {
      "album": "叶惠美",
      "albumKey": "%E5%8F%B6%E6%83%A0%E7%BE%8E",
      "albumArtist": "周杰伦",
      "artistKey": "%E5%91%A8%E6%9D%B0%E4%BC%A6",
      "year": 2003,
      "trackCount": 5,
      "lyricsCount": 5,
      "artworkCount": 4,
      "metadataIncompleteCount": 0,
      "coverMusicId": 123
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

`album` 为专辑名，`albumKey` 为 URL-safe 编码标识，
`artistKey` 为专辑歌手的编码标识。
`year` 为专辑内曲目最早年份（即 min(year)），可能为 null（分组内所有曲目均无年份）。
`coverMusicId` 为专辑封面歌曲 ID，优先取分组内第一首有主封面的歌曲 id；
若整张专辑没有任何主封面，则兜底取分组内第一首歌曲 id（即 `sampleMusicId`）。

`albumKey` 生成规则：`trim(album)` → `lowerCase(Locale.ROOT)` → `URLEncoder.encode(..., UTF-8)`；`artistKey` 生成规则同歌手 `artistKey`。

#### 专辑详情

```http
GET /api/music/albums/detail?albumKey=%E5%8F%B6%E6%83%A0%E7%BE%8E&artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6
Authorization: Bearer change-me
```

路径参数：

| 参数 | 说明 |
|------|------|
| `albumKey` | 专辑名的 URL-safe 编码（必填） |
| `artistKey` | 专辑歌手的 URL-safe 编码（必填，用于唯一定位同一专辑名下的多歌手情况） |

专辑不存在时返回 `404`。

响应示例：

```json
{
  "album": "叶惠美",
  "albumKey": "%E5%8F%B6%E6%83%A0%E7%BE%8E",
  "albumArtist": "周杰伦",
  "artistKey": "%E5%91%A8%E6%9D%B0%E4%BC%A6",
  "year": 2003,
  "trackCount": 5,
  "lyricsCount": 5,
  "artworkCount": 4,
  "metadataIncompleteCount": 0,
  "coverMusicId": 123
}
```

专辑详情接口只返回统计概览。专辑详情页另通过 `GET /api/music?albumKey=...&artistKey=...` 获取该专辑的曲目列表。

#### 歌曲列表按 albumKey + artistKey 过滤

`GET /api/music` 支持 `albumKey` 和 `artistKey` 查询参数，可组合使用以精确定位某张专辑的曲目：

```http
GET /api/music?albumKey=%E5%8F%B6%E6%83%A0%E7%BE%8E&artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6
Authorization: Bearer change-me
```

### 扫描任务

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/scan-jobs?page=0&size=20&status=completed` | 扫描任务分页列表，可按状态过滤 |
| POST | `/api/scan-jobs` | 创建 pending 状态扫描任务 |
| GET | `/api/scan-jobs/{id}` | 扫描任务详情 |
| POST | `/api/scan-jobs/{id}/run` | 执行扫描任务 |

v0.2 扫描只做本地文件发现和 `track_files` 记录。
它不会提取音频内嵌元数据，不会抓歌词，不会抓封面，
不会访问 MusicBrainz、LRCLIB 或其他外部网络服务，也不会做音频指纹。

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

扫描支持扩展名：`mp3`、`flac`、`wav`、`m4a`、`aac`、`ogg`、`opus`。
重复扫描相同 `file_path` 不会重复插入；
当 `file_size` 和 `last_modified_at` 未变化时会跳过，
变化时会更新 `file_size`、`last_modified_at`、`scan_job_id`、兜底元数据与 `updated_at`。
只有 `pending` 和 `failed` 任务可以执行；`running` 或 `completed` 任务再次执行会返回 `409`：

```json
{
  "error": "conflict",
  "message": "Completed scan job cannot be run again"
}
```

扫描目录必须位于配置允许的 `MUSIC_VAULT_MUSIC_DIRS` 范围内，
并会经过真实路径归一化校验。
路径穿越、非允许目录、根目录类危险路径、目录不存在、不可读或不是目录
都会使任务进入 `failed`，并记录 `errorMessage`。

### 音乐库

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/music/stats` | 音乐库统计（总数、元数据不完整数、歌词就绪数、封面就绪数、回收站数） |
| POST | `/api/music/scan` | 接受一次后台本地音乐扫描任务，默认扫描 `music.scan.default-path` |
| GET | `/api/music?page=0&size=20` | 音乐分页列表 |
| GET | `/api/music/{id}` | 音乐详情 |
| GET | `/api/music/{id}/file` | 音乐文件信息和安全删除状态 |
| GET | `/api/music/trash` | 回收站音乐列表 |
| DELETE | `/api/music/{id}` | 安全删除音乐文件，移动到音乐库根目录下的 `.music-vault-trash` |
| POST | `/api/music/{id}/restore` | 从回收站恢复音乐文件 |
| DELETE | `/api/music/{id}/trash` | 彻底删除回收站中的音乐文件 |
| PUT | `/api/music/{musicId}/artwork` | 绑定音乐主封面 |
| POST | `/api/music/{musicId}/artwork/import` | 上传本地图片、入库并绑定为音乐主封面 |
| DELETE | `/api/music/{musicId}/artwork` | 取消音乐主封面绑定 |
| PUT | `/api/music/metadata/batch` | 批量更新多个音乐的共同元数据字段（v0.7.4） |

`POST /api/music/scan` 可传空对象使用默认目录，也可传入 `path` 覆盖本次扫描目录：

```http
POST /api/music/scan
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "path": "/path/to/music"
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

统计：

```http
GET /api/music/stats
Authorization: Bearer change-me
```

```json
{
  "total": 529,
  "metadataIncomplete": 42,
  "lyricsReady": 487,
  "artworkReady": 510,
  "trashed": 3
}
```

所有字段均为整数，其中 `trashed` 为当前处于 `trashed` 状态的记录数量。

扫描会递归读取目录，跳过隐藏文件、非音乐文件和音乐库根目录下的
`.music-vault-trash` 回收目录。当前不引入音频标签解析依赖，
元数据采用文件名兜底：`周杰伦 - 晴天.flac` 会解析为
`artist = 周杰伦`、`title = 晴天`；
无法解析时 `artist = Unknown`、`title = 文件名去后缀`。
如果历史 `track_files` 行缺少 `track_id`，
重复音乐扫描会补建 `tracks` 元数据，供歌词匹配使用。

查询列表：

```http
GET /api/music?page=0&size=20&keyword=周杰伦&lyricStatus=MISSING_SWLRC&hasArtwork=true&metadata=incomplete
Authorization: Bearer change-me
```

| 参数 | 说明 |
|------|------|
| `page` | 页码，从 0 开始，默认 0 |
| `size` | 每页条数，默认 20，最大 100 |
| `keyword` | 模糊匹配文件名、标题、艺术家、专辑（可选） |
| `lyricStatus` | 按统一歌词状态过滤，允许值：`MISSING_SWLRC`、`SWLRC_READY`、`LRC_READY`、`NO_LYRICS`、`ALIGNMENT_RUNNING`、`DRAFT_PENDING`、`FAILED`（可选） |
| `hasLyrics` | 兼容旧版的歌词绑定过滤参数；管理端 v1.3.3 起优先使用 `lyricStatus` |
| `hasArtwork` | 按封面绑定状态过滤，`true` 为已有封面（可选） |
| `metadata` | 按元数据状态过滤，允许值：`complete`、`incomplete`（可选） |

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
      "lyricStatus": "LRC_READY",
      "lyricId": 1,
      "hasLrc": true,
      "hasSwlrc": false,
      "artworkStatus": "BOUND",
      "artworkId": 1,
      "artworkPreviewUrl": "/api/artworks/1/file",
      "artworkFileName": "周杰伦 - 晴天.png",
      "artworkFileExists": true,
      "filePath": "/path/to/music/周杰伦 - 晴天.flac",
      "fileName": "周杰伦 - 晴天.flac",
      "fileExtension": "flac",
      "fileSize": 123456,
      "lastModifiedTime": "2026-05-14T06:40:00",
      "deletedAt": null,
      "trashPath": null,
      "originalPath": null,
      "deleteStatus": "active",
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

查询文件信息：

```http
GET /api/music/1/file
Authorization: Bearer change-me
```

响应包含 `filePath`、`fileName`、`fileExtension`、`fileSize`、`lastModifiedTime`、`deletedAt`、`trashPath`、`originalPath`、`deleteStatus` 等字段。

安全删除：

```http
DELETE /api/music/1
Authorization: Bearer change-me
```

安全删除不会彻底物理删除文件，也不会删除歌词、封面或其他关联资源。
后端会校验原文件真实路径位于配置的音乐库根目录内，且必须是普通文件；
已经位于 `.music-vault-trash` 下的文件会被拒绝。
删除成功后，文件会移动到匹配音乐库根目录下：

```text
{musicRoot}/.music-vault-trash/{musicId}/{originalFileName}
```

如果目标文件名冲突，会自动生成唯一文件名。
数据库会记录 `deletedAt`、`trashPath`、`originalPath`、`deleteStatus = "trashed"`，
`GET /api/music` 默认不再返回该音乐。
`.music-vault-trash` 会被音乐扫描器忽略，避免回收文件再次入库。

查询回收站：

```http
GET /api/music/trash
Authorization: Bearer change-me
```

仅返回 `deleteStatus = "trashed"` 的记录。响应示例：

```json
[
  {
    "id": 1,
    "title": "晴天",
    "artist": "周杰伦",
    "album": "叶惠美",
    "fileName": "周杰伦 - 晴天.flac",
    "originalPath": "/path/to/music/周杰伦 - 晴天.flac",
    "trashPath": "/path/to/music/.music-vault-trash/1/周杰伦 - 晴天.flac",
    "deletedAt": "2026-05-18T07:30:00",
    "trashFileExists": true,
    "deleteStatus": "trashed"
  }
]
```

恢复回收站音乐：

```http
POST /api/music/1/restore
Authorization: Bearer change-me
```

仅允许恢复 `deleteStatus = "trashed"` 的记录。
后端会校验 `trashPath` 存在、是普通文件，
并且真实路径位于当前音乐库根目录下的 `.music-vault-trash`；
恢复目标 `originalPath` 必须位于音乐库根目录下且不能位于 `.music-vault-trash`。
如果原路径目录不存在会自动创建；如果原路径已有文件，返回 `409 conflict`，不会覆盖。
恢复成功后，文件移回 `originalPath`，`deleteStatus` 改为 `active`，
`deletedAt` 和 `trashPath` 清空，默认音乐列表重新可见。

彻底删除回收站文件：

```http
DELETE /api/music/1/trash
Authorization: Bearer change-me
```

仅允许处理 `deleteStatus = "trashed"` 的记录。
后端会校验 `trashPath` 位于 `.music-vault-trash` 下且是普通文件，
然后物理删除该回收站文件。
成功后数据库记录保留，`deleteStatus` 改为 `deleted`，
`deletedAt` 和 `trashPath` 可继续用于审计最后删除时间和位置；
不会删除歌词、封面或其他关联资源。

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
  "artworkFileName": "周杰伦 - 晴天.png",
  "artworkFileExists": true
}
```

该接口为幂等绑定：若该歌曲已有主封面绑定，
旧绑定会被降级（`is_primary = false`），然后写入新绑定（`is_primary = true`）。
绑定目标 artwork 不存在时返回 `404`；目标 artwork 文件已缺失时返回 `400`。

导入本地图片并立即绑定：

```http
POST /api/music/1/artwork/import
Authorization: Bearer change-me
Content-Type: multipart/form-data
```

字段：

| 字段 | 说明 |
|------|------|
| `file` | 本地 `jpg/jpeg/png/webp` 图片，最大 10MB |

后端会将图片保存到 `app.artwork.scan-dir` 配置目录，
按歌曲信息生成文件名，例如 `刘若英 - 后来.jpg`；若文件名已存在则追加 `-1`、`-2`。
上传文件会按扩展名、Content-Type、图片可读性和 SHA-256 hash 校验；
hash 已存在时复用已有 `artworks` 记录，并将当前歌曲主封面切换到该 artwork。

响应：

```json
{
  "musicId": 1,
  "artworkStatus": "BOUND",
  "artworkId": 10,
  "artworkPreviewUrl": "/api/artworks/10/file",
  "artworkFileName": "刘若英 - 后来.jpg",
  "artworkFileExists": true
}
```

错误处理：歌曲不存在时返回 `404`；
文件类型不符、超过 10MB、无法识别为支持的图片、图片损坏、
Artworks 目录不可写或配置路径不是目录时返回 `400`。

后续可考虑 `POST /api/music/{musicId}/artwork/import-url`，但本轮不实现。
URL 导入需要额外处理：SSRF 防护、文件大小限制、Content-Type 校验、
下载超时、重定向限制、域名/IP 限制、来源记录。

#### 批量更新元数据（v0.7.4）

```http
PUT /api/music/metadata/batch
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "ids": [1, 2, 3],
  "artist": "周杰伦",
  "album": "叶惠美",
  "year": 2003,
  "genre": "流行"
}
```

请求字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `ids` | number[] | 必填，待更新的音乐 ID 列表 |
| `artist` | string | 可选，歌手，空字符串不更新 |
| `album` | string | 可选，专辑，空字符串不更新 |
| `year` | number | 可选，年份，必须为 1900–当前年份+1 之间的整数，空值不更新 |
| `genre` | string | 可选，流派，空字符串不更新 |

**不支持批量编辑 `title` 和 `trackNo`**，这两个字段仍建议单首编辑。

响应示例：

```json
{
  "updated": 3
}
```

**设计说明：**
- 只更新用户实际填写的字段，空字段（不传或传 `null`）不更新
- 元数据只保存到 SQLite，不读取、不写回真实音频文件标签
- 不会联网刮削或 AI 自动补全
- 保存前前端会提示本次操作将影响多少首音乐

#### 元数据同步（v0.8.4 / v0.8.5 / v0.8.6 / v0.8.7）

v0.8.4 新增音频文件内嵌 Tag 读取与数据库/文件双向同步能力，
v0.8.5 补充审计历史与回滚基础能力，
v0.8.6 补强参数校验、强化边界限制、明确回滚规则与风险说明，
v0.8.7 完成 v0.8 功能冻结与回归测试，确认完整功能链路稳定可用。

**⚠️ 风险提示：数据库写回音频文件会修改本地音频文件。执行前应确认文件已备份。v0.8.5 已提供审计历史页面与回滚能力。**

**同步范围（v0.8.5 收敛）：** 当前仅同步 title（歌曲名）、artist（歌手）、album（专辑）三个核心字段。
albumArtist、year、genre、trackNumber 等高级字段暂不参与同步，
原因：不同来源文件中这些字段的可用性和含义存在差异，
暂不纳入当前版本同步范围，避免误覆盖。

**批量操作限制（v0.8.6）：** 批量同步和批量回滚每次最多 100 条，超出返回 `400 Bad Request`。

接口列表：

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/music/{id}/metadata/compare` | 单曲：比较数据库与文件 Tag 差异 |
| POST | `/api/music/{id}/metadata/apply-file-to-db` | 单曲：将文件 Tag 覆盖到数据库 |
| POST | `/api/music/{id}/metadata/apply-db-to-file` | 单曲：将数据库元数据写回音频文件 Tag |
| POST | `/api/music/metadata/compare` | 批量：比较多个音乐的差异（最多 100 条） |
| POST | `/api/music/metadata/apply-file-to-db` | 批量：将文件 Tag 覆盖到数据库（最多 100 条） |
| POST | `/api/music/metadata/apply-db-to-file` | 批量：将数据库元数据写回音频文件（最多 100 条） |

**支持的音频格式（读取）：** mp3、flac、wav、m4a、aac、ogg、opus（基于 jaudiotagger 3.0.1 读取）。

**db_to_file 写入限制：** jaudiotagger 3.0.1 注册的 writer 支持
ogg/oga、flac、mp3、mp4/m4a/m4b、wav、wma、aif/aiff/aifc、dsf。
当前扫描包含的 aac、opus 格式无对应 writer 支持，
其中 .opus 文件不应等同于 Ogg Vorbis 处理。
`db_to_file` 操作对不支持写入的格式会返回明确失败结果，不会未捕获异常。
建议生产使用中将 `db_to_file` 限制在已验证格式（mp3/flac/m4a/ogg/wav）范围内。

##### 单曲差异比较

```http
GET /api/music/1/metadata/compare
Authorization: Bearer change-me
```

响应示例：

```json
{
  "musicId": 1,
  "database": {
    "title": "晴天",
    "artist": "周杰伦",
    "album": "叶惠美"
  },
  "embedded": {
    "title": "晴天",
    "artist": "Jay Chou",
    "album": "Ye Hui Mei"
  },
  "diffs": [
    { "field": "artist", "databaseValue": "周杰伦", "embeddedValue": "Jay Chou" },
    { "field": "album", "databaseValue": "叶惠美", "embeddedValue": "Ye Hui Mei" }
  ]
}
```

`database` 表示数据库中的元数据，`embedded` 表示音频文件内嵌 Tag 中读取到的元数据，`diffs` 表示差异字段（当前仅包含 title/artist/album）。

##### 单曲文件 Tag 覆盖数据库

```http
POST /api/music/1/metadata/apply-file-to-db
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "mode": "overwrite"
}
```

`mode` 固定为 `overwrite`（当前仅支持此模式）。`confirm` 参数可选，不传或为 `false` 时为预览模式，不实际写入。

响应示例：

```json
{
  "musicId": 1,
  "direction": "file_to_db",
  "mode": "overwrite",
  "status": "SUCCESS",
  "beforeDatabase": { "title": "晴天", "artist": "周杰伦", ... },
  "afterDatabase": { "title": "晴天", "artist": "Jay Chou", ... },
  "beforeFile": { "title": "晴天", "artist": "Jay Chou", ... },
  "afterFile": { "title": "晴天", "artist": "Jay Chou", ... },
  "changedFields": ["artist", "album"],
  "auditId": 10,
  "errorMessage": null
}
```

覆盖成功后，`tracks.metadataExtractedAt` 更新为当前时间，`tracks.metadataSource` 记录为 `embedded_tag`。

##### 单曲数据库元数据写回音频文件

```http
POST /api/music/1/metadata/apply-db-to-file
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "mode": "overwrite"
}
```

**⚠️ 此操作直接修改本地音频文件。执行前请确认文件已备份。** `confirm` 参数可选，不传或为 `false` 时为预览模式，不实际写入。

响应示例：

```json
{
  "musicId": 1,
  "direction": "db_to_file",
  "mode": "overwrite",
  "status": "SUCCESS",
  "beforeDatabase": { "title": "晴天", "artist": "周杰伦", ... },
  "afterDatabase": { "title": "晴天", "artist": "周杰伦", ... },
  "beforeFile": { "title": "晴天", "artist": "Jay Chou", ... },
  "afterFile": { "title": "晴天", "artist": "周杰伦", ... },
  "changedFields": ["artist", "album"],
  "auditId": 11,
  "errorMessage": null
}
```

写入成功后，`tracks.metadataUpdatedAt` 和 `tracks.metadataSource` 不变（此方向仅修改文件，不修改数据库记录）。

##### 批量差异比较

```http
POST /api/music/metadata/compare
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "musicIds": [1, 2, 3]
}
```

最多 100 条，超出返回 `400 Bad Request`。

响应为数组，每个元素同单曲差异比较的响应结构。

##### 批量同步（文件 Tag → 数据库 或 数据库 → 文件 Tag）

```http
POST /api/music/metadata/apply-file-to-db
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "musicIds": [1, 2, 3],
  "mode": "overwrite"
}
```

```http
POST /api/music/metadata/apply-db-to-file
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "musicIds": [1, 2, 3],
  "mode": "overwrite"
}
```

批量响应示例：

```json
{
  "batchId": "a1b2c3d4-e5f6-...",
  "total": 3,
  "success": 2,
  "failed": 1,
  "items": [ /* 单个 MetadataSyncResult 数组 */ ]
}
```

批量操作中部分失败不影响其他项，每项的 `status` 字段标识成功/失败。

##### 审计记录

每次覆盖操作均会在 `music_metadata_sync_audit` 表写入一条审计记录，包含：

- 完整操作快照（`beforeDatabaseJson`、`afterDatabaseJson`、`beforeFileJson`、`afterFileJson`）
- 变更字段列表（`changedFieldsJson`，当前仅包含 title/artist/album）
- 操作方向（`file_to_db` 或 `db_to_file`）、模式（`overwrite`）
- 操作结果状态和错误信息（失败时）
- `rollbackStatus` 和 `rollbackOfAuditId` 字段预留给回滚能力

**审计查询接口（v0.8.5 / v0.8.6）：**
- `GET /api/music/metadata/audits` — 审计历史分页列表，支持 `page`、`pageSize`、`direction`、`status`、`rollbackStatus` 筛选
- `GET /api/music/metadata/audits/{auditId}` — 审计详情，包含完整操作快照
- `GET /api/music/metadata/audits/{auditId}/rollback-preview` — 回滚预览
- `POST /api/music/metadata/audits/{auditId}/rollback` — 执行单条回滚（`{ "confirm": true }`）
- `POST /api/music/metadata/audits/rollback-preview` — 批量回滚预览（最多 100 条，超出返回 `400`）
- `POST /api/music/metadata/audits/rollback` — 批量执行回滚（最多 100 条，超出返回 `400`）

**回滚规则（v0.8.6）：** 可回滚的审计记录需同时满足：
`status = SUCCESS` 且 `rollbackStatus = NOT_ROLLED_BACK`。
以下记录不可回滚：失败记录（`status = FAILED`）、
已回滚记录（`rollbackStatus = ROLLED_BACK`）、
ROLLBACK 记录（`rollbackOfAuditId != null`）。

回滚操作同样写入审计记录（`rollbackOfAuditId` 指向前序审计记录），用于追溯。

**暂不支持（v0.8.7）：** 全库同步、全库回滚、按歌手一键同步、按歌手一键回滚。如需大规模同步，建议分批调用，每批不超过 100 条。

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
  "artworkFileName": null,
  "artworkFileExists": null
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
    scan-dir: /path/to/artwork
```

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/artworks?page=0&size=20&keyword=晴天&boundStatus=all` | 封面列表分页查询，可按文件名/标题搜索和绑定状态筛选 |
| GET | `/api/artworks/{id}` | 封面详情 |
| GET | `/api/artworks/{id}/file` | 封面文件访问，供 `<img>` 使用 |
| POST | `/api/artworks/scan` | 扫描本地封面目录 |

### 封面列表查询

```http
GET /api/artworks?page=0&size=20&keyword=晴天&boundStatus=all
Authorization: Bearer change-me
```

`boundStatus` 可选值：`all`（默认）、`bound`、`unbound`。

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
      "sourcePath": "/path/to/artwork/周杰伦 - 晴天.png",
      "title": "周杰伦 - 晴天",
      "description": null,
      "previewUrl": "/api/artworks/1/file",
      "fileExists": true,
      "boundCount": 1,
      "boundTracks": [],
      "createdAt": "2026-05-15T22:40:00",
      "updatedAt": "2026-05-15T22:40:00"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1
}
```

列表接口只返回 `boundCount` 用于快速展示绑定数量，
`boundTracks` 固定为空数组以避免分页列表展开过多歌曲信息；
需要查看关联歌曲时使用 `GET /api/artworks/{id}`。

### 封面详情

```http
GET /api/artworks/1
Authorization: Bearer change-me
```

响应字段同列表项，并在 `boundTracks` 中返回轻量关联音乐信息：

```json
{
  "id": 1,
  "fileName": "周杰伦 - 晴天.png",
  "fileExists": true,
  "boundCount": 1,
  "boundTracks": [
    {
      "musicId": 1,
      "trackId": 1,
      "fileName": "周杰伦 - 晴天.flac",
      "filePath": "/path/to/music/周杰伦 - 晴天.flac",
      "title": "晴天",
      "artist": "周杰伦"
    }
  ]
}
```

### 封面文件访问

```http
GET /api/artworks/1/file
Authorization: Bearer change-me
```

成功时返回图片二进制，`Content-Type` 为入库时记录的 MIME 类型。
该接口不会接受任意文件路径，只能通过已入库的 `artwork.id` 访问，
并会校验真实文件路径仍位于 `app.artwork.scan-dir` 根目录内。
若数据库记录存在但本地文件已被删除，返回 `404`，
同时列表/详情中的 `fileExists` 会返回 `false`，
音乐列表中的 `artworkFileExists` 会返回 `false`。

### 扫描封面

```http
POST /api/artworks/scan
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "path": "/path/to/artwork"
}
```

`path` 可省略，省略时使用 `app.artwork.scan-dir`。
如果配置的 Artworks 目录不存在，扫描或导入时会自动创建；
如果配置路径已存在但不是目录、不可读或不可写，返回 `400`。
扫描支持 `jpg`、`jpeg`、`png`、`webp`，按真实文件路径和 SHA-256 文件哈希去重。
扫描目录和扫描到的真实文件路径都必须位于配置根目录内，
目录穿越和指向根目录外的符号链接文件会被拒绝或计入失败。

扫描入库后会尝试按文件名自动绑定：
封面文件名去扩展名后，与 `track_files.file_name` 或 `track_files.file_path`
文件名去扩展名完全相同，则写入 `music_artwork_bindings`。
当前 `music_artwork_bindings.music_id` 使用 `track_files.id`，
`relation_type` 使用 `track_cover`。已有主封面绑定时不会覆盖。

响应示例：

```json
{
  "path": "/path/to/artwork",
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
| DELETE | `/api/lyrics/{id}` | 删除未绑定歌词记录；不删除磁盘 `.lrc` 源文件（v1.2.4） |
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
      "sourcePath": "/path/to/lyrics/周杰伦 - 晴天.lrc",
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
  "sourcePath": "/path/to/lyrics/周杰伦 - 晴天.lrc",
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

### 删除未绑定歌词记录

```http
DELETE /api/lyrics/1
Authorization: Bearer change-me
```

仅允许删除当前没有任何 `song_lyrics` 引用的歌词记录。删除动作只清理数据库中的 `lyrics` 行，不会删除 Lyrics 目录中的 `.lrc` 源文件。若删除时记录已经重新绑定歌曲，接口返回 `409 conflict`。

成功响应：

```json
{
  "success": true,
  "message": "Lyric record deleted; source file was not removed"
}
```

### 扫描歌词

```http
POST /api/lyrics/scan
Authorization: Bearer change-me
Content-Type: application/json
```

```json
{
  "path": "/path/to/lyrics",
  "overwritePrimary": false
}
```

`path` 为空时使用 `music-vault.lyric-dirs` 的第一个目录。
扫描目录必须位于 `MUSIC_VAULT_LYRIC_DIRS` 允许范围内。
`overwritePrimary` 默认为 `false`，不会覆盖已有主歌词绑定。
歌词扫描目录是本地歌词可用性的事实来源：本次扫描完整成功且 `failed=0` 时，系统会检查当前扫描目录范围内的 `LOCAL_FILE` 歌词记录。若数据库中的 `source_path` 已不再对应实际存在的 `.lrc` 文件，会解除相关 `song_lyrics` 绑定；之后 `/api/open/v1/tracks/{id}/lyrics/meta` 返回 `available=false`，`/api/open/v1/tracks/{id}/lyrics` 返回 `404`。如果扫描目录不可访问、遍历中断或文件处理失败，本次扫描不会执行缺失源文件清理。

响应示例：

```json
{
  "path": "/path/to/lyrics",
  "totalFiles": 278,
  "imported": 0,
  "duplicateFiles": 278,
  "matched": 274,
  "unmatched": 4,
  "skippedBindings": 0,
  "failed": 0
}
```

歌词扫描会递归查找 `.lrc` 文件，
读取 `[ti:]`、`[ar:]`、`[al:]` 标签和文件名中的 `歌手 - 歌名` 作为基础元数据，
优先使用规范化后的 `source_path` 识别同一源文件。若同一路径的歌词记录此前因源文件删除而被解绑，恢复或更新 `.lrc` 后再次扫描会复用原 `lyrics` 行，刷新正文、内容哈希、标题、歌手、解析状态和更新时间，并重新尝试恢复 `song_lyrics` 绑定，不会插入同路径重复记录。内容 SHA-256 仍作为无同路径记录时的去重兜底。
历史数据中若存在同一规范化 `source_path` 的重复记录，扫描仅会在能明确确认重复记录未绑定时清理；无法安全判断的记录会保留给管理员在歌词管理页手动删除。
自动绑定依赖音乐扫描生成的 `tracks.normalized_title`，
因此首次导入或旧库升级时推荐顺序是：

1. 先执行 `POST /api/music/scan`，补齐 `track_files.track_id` 和 `tracks` 元数据。
2. 等扫描任务 `completed` 后，再执行 `POST /api/lyrics/scan`。
3. 刷新 `GET /api/music`，已绑定有效 LRC 的歌曲会返回 `lyricStatus = LRC_READY` 和 `lyricId`；已有可读取 SWLRC 的歌曲会返回 `SWLRC_READY`。

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
  "sourcePath": "/path/to/lyrics/周杰伦 - 晴天.lrc",
  "format": "LRC",
  "parseStatus": "PARSED",
  "parseMessage": null,
  "content": "[ti:晴天]\n[ar:周杰伦]\n...",
  "createdAt": "2026-05-14T06:40:00",
  "updatedAt": "2026-05-14T06:40:00"
}
```

`/api/songs/{id}/lyrics` 是旧版歌词绑定详情接口，仍使用 `BOUND`、`NO_LYRIC`、`PARSE_FAILED`、`MISSING_FILE` 等绑定/解析语义。歌曲列表、首页看板和歌词待办从 v1.3.3 起统一使用歌曲级状态：`SWLRC_READY`、`LRC_READY`、`NO_LYRICS`、`ALIGNMENT_RUNNING`、`DRAFT_PENDING`、`FAILED`。`UNMATCHED` 是歌词扫描统计语义，表示导入了歌词文件但未找到歌曲候选。

### 错误格式

```json
{
  "code": "OPENAPI_TRACK_NOT_FOUND",
  "message": "Track not found",
  "traceId": "a1b2c3d4-e5f6-...",
  "details": {}
}
```

所有 OpenAPI 接口错误响应格式统一为 `code` + `message` + `traceId` + `details`。`traceId` 用于问题追踪，`details` 为可选的补充信息对象。

### 安全与访问控制

v1.1.3 起 OpenAPI 专用安全配置仅作用于 `/api/open/v1/*`，不影响后台管理 API、静态资源、健康检查或旧版封面文件接口。管理端登录和 OpenAPI 凭证是两条独立认证线：管理端使用账号密码 + Session Cookie；OpenAPI 使用 AK/SK + HMAC-SHA256。

当前 OpenAPI 主要服务于本地 / 局域网内的星语音乐盒联调和访问，不建议将 `/api/open/v1/*` 裸露到公网。HTTPS 仍然必要，HMAC 不能替代 HTTPS。星语音乐盒需要后续版本适配新认证方式；适配前可继续使用上一版本星语音库联调。

在未来加入候选确认、移动端确认、AI 辅助整理等写操作前，必须先建立清晰的安全边界。当前说明为访问风险提示，不代表登录、公网 HTTPS、反向代理模板或完整权限模型已经实现。

配置项：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `xingyu.openapi.credential.master-key` | 未配置 | OpenAPI Secret Key AES-GCM 加密 master key，生产环境必须配置 |
| `xingyu.openapi.hmac.timestamp-window-seconds` | `300` | timestamp 允许偏差窗口 |
| `xingyu.openapi.hmac.nonce-ttl-seconds` | `600` | nonce 防重放记录有效期 |
| `xingyu.openapi.auth.enabled` | `false` | 旧静态 Token 开关，v1.1.3 起废弃且不再参与认证 |
| `xingyu.openapi.auth.token` | 未配置 | 旧静态 Token，v1.1.3 起废弃 |
| `xingyu.openapi.rate-limit.enabled` | `false` | 是否启用 OpenAPI 简单 IP 限流 |
| `xingyu.openapi.rate-limit.requests-per-minute` | `120` | 每个客户端 IP 每分钟请求数 |
| `xingyu.openapi.access-log.enabled` | `true` | 是否记录 OpenAPI 访问日志 |

先在管理后台创建 OpenAPI 凭证：`POST /api/admin/openapi/credentials`，请求包含 `name`、`description`、`scopes`、`expiresAt`。创建响应会返回 `accessKey` 与 `secretKey`，其中 `secretKey` 只显示一次；列表、详情和日志不会返回明文 Secret，也不会返回加密密文。

OpenAPI 请求必须携带：

```http
X-Xingyu-Access-Key: xmv_ak_xxx
X-Xingyu-Timestamp: 1717890000000
X-Xingyu-Nonce: 550e8400-e29b-41d4-a716-446655440000
X-Xingyu-Signature-Version: v1
X-Xingyu-Signature: <lowercase-hex-hmac-sha256>
```

签名算法为 `HMAC-SHA256(secretKey, canonicalString)`。`canonicalString` 固定为 5 行：`METHOD`、`PATH_WITH_CANONICAL_QUERY`、`SHA256_HEX_BODY`、`TIMESTAMP`、`NONCE`。query 按参数名升序、同名按值升序并统一 URL 编码；无 query 时只使用 path。GET / DELETE 无 body 时 body hash 为 SHA-256 空字符串；POST / PUT / PATCH 使用原始 body 字节，不重新格式化 JSON。签名输出 lowercase hex。

timestamp 非数字或超出窗口返回 `401 OPENAPI_UNAUTHORIZED`；同一 Access Key + nonce 在有效期内重复使用返回 `401`；缺少 `OPENAPI_READ` 访问当前只读接口返回 `403 OPENAPI_FORBIDDEN`。旧 `Authorization: Bearer <token>` 与 `X-Xingyu-Api-Token` 不再可访问 OpenAPI。

限流开启后，服务端按客户端 IP 做每分钟限流。
客户端 IP 获取优先级为：`X-Forwarded-For` 第一个 IP、`X-Real-IP`、请求 remote address。
超过限制返回 `429`，错误码为 `OPENAPI_RATE_LIMITED`，
`details` 包含 `limit` 和 `windowSeconds`。

访问日志格式示例：

```text
OpenAPI access method=GET path=/api/open/v1/tracks status=200 durationMs=18 clientIp=127.0.0.1 traceId=...
```

访问日志不会记录 `Authorization`、`X-Xingyu-Api-Token` 或 token。

OpenAPI 错误码：

| 错误码 | 说明 |
|--------|------|
| `OPENAPI_INVALID_ARGUMENT` | 请求参数非法 |
| `OPENAPI_UNAUTHORIZED` | OpenAPI token 缺失或错误 |
| `OPENAPI_RATE_LIMITED` | OpenAPI 请求超过限流阈值 |
| `OPENAPI_TRACK_NOT_FOUND` | 曲目不存在或不可访问 |
| `OPENAPI_LYRICS_NOT_FOUND` | 歌词不存在 |
| `OPENAPI_WORD_LYRICS_NOT_FOUND` | 逐字歌词不存在 |
| `OPENAPI_ARTWORK_NOT_FOUND` | 封面不存在或不可访问 |
| `OPENAPI_UNSUPPORTED_SORT` | 不支持的排序字段 |
| `OPENAPI_INTERNAL_ERROR` | OpenAPI 内部错误 |
| `OPENAPI_CONFIG_ERROR` | OpenAPI 安全配置错误 |

### 接口清单

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/open/v1/server/info` | 服务信息（版本、API 版本、readOnly 标识、功能特性列表） |
| GET | `/api/open/v1/sync/state` | 音乐库状态（版本号、各维度统计计数、lastChangedAt） |
| GET | `/api/open/v1/sync/changes` | 增量变更日志 |
| GET | `/api/open/v1/tracks` | 曲目列表（分页、搜索、多维过滤、排序） |
| GET | `/api/open/v1/tracks/{id}` | 曲目详情 |
| GET | `/api/open/v1/tracks/{id}/lyrics` | 歌词原文（按需拉取） |
| GET | `/api/open/v1/tracks/{id}/lyrics/meta` | 歌词元数据 |
| GET | `/api/open/v1/tracks/{id}/word-lyrics` | 可选逐字歌词 SWLRC；仅导入对齐结果且 SWLRC 存在时可用 |
| GET | `/api/open/v1/tracks/{id}/artwork` | 封面图片（二进制流） |
| GET | `/api/open/v1/tracks/{id}/artwork/meta` | 封面元数据 |
| GET | `/api/open/v1/artists` | 歌手聚合列表（全量，无分页） |
| GET | `/api/open/v1/artists/{artistName}/tracks` | 指定歌手的曲目列表（分页） |
| GET | `/api/open/v1/albums` | 专辑聚合列表（全量，无分页） |
| GET | `/api/open/v1/albums/tracks` | 指定专辑的曲目列表（分页） |
| GET | `/api/open/v1/match/track` | 本地音乐与服务端元数据匹配 |

### 服务信息

```http
GET /api/open/v1/server/info
```

响应示例：

```json
{
  "serviceName": "xingyu-music-vault",
  "serviceVersion": "1.3.3",
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
`features` 映射中 `true` 表示已实现，`false` 表示未实现。
建议客户端启动时调用此接口，验证 `apiVersion` 与客户端预期一致，
并检查 `features` 中需要的功能是否为 `true`。

### 音乐库状态

```http
GET /api/open/v1/sync/state
```

响应示例：

```json
{
  "libraryVersion": 128,
  "trackCount": 529,
  "artistCount": 42,
  "albumCount": 38,
  "lyricsCount": 487,
  "artworkCount": 510,
  "lastUpdatedAt": "2026-05-26T06:00:00",
  "lastChangedAt": "2026-05-26T22:15:30+08:00",
  "changesAvailable": true
}
```

`libraryVersion` 是 OpenAPI 同步版本号，
每次记录歌曲、歌词或封面变更时递增，初始值为 1。
`lastChangedAt` 是最近一次 OpenAPI 变更日志记录时间。
`changesAvailable` 表示服务端支持 `/sync/changes` 增量同步能力，
固定返回 `true`，不表示特定客户端是否存在待同步变更。
`lastUpdatedAt` 保留用于兼容旧客户端，
它表示活跃歌曲 `track_files.updatedAt` 与关联 `tracks.updatedAt` 中的最大值（取较新者）。
各计数字段（trackCount、artistCount、albumCount、lyricsCount、artworkCount）
均统计当前活跃曲目及其主绑定资源，不含已删除曲目；`lyricsCount` 仅统计主绑定存在且歌词记录仍存在的资源，`artworkCount` 仅统计主封面文件真实可读的资源。

### 增量变更

```http
GET /api/open/v1/sync/changes?sinceVersion=120&limit=500
```

参数：

| 参数 | 说明 |
|------|------|
| `sinceVersion` | 起始版本，返回 `version > sinceVersion` 的变更；默认 0 |
| `limit` | 返回条数，默认 500，最大 1000 |

响应示例：

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
      "updatedAt": "2026-05-26T22:10:00+08:00"
    }
  ]
}
```

返回项按 `version` 升序排列。
`toVersion` 为响应生成时的当前 `libraryVersion`。
`hasMore` 为 `true` 表示服务端仍有 `version` 更大的变更，
客户端应继续用最后一条 `version` 作为下一次 `sinceVersion` 拉取。

### 曲目列表

```http
GET /api/open/v1/tracks?page=0&pageSize=20&keyword=周杰伦&metadataStatus=complete&lyricsStatus=BOUND&artworkStatus=BOUND
```

参数：

| 参数 | 说明 |
|------|------|
| `page` | 页码，从 0 开始，默认 0 |
| `pageSize` | 每页条数，默认 20，最大 100 |
| `keyword` | 模糊匹配文件名、标题、歌手、专辑、流派 |
| `artist` | 按歌手名精确过滤（小写 trim 后比较） |
| `album` | 按专辑名精确过滤（小写 trim 后比较） |
| `year` | 按年份精确过滤 |
| `genre` | 按流派精确过滤 |
| `metadataStatus` | 按元数据状态过滤，允许值：`pending`、`matched`、`missing`、`ignored` |
| `lyricsStatus` | OpenAPI 客户端过滤语义，允许值：`BOUND`（含 `AVAILABLE`）、`NO_LYRIC`（含 `MISSING`）；管理端 `/api/music` 的歌曲级展示状态见上文 `lyricStatus` |
| `artworkStatus` | 按封面状态过滤，允许值：`BOUND`（含 `AVAILABLE`）、`MISSING` |
| `updatedAfter` | ISO-8601 日期时间，返回 `updatedAt` 严格晚于该时间的曲目（`updatedAt > updatedAfter`） |
| `sort` | 排序字段：`updatedAt`（默认）、`title`、`artist`、`album`、`year`、`durationMs`、`trackNo`、`metadataStatus`、`lyricsStatus`、`artworkStatus`、`fileName`、`createdAt` |
| `order` | 排序方向：`asc` 或 `desc`（默认 `desc`） |

`lyricsStatus=BOUND` 与 `artworkStatus=BOUND` 过滤器基于数据库主绑定关系筛选；响应中的 `lyricsAvailable` / `artworkAvailable` 才表示客户端是否可以继续请求对应资源。若封面绑定存在但文件缺失或路径不可访问，`artworkStatus=BOUND` 过滤仍可能命中该曲目，但响应会返回 `artworkAvailable=false`、`artworkUrl=null`。

响应示例：

```json
{
  "items": [
    {
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
  ],
  "page": 0,
  "pageSize": 20,
  "total": 1
}
```

**注意**：`durationMs` 单位为毫秒（如 `268000`）。
`artworkUrl` 为相对路径，客户端使用服务端 base URL 拼接。
列表接口中 `artworkUrl` 返回相对路径，不是 base64。
歌词和封面正文按需调用单独接口获取，不要在列表接口中加载。

`updatedAfter` 使用严格大于语义：如果曲目的 `updatedAt` 与参数值完全相等，该曲目不会出现在结果中。

### 曲目详情

```http
GET /api/open/v1/tracks/{id}
```

响应示例：

```json
{
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
```

本接口不返回 `filePath`（文件路径不暴露给外部客户端）。

### 歌词原文

```http
GET /api/open/v1/tracks/{id}/lyrics
```

无歌词时返回 `404 OPENAPI_LYRICS_NOT_FOUND`。这对客户端是可降级业务状态，不应视为致命错误。

响应示例：

```json
{
  "trackId": 1,
  "lyricId": 1,
  "format": "LRC",
  "content": "[ti:晴天]\n[ar:周杰伦]\n[al:叶惠美]\n[00:00.00] 作曲 : 周杰伦\n[00:05.00] 作词 : 方文山\n[00:10.00] 故事的小黄花\n...",
  "hash": "a3f5e2d...",
  "updatedAt": "2026-05-14T06:40:00"
}
```

成功响应会设置 `ETag`。客户端再次请求时可携带 `If-None-Match`，若命中当前歌词 ETag，服务端返回 `304 Not Modified`，并继续返回当前 `ETag` 响应头。

### 歌词元数据

```http
GET /api/open/v1/tracks/{id}/lyrics/meta
```

响应示例：

```json
{
  "trackId": 1,
  "available": true,
  "lyricId": 1,
  "format": "LRC",
  "hash": "sha256:a3f5e2d...",
  "etag": "\"lyrics-1-a3f5e2d...\"",
  "updatedAt": "2026-05-14T06:40:00"
}
```

`available` 为 `true` 表示该曲目有主绑定歌词，客户端可以继续请求 `/lyrics`。`available=false` 表示无歌词、未绑定或歌词资源不可提供，客户端应降级为无歌词状态。
本接口不返回 `language` 和 `hasTimeTag`。
`hash` 由歌词内容、歌词格式和语言组合后计算 SHA-256，格式为 `sha256:<hex>`。
`etag` 使用当前歌词 hash 生成，供 `/lyrics` 条件请求使用。

v1.3.0 起，`lyrics/meta` 对导入了逐字歌词的歌曲追加以下可选字段：

```json
{
  "wordLyricsAvailable": true,
  "wordLyricsUrl": "/api/open/v1/tracks/1/word-lyrics",
  "lyricsVersionSource": "ALIGNMENT"
}
```

旧 LRC 客户端可以忽略这些新增字段。`wordLyricsAvailable=false` 或 `wordLyricsUrl=null` 表示没有可用 SWLRC。

### 逐字歌词 SWLRC

```http
GET /api/open/v1/tracks/1/word-lyrics
```

仅当前主歌词来自已导入的对齐结果，且关联 SWLRC 文件真实存在时返回。无逐字歌词时返回 `404 OPENAPI_WORD_LYRICS_NOT_FOUND`，客户端应降级为普通 LRC。

成功响应：

```json
{
  "trackId": 1,
  "lyricId": 11,
  "format": "SWLRC",
  "content": "{...}",
  "hash": "sha256:...",
  "updatedAt": "2026-07-03T21:57:39"
}
```

### 封面文件

```http
GET /api/open/v1/tracks/{id}/artwork
```

无封面、封面文件缺失、不可读或路径不在允许根目录内时返回 `404 OPENAPI_ARTWORK_NOT_FOUND`。这对客户端是可降级业务状态，不应视为致命错误。
成功时直接返回图片二进制流，`Content-Type` 为图片 MIME 类型，
支持 PNG/JPEG/WebP，含 HTTP 缓存头（`Cache-Control: max-age=3600`）和 ETag。
客户端再次请求时可携带 `If-None-Match`，
若命中当前封面 ETag，服务端返回 `304 Not Modified`。

`OpenTrackResponse.artworkUrl` 字段返回相对路径 `/api/open/v1/tracks/{id}/artwork`，客户端应使用服务端 base URL 拼接该相对路径获取图片。

### 封面元数据

```http
GET /api/open/v1/tracks/{id}/artwork/meta
```

响应示例：

```json
{
  "trackId": 1,
  "available": true,
  "artworkId": 1,
  "mimeType": "image/png",
  "fileSize": 123456,
  "width": 600,
  "height": 600,
  "hash": "sha256:b4c3d2e...",
  "etag": "\"artwork-1-b4c3d2e...\"",
  "updatedAt": "2026-05-14T06:40:00"
}
```

本接口不返回 `fileName`。

`available=true` 表示封面文件真实可读，且路径位于服务端允许的封面 / 音乐根目录内，客户端可以继续请求 `/artwork`。`available=false` 表示无封面、未绑定、文件缺失、Docker / local-dev 路径不一致或路径不可访问，客户端应降级为无封面状态。

### 资源可用状态契约（v0.9.5）

`tracks` 列表和详情中的 `lyricsAvailable` / `artworkAvailable` 必须与 meta 接口一致：

| 字段 | 语义 |
|------|------|
| `lyricsAvailable=true` | `/lyrics/meta` 返回 `available=true`，客户端可请求 `/lyrics` |
| `lyricsAvailable=false` | 客户端不应继续请求 `/lyrics`，可直接进入无歌词状态 |
| `artworkAvailable=true` | `/artwork/meta` 返回 `available=true`，客户端可请求 `/artwork` |
| `artworkAvailable=false` | 客户端不应继续请求 `/artwork`，可直接进入无封面状态 |

`404` 表示资源不存在或当前不可提供，客户端应降级；`500` 表示服务端异常，客户端应降级并可记录错误。

星语音乐盒当前歌词来源策略为：本地已有缓存歌词 → 星语音库 OpenAPI 歌词 → 无歌词状态。客户端不再假设会请求公网 LRCLIB。

### 歌手列表

```http
GET /api/open/v1/artists
```

无分页参数，返回全量歌手列表，按歌手名字升序排列。

响应示例：

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

路径参数 `artistName` 为歌手名 URL-safe 编码，传入 tracks 接口的 artist 参数筛选该歌手曲目。响应结构同曲目列表，按 title 升序排列。

### 专辑列表

```http
GET /api/open/v1/albums
```

无分页参数，返回全量专辑列表，按专辑名升序排列（同名专辑按专辑歌手升序）。

响应示例：

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

参数：

| 参数 | 说明 |
|------|------|
| `album` | 专辑名（必填） |
| `artist` | 专辑歌手（选填，用于区分同名专辑） |
| `page` | 页码，从 0 开始，默认 0 |
| `pageSize` | 每页条数，默认 20，最大 100 |

`album` 和 `artist` 均为原始字符串，无需 URL-safe 编码。响应结构同曲目列表，按 trackNo 升序排列。

### 本地音乐匹配

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

响应示例：

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

无可匹配曲目时返回 `200` 且 `matched: false`，
`score` 为 0，`reason` 为 `"No exact title match"`，`track` 为 `null`。
建议客户端持有本地音乐文件时，
先通过 match/track 查询服务端对应曲目 ID，建立关联后再使用其他接口。

### OpenAPI 安全配置（v1.1.3）

v0.9.2 的静态 OpenAPI Token 认证已废弃。v1.1.3 起，所有 `/api/open/v1/*` 请求必须使用管理端创建的 AK/SK + HMAC-SHA256 签名；`Authorization: Bearer <token>` 与 `X-Xingyu-Api-Token` 不再可用。

认证仅作用于 `/api/open/v1/*`；后台管理 API 使用管理员 Session Cookie，不与 OpenAPI 凭证混用。配置项和签名规则见本文前文“安全与访问控制”章节。

**限流（默认关闭）：**

```properties
xingyu.openapi.rate-limit.enabled=true
xingyu.openapi.rate-limit.requests-per-minute=120
```

客户端 IP 识别优先级：`X-Forwarded-For` 第一个 IP > `X-Real-IP` > 远端地址。超过限制返回 `429 OPENAPI_RATE_LIMITED`。

**访问日志（默认开启）：**

访问日志记录 `method`、`path`、`status`、`durationMs`、`clientIp`、`traceId`。
以下内容不会被记录：`Authorization` 请求头值、`X-Xingyu-Api-Token` 请求头值、OpenAPI Access Key / Secret Key、签名值和 master key。

**错误码：**

| 状态码 | code | 说明 |
|--------|------|------|
| 401 | `OPENAPI_UNAUTHORIZED` | 未提供或无效的 AK/SK + HMAC 签名 |
| 401 | `OPENAPI_CREDENTIAL_DISABLED` | OpenAPI 凭证已禁用 |
| 401 | `OPENAPI_CREDENTIAL_EXPIRED` | OpenAPI 凭证已过期 |
| 403 | `OPENAPI_FORBIDDEN` | OpenAPI 凭证缺少所需 scope |
| 429 | `OPENAPI_RATE_LIMITED` | 请求超出限流阈值 |
| 413 | `OPENAPI_PAYLOAD_TOO_LARGE` | 请求 body 超出 HMAC 签名读取上限 |
| 400 | `OPENAPI_INVALID_ARGUMENT` | 请求参数错误 |
| 500 | `OPENAPI_CONFIG_ERROR` | OpenAPI master key 未配置或配置错误 |

所有 OpenAPI 错误响应格式统一：

```json
{
  "code": "OPENAPI_UNAUTHORIZED",
  "message": "...",
  "traceId": "...",
  "details": {}
}
```

### 暂不支持

v0.9.5 是 OpenAPI 联调反馈收口与契约稳定版本，以下能力不提供：

- 音频流播放（`GET /api/open/v1/tracks/{id}/stream`）
- 客户端修改元数据（`PUT /api/open/v1/tracks/{id}` 等）
- 上传音乐（文件上传接口）
- 复杂限流 / 分布式限流 / Redis 限流
- WebSocket 推送（实时同步）
- 客户端批量修改元数据
- 远程扫描音乐库
- 网络刮削
- AI 元数据补全
- OAuth / OIDC / JWT
- Token 自动轮换
- Secret 重置流程
- 反向代理配置
- 公网部署完整安全方案
- 通过 `X-Forwarded-For` 伪造 IP 的防护（v0.9.2 仅取第一个 IP，不校验真实性）

OpenAPI `/api/open/v1/*` 必须使用 AK/SK + HMAC 签名。后台管理 API 使用管理员 Session Cookie，二者互不混用。

### 客户端接入建议（v0.9.5）

1. **调用 `/api/open/v1/server/info`**，验证 `apiVersion` 与客户端预期一致，检查 `features` 中需要的功能是否为 `true`
2. **调用 `/api/open/v1/sync/state`**，获取 `libraryVersion`，与本地保存的版本比对
3. **若 `libraryVersion` 未变化**：跳过同步，直接使用本地缓存
4. **若 `libraryVersion` 变化**：调用 `GET /api/open/v1/sync/changes?sinceVersion=<localVersion>`
5. **根据 `changedFields` 刷新缓存**：对 `created` / `updated` 歌曲刷新本地缓存；对 `deleted` 歌曲移除本地缓存
6. **`hasMore=true` 时**：继续用最后一条变更的 `version` 作为下一次 `sinceVersion`，分批拉取直至 `hasMore=false`
7. **歌词和封面按需刷新**：先读 `lyrics/meta` 或 `artwork/meta` 接口比对 `hash`；或直接在正文请求中携带 `If-None-Match`，命中 `304` 时复用本地缓存
8. **客户端保存最新 `libraryVersion`**，用于下次启动时的增量判断

歌词 `hash` 由歌词内容、格式和语言组合计算 SHA-256，格式为 `sha256:<hex>`；
封面 `hash` 按图片二进制内容计算 SHA-256，格式为 `sha256:<hex>`。
ETag 由服务端生成，供条件请求使用。

## 规划中接口

以下接口仅为规划，当前版本尚未实现。

### 曲目

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/tracks/match` | 发起曲目匹配 |

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
