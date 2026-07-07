# 系统架构

## 总体架构

```
┌─────────────────────────────────────────────────────┐
│                    Clients                           │
│         (Web UI / Mobile App / Third-party)          │
└─────────────────┬───────────────────────────────────┘
                  │ HTTP/REST
┌─────────────────▼───────────────────────────────────┐
│              Backend (Quarkus)                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │  Scan    │ │ Metadata │ │  Lyrics  │ │ Artwork │ │
│  │  Engine  │ │  Service │ │  Service │ │ Service │ │
│  └──────────┘ └──────────┘ └──────────┘ └─────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │  Match   │ │  Review  │ │   API    │              │
│  │  Engine  │ │  Workflow│ │  Layer   │              │
│  └──────────┘ └──────────┘ └──────────┘              │
└─────────────────┬───────────────────────────────────┘
                  │ JDBC
┌─────────────────▼───────────────────────────────────┐
│         SQLite (dev) / PostgreSQL (prod)            │
└─────────────────────────────────────────────────────┘
```

## 前后端分离设计

- **后端**：Quarkus，提供 RESTful API，监听 8080 端口
- **前端**：Vue 3 SPA，通过 `/api/*` 调用后端，不做服务端渲染
- **部署**：同进程或反向代理均可，前端静态资源由后端 Serve 或独立 Nginx

## 后端模块规划

| 模块 | 职责 | 状态 |
|------|------|------|
| Scan Engine | 扫描音乐目录，文件发现与入库 | 已实现基础能力（v0.2，v0.3 兜底元数据） |
| Metadata Service | 歌手/专辑/曲目元数据的 CRUD，歌手聚合列表与详情，元数据同步与审计回滚 | 已实现（v0.8.7） |
| Lyrics Service | 本地 LRC 导入、存储、歌曲绑定、统一歌词状态、覆盖率统计、每日推荐、草稿确认和对齐资产导入 | 已实现基础能力（v0.5），草稿 / 对齐闭环（v1.3.0），工作台体验优化（v1.3.2），歌词待办看板（v1.3.3） |
| Artwork Service | 本地封面扫描、去重、文件访问与音乐绑定 | 已实现基础能力（v0.6） |
| Match Engine | 自动匹配音乐指纹与元数据源 | 规划中 |
| Review Workflow | 人工审核工作流与状态机 | 规划中 |
| API Layer | 统一 REST 入口，Bearer Token 鉴权，异常映射；OpenAPI v0.9.2 缓存、增量同步、安全与访问控制增强（`/api/open/v1/*`）；v0.9.3 完成后端打包与 Docker 基础启动验证 | 已实现基础能力；OpenAPI 已实现（v0.9.3） |
| OpenAPI Security Filter | 可选 API Token 认证、简单 IP 限流、访问日志 | 已实现（v0.9.2，仅作用于 `/api/open/v1/*`，不影响后台管理 API） |
| Music Workbench | 管理端歌曲工作台、受保护音频播放、校验聚合、OpenAPI 输出预览、草稿编辑和 SWLRC 逐字试听 | 已实现 MVP（v1.2.1），小屏布局优化（v1.2.2），歌词工作台体验优化（v1.3.2） |
| Brave Search Proxy | 管理端 Brave Key 配置、加密托管、候选来源搜索代理 | 已实现（v1.3.2，仅返回候选来源，不抓取第三方网页正文） |

## 前端页面规划

| 页面 | 路由 | 状态 |
|------|------|------|
| 概览仪表盘 | `/dashboard` | 已实现（统计卡片，v1.3.3 新增歌词覆盖率与每日待办） |
| 全部歌曲（表格/卡片视图） | `/music` | 已实现（v0.8.0，v1.3.3 新增歌词状态筛选） |
| 歌曲工作台 | `/music/workbench` | 已实现 MVP（v1.2.1），小屏布局优化（v1.2.2），手工草稿、Brave 候选来源和 SWLRC 逐字试听（v1.3.2） |
| 歌手浏览 | `/artists` | 已实现（v0.8.1） |
| 歌手详情 | `/artists/:artistKey` | 已实现（v0.8.2） |
| 专辑浏览 | `/albums` | 已实现（v0.8.3） |
| 专辑详情 | `/albums/detail` | 已实现（v0.8.3） |
| 歌词管理 | `/lyrics` | 已实现（v0.5.2） |
| 封面管理 | `/artwork` | 已实现（v0.6） |
| 扫描任务 | `/scan-jobs` | 已实现（路由保留，菜单隐藏） |
| 音乐文件 | `/track-files` | 已实现（路由保留，菜单隐藏） |
| 元数据审计 | `/metadata-audit` | 已实现（v0.8.5） |
| 回收站 | `/trash` | 后端已实现，前端通过音乐列表操作入口使用 |
| 设置 | `/settings` | 已实现 |

## 全部歌曲多视图设计（v0.8.0）

v0.8.0 起，全部歌曲页面支持两种视图：

**表格视图**：适合元数据管理和批量整理，保留完整的列信息（文件名、歌手、专辑、年份、流派、歌词状态、封面状态），支持排序和批量操作。

**卡片视图**：适合音乐化浏览体验，以封面缩略图为核心，每张卡片展示歌名、歌手、专辑、时长。无封面时显示默认占位图。

两种视图共享同一套筛选条件（搜索关键词、待整理/已整理状态等），随时可切换。

后续版本将在歌手浏览和专辑浏览中继续扩展音乐化浏览体验。

## 歌曲工作台设计（v1.2.1，v1.2.2 布局优化，v1.3.2 歌词体验优化）

v1.2.1 新增管理端「歌曲工作台」MVP，用于边听边看、只读校验。前端通过 `/music/workbench?id={musicId}` 选择当前歌曲，复用 `/api/music` 基础分页列表加载工作台范围，并通过独立组件展示播放器、元数据、歌词、封面和 OpenAPI 输出。

后端新增 `/api/admin/music/{id}/workbench` 聚合接口和 `/api/admin/music/{id}/openapi-preview` 预览接口。OpenAPI 预览由 `OpenApiPreviewService` 生成，尽量复用公开 OpenAPI 的 DTO 结构，但通过管理端登录态访问，不要求 AK/SK 签名。

音频播放使用 `/api/admin/music/{id}/audio`。该接口受管理端 Session 保护，只按数据库中已登记的 `TrackFile.filePath` 读取文件，并校验真实路径位于 `music-vault.music-dirs` 下；不接收任意路径参数，不新增匿名资源浏览入口。接口支持基础 Range 请求，便于浏览器 audio 播放和进度跳转。

v1.2.2 前端补强小屏与低高度布局：工作台通过页面级缩放变量降低布局密度，播放器按自身容器宽度响应，歌词和封面区域跟随缩放；元数据和 OpenAPI 输出 Tab 使用内部滚动承载长内容。

v1.3.2 在同一工作台内补齐歌词整理体验：草稿编辑区保持稳定高度，核心操作按钮上移；质量摘要、质量报告和 Worker 状态摘要增加中文解释，并将完整报告和低价值 Worker 原始细节后置。歌词展示优先尝试读取 `wordLyrics` 中的 SWLRC，并在前端按字级 token 渲染逐字高亮；没有 SWLRC、读取失败或解析失败时回退 `lyrics` 中的 LRC 行级歌词。该逐字渲染只用于试听和审核，不提供逐字时间轴编辑能力。

已知限制：管理端音频接口暂未提供 `Cache-Control`、ETag 或 `Last-Modified` 等缓存协商头。浏览器可通过 Range 请求完成播放和拖动，但大文件重复播放的缓存效率仍有优化空间，后续版本可补充条件请求或更明确的缓存策略。

本工作台不写回音频 Tag，不做第三方网页歌词抓取或 AI 自动写词；后续版本可在此页面基础上设计元数据 / 歌词 / 封面确认状态。

## 歌词工作台数据流（v1.3.0 / v1.3.1 / v1.3.2）

### Worker 草稿提取

```text
用户创建草稿提取任务
→ 音库写入 jobs 目录 request.json / READY
→ xingyu-lyrics-aligner Worker 生成 transcript.cleaned.txt
→ 音库同步真实任务状态和草稿文本
→ 用户人工编辑 / 确认
→ DRAFT_CONFIRMED 可信歌词
→ 用户手动创建逐字对齐任务
```

音库只展示 Worker 已经写入的真实任务状态，不估算、不模拟、不伪造阶段或百分比进度。

### 手工草稿

```text
用户在歌曲工作台粘贴歌词文本
→ 音库创建 LYRIC_DRAFT_MANUAL / MANUAL_PASTE 草稿记录
→ 用户人工编辑 / 保存
→ 用户确认可信歌词
→ DRAFT_CONFIRMED 可信歌词
→ 用户手动创建逐字对齐任务
```

手工草稿不会创建 Worker `READY` 信号，不会被标记为 Worker 提取结果，也不会被 Worker 状态同步器当作可同步任务处理。

### Brave 来源辅助

```text
管理员配置 Brave Key
→ 音库后端调用 Brave Search API
→ 返回标题 / URL / 域名 / 摘要等候选来源
→ 用户打开外部网页自行查看
→ 用户手工复制歌词文本
→ 粘贴到草稿编辑区
→ 可把来源元信息关联到草稿
```

Brave Search 只用于候选来源发现。音库不抓取第三方网页正文，不下载、不缓存、不解析第三方歌词网页全文，也不把 Brave 搜索摘要写入歌词正文。

### 对齐与导入

```text
DRAFT_CONFIRMED 或其他可信歌词 + 本地音频
→ 音库写入对齐任务
→ Worker 生成 LRC / SWLRC / report / alignment
→ 管理员人工审核
→ 导入到歌词根目录 alignment/{songId}/{jobId}
→ 当前主歌词绑定到 ALIGNMENT LRC，SWLRC 作为可选逐字歌词资产
```

v1.3.1 起，正式对齐资产发布到歌词目录受控 `alignment` 子目录，普通歌词扫描会排除该目录，删除同步不处理 `ALIGNMENT` 资产。

### 明确不包含

- 第三方网页歌词抓取或 Brave 结果正文采集。
- Worker 真实 `progress.json` 协议读取。
- SSE 或等价实时推送。
- 文件监听。
- 音库伪造、估算或模拟 Worker 阶段和百分比进度。
- 逐字时间轴编辑器。

## 歌词状态与推荐数据流（v1.3.3）

统一歌词状态由 `SongLyricStatusService` 计算，首页、歌曲列表、歌曲工作台和推荐服务不再各自拼装判断。

```text
track_files + song_lyrics + lyrics + lyric_alignment_jobs + lyric_drafts
→ SongLyricStatusService
→ /api/music lyricStatus / hasLrc / hasSwlrc
→ /api/admin/lyrics/overview
→ /api/admin/lyrics/recommendations/*
```

每日推荐首次访问当天会写入 `lyric_daily_recommendation`，后续刷新返回同一批可见记录。跳过只隐藏当天推荐，不补位；换一首会将原记录标记为 `REPLACED`，并为原槽位持久化一条新推荐。推荐与随机候选都只生成候选，不自动创建歌词草稿、对齐任务或批量制作。

## 存储目录规划

```
/app/data/               # 数据目录（容器内）
├── music-vault.db       # SQLite 数据库文件
├── artworks/            # 封面存储
│   └── {track_id}/
├── lyrics/              # 歌词存储
│   └── {track_id}/
└── logs/                # 运行日志

/music                   # 音乐挂载目录（只读，容器内）
```

本地开发目录结构（仓库内占位目录）：

```
project-root/
└── backend/
    ├── data/            # 本地开发数据目录，可用于 MUSIC_VAULT_DATA_DIR
    ├── config/          # 本地开发配置目录，可用于 MUSIC_VAULT_CONFIG_DIR
    └── music/           # 本地开发音乐目录或软链接，可用于 MUSIC_VAULT_MUSIC_DIRS
```

Docker Compose 部署目录结构（相对于 `deploy/docker-compose.yml` 所在目录）：

```
deploy/
├── docker-compose.yml
├── data/                # 挂载到容器 /app/data
├── config/              # 挂载到容器 /app/config
└── logs/                # 可选挂载到容器 /app/data/logs
```

群晖 NAS 建议目录（v0.9.3 仅做规划与基础验证，不强制实机验收）：

```
/volume1/docker/xingyu-music-vault/data    # SQLite 与运行数据，挂载到 /app/data
/volume1/docker/xingyu-music-vault/logs    # 可选日志目录，挂载到 /app/data/logs
/volume1/music                             # 音乐目录，只读挂载到 /music:ro
```

## 管理后台 UI 体验优化（v0.7.3）

v0.7.3 聚焦于 Web 管理后台的 UI 体验改善，让日常音乐库整理更高效、更清爽。

**本次优化点：**

- **统计卡片**：在概览页展示音乐总数、元数据不完整数、歌词就绪数、封面就绪数、回收站文件数，一眼掌握全库状态
- **工具栏**：工具栏交互优化，支持快捷筛选与批量操作入口
- **列表展示**：音乐列表列宽、字段排布、状态展示优化，提升信息密度与可读性
- **状态标签**：歌词状态、封面状态、元数据状态用颜色标签区分，快速识别待处理项
- **空状态**：列表为空时展示友好提示，引导用户执行下一步操作
- **错误提示**：操作失败时给出明确错误原因，减少排查成本

**v0.7.3 仍是管理后台**，不是播放器，也不是音乐化浏览页。v0.7.3 不包含专辑视图、歌手视图、卡片视图、播放器、上传/批量上传、批量编辑、AI 补全、自动刮削、音频标签读取/写回、登录系统等能力，这些属于后续规划。

**v0.8 可继续扩展：** 专辑视图、歌手视图、卡片视图等音乐化浏览体验。

## 批量整理设计（v0.7.4）

v0.7.4 新增音乐列表多选和批量编辑共同元数据字段能力，用于快速整理同一批音乐的共同字段（如同一演唱会的现场专辑、同一张合辑的歌手/专辑信息）。

**本次支持字段：** `artist`（歌手）、`album`（专辑）、`year`（年份）、`genre`（流派）。

**不支持批量编辑的字段：**
- `title`（标题）：每首歌标题通常不同，建议单首编辑
- `trackNo`（曲目号）：由歌曲在专辑中的位置决定，建议单首编辑

**设计要点：**
- 批量编辑只更新用户实际填写的字段，空字段（不传或传 `null`）不更新
- 批量编辑只作用于元数据数据库表，不会写回真实音频文件标签
- 不会联网刮削，也不会 AI 自动补全，元数据由用户手动填写
- 保存前前端会提示本次操作将影响多少首音乐

**v0.7.4 仍是管理后台**，不是播放器，也不是音乐化浏览页。v0.7.4 不包含专辑视图、歌手视图、卡片视图、播放器、上传/批量上传、批量删除、AI 补全、自动刮削、音频标签读取/写回、登录系统等能力。

## 元数据同步设计（v0.8.4 / v0.8.5 / v0.8.6 / v0.8.7）

v0.8.4 新增音频文件内嵌 Tag 读取与数据库/文件双向同步能力，v0.8.5 补充审计历史与回滚基础能力，v0.8.6 补强参数校验、强化边界限制、明确回滚规则与风险说明，v0.8.7 完成 v0.8 功能冻结与回归测试，确认完整功能链路稳定可用。

**同步范围（v0.8.5 收敛）：** 当前仅同步 title（歌曲名）、artist（歌手）、album（专辑）三个核心字段。albumArtist、year、genre、trackNumber 等高级字段暂不参与同步，原因：这三个字段在常见音频 Tag 中较稳定，且与当前管理后台编辑能力直接对应；高级字段在不同来源文件中的可用性和含义存在差异，暂不纳入当前版本同步范围，避免误覆盖和用户理解成本。

**技术实现：**
- `AudioMetadataService`：封装 `jaudiotagger` 库，支持 mp3/flac/wav/m4a/aac/ogg/opus 的 Tag 读取与写入（写入限制于 mp3/flac/m4a/ogg/wav）
- `MusicMetadataSyncService`：核心同步逻辑，支持单曲和批量（最多 100 条）两种模式，`diffs()` 仅比较 title/artist/album，`applySnapshotToTrack()` 仅写入这三个字段
- 差异比较：读取数据库快照与文件 Tag 快照，仅对 title/artist/album 逐字段对比，返回 `diffs` 列表
- 覆盖操作：写入前记录完整快照（`beforeDatabaseJson`、`beforeFileJson`），写入后再记录一次（`afterDatabaseJson`、`afterFileJson`），用于审计和回滚

**双向同步方向：**
- `file_to_db`（文件 Tag → 数据库）：`applyFileToDatabase`，提取文件 Tag 覆盖数据库，仅写入 title/artist/album，`tracks.metadataSource = 'embedded_tag'`，`tracks.metadataExtractedAt` 更新
- `db_to_file`（数据库 → 文件 Tag）：`applyDatabaseToFile`，将数据库元数据写回音频文件 Tag，仅写入 title/artist/album，不修改数据库记录的 `metadataUpdatedAt` 和 `metadataSource`

**审计与回滚（v0.8.5 / v0.8.6）：**
- `MetadataAuditService`：审计记录查询、单条回滚、批量回滚预览与执行
- 回滚操作同样写入审计记录（`rollbackOfAuditId` 指向前序审计记录），形成操作链
- 批量回滚支持预览（`canRollback` 标识每条是否可回滚）和确认执行
- **回滚规则（v0.8.6）：** 可回滚的审计记录需同时满足：`status = SUCCESS` 且 `rollbackStatus = null`。以下记录不可回滚：失败记录（`status = FAILED`）、已回滚记录（`rollbackStatus = ROLLED_BACK`）、ROLLBACK 记录（`rollbackOfAuditId != null`）

**风险控制：**
- `db_to_file` 操作直接修改本地音频文件，通过 `Files.isWritable()` 校验文件可写性
- jaudiotagger 3.0.1 writer 不覆盖所有扫描格式：aac、.opus 无对应 writer，不支持写入的格式返回明确失败而非未捕获异常；建议生产使用中将 `db_to_file` 限制在已验证格式（mp3/flac/m4a/ogg/wav）范围内
- 批量操作中单条失败不影响其他项，每项返回独立 `status`
- 审计快照完整记录变更前后状态，支持回滚
- **回滚说明（v0.8.6）：** 回滚是基于历史快照执行一次新的同步操作，不是删除历史。回滚同样会生成新的审计记录，形成操作链，可追溯

**批量操作限制（v0.8.6）：**
- 批量同步（`apply-file-to-db` / `apply-db-to-file`）最多 100 条，超出返回 `400 Bad Request`
- 批量回滚（`/rollback`）最多 100 条，超出返回 `400 Bad Request`
- 参数校验强化：`musicIds` / `auditIds` 传入空数组、空值、重复值时返回明确的 `400 Bad Request` 说明

**v0.9.0 暂不支持：** 全库同步、全库回滚、按歌手一键同步、按歌手一键回滚、音频流播放、客户端修改元数据、网络刮削、AI 元数据补全、高级字段同步。

## OpenAPI 设计（v0.9.0 / v0.9.1 / v0.9.2 / v0.9.3）

v0.9.0 为播放器客户端提供只读 OpenAPI，前缀为 `/api/open/v1`。v0.9.1 在 v0.9.0 基础上补强客户端缓存判断和增量同步能力，支持资源变更感知和按需刷新。v0.9.2 增加只作用于 OpenAPI 的可选 API Token 认证、简单 IP 限流、访问日志和 `OPENAPI_*` 错误码。v0.9.3 完成后端打包运行和 Docker 基础启动验证，客户端仍通过 `musicVaultBaseUrl` 拼接 OpenAPI 相对路径。本版本仍不包含音频流、客户端元数据修改或 OAuth/JWT 认证。

**v0.9.3 不做：星语音乐盒真实联调、音频流、客户端写入元数据、OAuth/JWT、复杂代理信任链、分布式限流、公网 HTTPS、CI/CD、正式 NAS 实机部署验收、WebSocket 推送。**

**v0.9.1 / v0.9.2 新增能力：**

- `sync/state` 返回 `libraryVersion`（版本号）、`lastChangedAt`（最近一次 OpenAPI 变更日志时间）、`changesAvailable`（是否支持增量同步，固定为 true）
- `sync/changes` 增量变更接口：`sinceVersion`、`limit`、`hasMore`，按 `version` 升序返回变更
- 曲目变更记录：歌曲新增、删除、元数据更新、歌词更新、封面更新时记录 change log，并递增 `libraryVersion`
- `lyrics/meta` 返回 `sha256:` hash 和 ETag；`/tracks/{id}/lyrics` 支持 `If-None-Match`，命中时返回 `304 Not Modified`
- `artwork/meta` 返回基于图片二进制内容的 `sha256:` hash 和 ETag；`/tracks/{id}/artwork` 支持 `If-None-Match`，命中时返回 `304 Not Modified`
- `tracks?updatedAfter` 使用严格大于语义（`updatedAt > updatedAfter`）
- OpenAPI 可选 API Token 认证：`Authorization: Bearer <token>` 或 `X-Xingyu-Api-Token: <token>`
- OpenAPI 简单 IP 限流：按 `X-Forwarded-For` 第一个 IP、`X-Real-IP`、remote address 的优先级识别客户端 IP
- OpenAPI 访问日志：记录 `method`、`path`、`status`、`durationMs`、`clientIp`、`traceId`；不会记录 `Authorization`、`X-Xingyu-Api-Token` 或 token 值
- OpenAPI 错误码统一使用 `OPENAPI_*` 前缀，并覆盖认证、限流、配置错误等安全场景

**设计目标：** 让播放器客户端能够查询星语音库的音乐库元数据（曲目、歌词、封面、歌手、专辑），通过增量同步和资源 hash 实现高效的本地缓存管理。

**客户端同步流程（v0.9.2）：**

1. 调用 `GET /api/open/v1/server/info` 确认 API 能力和 `features`
2. 调用 `GET /api/open/v1/sync/state` 获取 `libraryVersion`
3. `libraryVersion` 未变化时跳过同步，直接使用本地缓存
4. `libraryVersion` 变化时调用 `GET /api/open/v1/sync/changes?sinceVersion=<localVersion>`
5. 根据 `changedFields` 对 created / updated 歌曲刷新本地缓存
6. 对 deleted 歌曲移除本地缓存
7. 歌词和封面通过 `hash` / `ETag` 按需刷新：先读 meta 接口比对 hash，或在正文请求中携带 `If-None-Match` 避免重复下载
8. 客户端保存最新 `libraryVersion`，用于下次启动时的增量判断

**客户端 baseUrl（v0.9.3）：**

客户端配置服务根地址 `musicVaultBaseUrl`，例如 `http://localhost:8080`、`http://<Mac-mini-LAN-IP>:8080` 或 `http://<NAS-LAN-IP>:8080`，再拼接 `{musicVaultBaseUrl}/api/open/v1/server/info` 等相对路径。客户端不应写死具体主机、端口和完整接口地址。

**技术实现：**

- OpenAPI change log 表（`openapi_sync_change_log`）：v0.9.1 新增表，歌曲新增、删除、元数据更新、歌词更新、封面更新时写入，`version` 自增
- `sync/state.libraryVersion` = 当前 change log 最大 `version`，初始值为 1
- `sync/changes` 返回 `version > sinceVersion` 的变更记录，`limit` 默认 500、最大 1000，`hasMore=true` 时继续用最后一条 `version` 作为下一次 `sinceVersion`
- 歌词 hash：基于歌词内容、歌词格式、歌词语言三者按固定顺序组合后计算 SHA-256（`null` 按空字符串处理，编码为 UTF-8），格式为 `sha256:<hex>`
- 封面 hash：按图片二进制内容计算 SHA-256，格式为 `sha256:<hex>`
- ETag 由服务端生成，用于 `If-None-Match` 条件请求，命中时返回 `304 Not Modified`
- OpenAPI 安全过滤器仅匹配 `/api/open/v1/*`，不影响后台管理 API、静态资源或健康检查
- OpenAPI API Token 使用安全比较，日志不打印 `Authorization`、`X-Xingyu-Api-Token` 或 token 值
- OpenAPI 限流为进程内每 IP 每分钟计数，v0.9.2 不依赖 Redis，也不做分布式同步

**v0.9.3 暂不支持：** 音频流播放、客户端修改元数据、上传音乐、OAuth/JWT 认证、分布式限流、复杂代理信任链、WebSocket 推送、星语音乐盒联调、公网 HTTPS、CI/CD、正式 NAS 实机部署验收。

## 安全删除设计（v0.7.2）

音乐库根目录是安全边界。删除操作只针对位于音乐库根目录下的普通音乐文件，不允许跨目录、不允许路径穿越。

**回收目录结构：**

```
{musicRoot}/
├── ...音乐文件...
└── .music-vault-trash/      # 回收目录，音乐扫描器忽略此目录
    └── {musicId}/
        └── {originalFileName}
```

**设计要点：**

- 数据库记录 `deletedAt`（删除时间）、`trashPath`（回收目录路径）、`originalPath`（恢复目标原路径）、`deleteStatus`（`active` / `trashed` / `deleted`）
- 删除前校验文件真实路径位于配置的音乐库根目录内，且必须是普通文件
- 已在 `.music-vault-trash` 下的文件拒绝再次删除
- 回收目录由后端自动创建，无需人工干预
- 恢复时校验 `trashPath` 位于 `.music-vault-trash` 且 `originalPath` 位于音乐库根目录，原路径已存在时拒绝覆盖
- 彻底删除只处理 `.music-vault-trash` 下的普通文件，数据库记录保留为 `deleteStatus = deleted`
- 音乐扫描器在遍历时忽略 `.music-vault-trash` 目录，避免回收文件再次入库

**非目标：** 批量恢复、批量彻底删除、自动清理策略、文件重命名。
