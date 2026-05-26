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
| Lyrics Service | 本地 LRC 导入、存储、歌曲绑定、歌词状态查询 | 已实现基础能力（v0.5） |
| Artwork Service | 本地封面扫描、去重、文件访问与音乐绑定 | 已实现基础能力（v0.6） |
| Match Engine | 自动匹配音乐指纹与元数据源 | 规划中 |
| Review Workflow | 人工审核工作流与状态机 | 规划中 |
| API Layer | 统一 REST 入口，Bearer Token 鉴权，异常映射；OpenAPI v0.9.0 只读 MVP（`/api/open/v1/*`） | 已实现基础能力；OpenAPI 已实现（v0.9.0） |

## 前端页面规划

| 页面 | 路由 | 状态 |
|------|------|------|
| 概览仪表盘 | `/dashboard` | 已实现（统计卡片） |
| 全部歌曲（表格/卡片视图） | `/music` | 已实现（v0.8.0） |
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
└── config/              # 挂载到容器 /app/config
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

## OpenAPI 设计（v0.9.0）

v0.9.0 为播放器客户端提供只读 OpenAPI，前缀为 `/api/open/v1`。本版本是 MVP，不包含音频流、客户端元数据修改或独立的 OAuth/JWT 认证（使用后端全局 Bearer Token）。

**设计目标：** 让播放器客户端能够查询星语音库的音乐库元数据（曲目、歌词、封面、歌手、专辑），并通过本地音乐匹配接口建立客户端与服务端的关联。

**技术实现：**

- OpenAPI  routes 独立于管理后台 API（`/api/*`），避免管理后台鉴权与客户端鉴权耦合
- 所有 OpenAPI 接口均为只读 GET，不提供写操作
- 封面和歌词按需拉取：列表接口返回 `artworkUrl`（相对路径），正文在单独接口获取
- `sync/state` 接口返回 `lastUpdatedAt`（活跃歌曲 track_files.updatedAt 与关联 tracks.updatedAt 的最大值），客户端通过比对该值判断音乐库是否变化，避免重复拉取

**客户端接入建议（按优先级）：**

1. `GET /api/open/v1/server/info` — 启动时验证服务可用性
2. `GET /api/open/v1/sync/state` — 判断是否需要重新拉取
3. `GET /api/open/v1/tracks` — 分页拉取曲目列表
4. `GET /api/open/v1/tracks/{id}/lyrics` — 按需获取歌词
5. `GET /api/open/v1/tracks/{id}/artwork` — 按需获取封面
6. `GET /api/open/v1/match/track` — 本地音乐与服务端元数据关联

**v0.9.0 暂不支持：** 音频流播放、客户端修改元数据、上传音乐、OAuth/JWT 认证、复杂限流、WebSocket 推送。

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
