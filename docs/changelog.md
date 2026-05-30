# 更新日志

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## v0.9.3 — 打包部署与 Docker 基础验证

**发布日期：** 2026-05-30

v0.9.3 确认星语音库后端的 Maven 打包、独立运行方式，并完成 Dockerfile、Compose 示例和部署文档同步。本版本不做星语音乐盒真实联调、不做公网 HTTPS、不做 CI/CD、不做正式 NAS 实机部署硬验收。

### 新增 / 补强

- **后端 JVM 打包方式确认**：后端通过 `cd backend && mvn package` 打包，产物位于 `backend/target/quarkus-app`，独立启动命令为 `java -jar target/quarkus-app/quarkus-run.jar`
- **Dockerfile 完善**：`backend/Dockerfile` 使用 JRE 21 运行环境，复制 Quarkus JVM 打包产物，不内置本地音乐文件或 SQLite 运行数据，默认监听 `8080`
- **Docker 构建上下文收敛**：新增 `backend/.dockerignore`，保留 Docker build 必需的 `target/quarkus-app`，排除源码、运行数据、日志和本地目录
- **Docker 基础验证通过**：镜像 `xingyu-music-vault:v0.9.3-verify` 可构建并启动，容器内服务监听 `8080`，宿主机端口映射后 `/api/open/v1/server/info` 和 `/api/open/v1/sync/state` 可访问
- **Compose 示例完善**：`deploy/docker-compose.yml` 支持端口映射、数据目录挂载、日志目录挂载、音乐目录只读挂载、OpenAPI auth / rate-limit / access-log 环境变量、时区和 `unless-stopped` 重启策略
- **本地联调 Compose 模板**：新增 `deploy/debugging-docker-compose.example.yml`，用于 Mac mini / 本机 Docker 局域网联调；真实本机路径文件应保存为 `deploy/debugging-docker-compose.local.yml` 并保持忽略
- **本地联调部署说明**：部署文档补充本地联调模式与正式 Docker / NAS 部署模式的区别，并记录星语音乐盒局域网联调已验证的只读 OpenAPI 接口范围
- **部署文档同步**：补充 Maven 打包、Jar 启动、Docker build、Compose 启动、NAS 目录建议、OpenAPI baseUrl 和验证接口说明
- **OpenAPI 服务版本同步**：`/api/open/v1/server/info` 的 `serviceVersion` 更新为 `0.9.3`

### 暂不支持

- 星语音乐盒真实联调
- 公网 HTTPS / 反向代理生产化配置
- CI/CD、自动镜像发布、多架构镜像发布
- Kubernetes 部署
- 正式 NAS 实机部署硬验收
- 音频 stream、客户端写入元数据

---

## v0.9.2 — OpenAPI 安全与访问控制

**发布日期：** 2026-05-28

v0.9.2 只补强 `/api/open/v1/*` 的安全与访问控制能力。本版本不做星语音乐盒真实联调，不提供音频流，不提供客户端元数据写入。

### 新增 / 补强

- **可选 OpenAPI API Token 认证**：通过 `xingyu.openapi.auth.enabled` 开启，仅作用于 `/api/open/v1/*`；支持 `Authorization: Bearer <token>` 和 `X-Xingyu-Api-Token`
- **简单 IP 限流**：通过 `xingyu.openapi.rate-limit.enabled` 开启，默认每 IP 每分钟 120 次；优先使用 `X-Forwarded-For` 第一个 IP，其次 `X-Real-IP`
- **OpenAPI 访问日志**：默认开启，记录 method、path、status、durationMs、clientIp、traceId；不会记录 Authorization、`X-Xingyu-Api-Token` 或 token
- **错误码完善**：OpenAPI 错误码统一使用 `OPENAPI_*` 前缀，新增 `OPENAPI_UNAUTHORIZED`、`OPENAPI_RATE_LIMITED`、`OPENAPI_CONFIG_ERROR` 等

### 暂不支持

- 星语音乐盒真实联调
- 音频流播放
- 客户端写入元数据
- 分布式限流 / Redis 限流
- 复杂代理信任链
- 反向代理部署测试

---

## v0.9.1 — 客户端缓存与增量同步增强

**发布日期：** 2026-05-27

v0.9.1 在 v0.9.0 只读 OpenAPI 基础上补强客户端缓存、增量同步和资源变化判断能力。本版本仍不提供音频流、客户端修改、上传、写回或扫描触发能力。

### 新增 / 补强

- **同步状态版本化**：新增 `openapi_library_state`，`GET /api/open/v1/sync/state` 返回 `libraryVersion`（版本号）、`lastChangedAt`（最近一次 OpenAPI 变更日志时间）、`changesAvailable`（是否支持增量同步，v0.9.1 固定为 true）；保留 `lastUpdatedAt` 用于兼容旧客户端
- **增量变更日志**：新增 `openapi_sync_change_log` 和 `GET /api/open/v1/sync/changes`，支持 `sinceVersion`、`limit`，按 `version` 升序返回变更
- **变更记录服务**：歌曲新增、删除、元数据更新、歌词更新、封面更新时记录 OpenAPI change log，并递增 `libraryVersion`
- **歌词缓存判断**：`/tracks/{id}/lyrics/meta` 返回 `sha256:` hash 和 ETag；`/tracks/{id}/lyrics` 支持 `If-None-Match`，命中时返回 `304 Not Modified`
- **封面缓存判断**：`/tracks/{id}/artwork/meta` 返回基于图片二进制内容的 `sha256:` hash 和 ETag；`/tracks/{id}/artwork` 支持 `If-None-Match`，命中时返回 `304 Not Modified`
- **updatedAfter 语义明确**：`GET /api/open/v1/tracks?updatedAfter=...` 使用严格大于语义，即 `updatedAt > updatedAfter`

### 客户端接入建议

- 客户端启动后优先读取 `/sync/state` 的 `libraryVersion`
- 本地已有版本时调用 `/sync/changes?sinceVersion=<localVersion>`，根据 `changedFields` 决定刷新曲目、歌词或封面缓存
- 对歌词和封面正文请求携带本地保存的 ETag，命中 `304` 时复用本地缓存
- `hasMore=true` 时继续以最后一条变更的 `version` 作为下一次 `sinceVersion`

---

## v0.9.0 — OpenAPI MVP

**发布日期：** 2026-05-26

v0.9.0 是星语音库的 OpenAPI 首个版本，为播放器客户端提供面向服务的只读 API。本版本不包含音频流能力，不支持客户端修改元数据或上传音乐。

### 新增 / 补强

新增 `/api/open/v1/*` 只读 OpenAPI 前缀，提供以下只读接口：

- **服务信息**：`GET /api/open/v1/server/info` — 服务版本、API 版本、readOnly 标识、功能特性映射（features）
- **音乐库状态**：`GET /api/open/v1/sync/state` — 返回 `lastUpdatedAt`、trackCount、artistCount、albumCount、lyricsCount、artworkCount（本接口不返回 lastScanAt）
- **曲目列表**：`GET /api/open/v1/tracks` — 支持分页（page/pageSize）、关键词搜索、多维过滤（metadataStatus/lyricsStatus/artworkStatus）、排序
- **曲目详情**：`GET /api/open/v1/tracks/{id}` — 单曲元数据（不含 filePath）
- **歌词查询**：`GET /api/open/v1/tracks/{id}/lyrics` — 歌词原文（按需拉取）
- **歌词元数据**：`GET /api/open/v1/tracks/{id}/lyrics/meta` — 歌词元数据（available、format、hash，不含 language/hasTimeTag）
- **封面查询**：`GET /api/open/v1/tracks/{id}/artwork` — 封面图片（二进制流），不含 base64
- **封面元数据**：`GET /api/open/v1/tracks/{id}/artwork/meta` — 封面元数据（宽高、MIME、文件大小、hash，不含 fileName）
- **歌手列表**：`GET /api/open/v1/artists` — 歌手聚合列表（全量返回，无分页，按歌手名升序）
- **歌手曲目**：`GET /api/open/v1/artists/{artistName}/tracks` — 指定歌手的曲目列表（分页）
- **专辑列表**：`GET /api/open/v1/albums` — 专辑聚合列表（全量返回，无分页，按专辑名升序）
- **专辑曲目**：`GET /api/open/v1/albums/tracks?album=...&artist=...` — 指定专辑的曲目列表（分页，参数为 album/artist，非 albumKey/artistKey）
- **本地匹配**：`GET /api/open/v1/match/track?title=...` — 将客户端本地音乐与服务端元数据关联（只有 title 必填，artist/album/durationMs 选填，返回 score/reason）

### 客户端接入建议

- **启动时先调用 `/api/open/v1/server/info`**：获取服务版本和 API 版本，验证服务端兼容性
- **再调用 `/api/open/v1/sync/state`**：获取 `lastUpdatedAt`（活跃歌曲 track_files.updatedAt 与关联 tracks.updatedAt 的最大值），与本地缓存的值比对，若未变化可跳过全量拉取
- **歌曲列表分页拉取**：使用 page + pageSize 参数，按需翻页，不要一次拉取全量
- **歌词和封面按需拉取**：不要在列表接口中加载歌词和封面正文，按需调用单独的歌词/封面接口
- **封面不要使用 base64**：封面接口返回文件 URL，客户端按 URL 获取图片，不要将封面 data URL 嵌入列表项
- **客户端本地缓存 lyrics/artwork**：歌词和封面接口支持 ETag / Last-Modified 缓存，客户端应缓存已拉取结果并利用条件请求
- **使用 match/track 将本地音乐与服务端元数据关联**：客户端持有本地音乐文件时，通过 match/track 查询服务端对应曲目 ID，建立关联后再使用其他接口

### 暂不支持

v0.9.0 是只读 MVP，以下能力在当前版本不提供：

- 音频流播放（`/api/open/v1/tracks/{id}/stream` 等）
- 客户端修改元数据（`PUT /api/open/v1/tracks/{id}` 等）
- 上传音乐（文件上传接口）
- OAuth / JWT 认证（当前使用后端全局 Bearer Token，不需要 OAuth/JWT）
- 复杂限流
- WebSocket 推送（实时同步）
- 客户端批量修改元数据
- 远程扫描音乐库
- 网络刮削元数据
- AI 元数据补全

### 后续版本规划

- 音频流播放接口（`GET /api/open/v1/tracks/{id}/stream`）
- 客户端元数据修改接口
- 音乐库变更 WebSocket 推送
- 星语音乐盒真实联调（客户端集成测试）

> **v0.9.2 已实现：** 基础鉴权（简单 token 验证）、基础限流（简单 IP 限流）。

---

## v0.8.7 — v0.8 功能冻结与回归测试

**发布日期：** 2026-05-25

v0.8.7 是 v0.8 功能冻结与回归测试版本，不新增大功能。主要验证 v0.8 音乐化浏览体验的完整链路，确保元数据同步与回滚能力稳定可用。

### v0.8 功能完成确认

本版本确认 v0.8 已完成以下全部功能：

- 全部歌曲卡片视图（v0.8.0）
- 歌手列表（v0.8.1）
- 歌手详情（v0.8.2）
- 专辑列表（v0.8.3）
- 专辑详情（v0.8.3）
- 元数据提取与同步（v0.8.4）
- 元数据审计与回滚（v0.8.5 / v0.8.6）

### 新增 / 补强

- **批量操作上限明确为 100 条**：批量同步（`apply-file-to-db` / `apply-db-to-file`）和批量回滚（`/rollback`）每次最多 100 条，超出返回 `400 Bad Request`
- **回滚行为明确**：回滚是基于历史快照执行一次新的同步操作，不是删除历史。回滚同样会生成新的审计记录（`rollbackOfAuditId` 指向前序记录），形成操作链，可追溯

### 风险说明

- **数据库写回音频文件会修改本地音频文件**。`db_to_file`（数据库 → 文件 Tag）直接修改本地音频文件，执行前应确认文件已备份。
- **文件 Tag 回滚会修改本地文件**。回滚操作同样是同步操作，会将数据库中的历史快照写回音频文件。
- **覆盖/回滚前应确认差异预览**。所有覆盖操作和回滚操作都需要预览和用户确认。
- 不支持全库同步、不支持全库回滚、不支持按歌手一键同步/回滚。如需大规模同步，建议分批调用，每批不超过 100 条。

### 暂不支持

- 网络刮削元数据
- AI 元数据补全
- 全库同步 / 全库回滚
- 按歌手一键同步 / 按歌手一键回滚
- 高级字段（albumArtist/year/genre/trackNumber）同步
- 音频流播放（OpenAPI v0.9.x）
- 客户端修改元数据（OpenAPI v0.9.x）

### 后续版本规划

- 音频流播放接口
- 客户端元数据修改接口
- 基础鉴权（简单 token 验证）
- 基础限流
- 网络刮削元数据（MusicBrainz 等在线元数据源）
- AI 元数据补全

---

## v0.8.6 — 元数据同步稳定性修复与边界收敛

**发布日期：** 2026-05-25

v0.8.6 是**稳定性修复版本**，不新增大功能。主要补充参数校验强化、边界限制明确、异常提示优化和风险说明。

### 新增 / 补强

- **批量元数据同步最多 100 条**：批量同步（`apply-file-to-db` / `apply-db-to-file`）传入超过 100 个 musicId 时返回 `400 Bad Request`，拒绝执行
- **批量元数据回滚最多 100 条**：批量回滚（`/rollback`）传入超过 100 个 auditId 时返回 `400 Bad Request`，拒绝执行
- **回滚规则明确**：审计记录可回滚状态为 `SUCCESS` 且 `rollbackStatus = NOT_ROLLED_BACK` 的记录；失败记录（`status = FAILED`）、已回滚记录（`rollbackStatus = ROLLED_BACK`）、ROLLBACK 记录（`rollbackOfAuditId != null`）不可再次回滚

### 优化

- **参数校验强化**：`musicIds` / `auditIds` 传入空数组、空值、重复值时返回明确的 `400 Bad Request` 说明；回滚接口（`POST .../rollback`）必须传入 `confirm: true` 才会执行，否则返回 `400`
- **异常提示优化**：文件不存在、文件不可写、Tag 读写失败等场景返回更明确的错误信息，不静默失败
- **危险操作确认文案**：数据库写回文件（`db_to_file`）和回滚操作在执行前必须有预览和用户确认步骤；UI 展示明确的风险提示

### 风险说明

- **数据库写回音频文件会修改本地音频文件**。`db_to_file`（数据库 → 文件 Tag）直接修改本地音频文件，执行前应确认文件已备份。v0.8.5 已提供审计历史与回滚能力。
- **回滚是基于历史快照执行一次新的同步操作，不是删除历史**。回滚同样会生成新的审计记录（`rollbackOfAuditId` 指向前序记录），形成操作链，可追溯。
- 不支持全库同步、不支持全库回滚、不支持按歌手一键同步/回滚。如需大规模同步，建议分批调用，每批不超过 100 条。

### 暂不支持

- 网络刮削元数据
- AI 元数据补全
- 全库同步 / 全库回滚
- 按歌手一键同步 / 按歌手一键回滚
- 高级字段（albumArtist/year/genre/trackNumber）同步
- 音频流播放（OpenAPI v0.9.x）
- 客户端修改元数据（OpenAPI v0.9.x）

### 后续版本规划

- 音频流播放接口
- 客户端元数据修改接口
- 基础鉴权（简单 token 验证）
- 基础限流
- 高级字段同步（albumArtist/year/genre/trackNumber）
- 封面写入音频文件
- 网络刮削元数据（MusicBrainz 等在线元数据源）
- AI 元数据补全

---

## v0.8.5 — 元数据同步审计与回滚基础能力

**发布日期：** 2026-05-24

### 新增

- 审计历史接口（`GET /api/music/metadata/audits`）：支持分页、方向筛选、状态筛选
- 审计详情接口（`GET /api/music/metadata/audits/{auditId}`）：查看完整操作快照
- 单条回滚接口（`GET /api/music/metadata/audits/{auditId}/rollback-preview` 预览 + `POST` 执行）
- 批量回滚接口（`POST /api/music/metadata/audits/rollback-preview` 预览 + `POST /api/music/metadata/audits/rollback` 执行）

### 调整

- 元数据同步范围收敛为歌曲名（title）、歌手（artist）、专辑（album）三个核心字段
- `file_to_db` 方向：仅同步 title/artist/album，`applySnapshotToTrack` 不再写入 albumArtist/year/genre/trackNumber
- `db_to_file` 方向：仅同步 title/artist/album 到音频文件 Tag
- 差异比较（`diffs` 接口）仅返回 title/artist/album 的差异，不比较高级字段
- `MetadataCompareSnapshot` 仅包含 title/artist/album，与实际同步范围一致

### 暂不支持

- 专辑歌手、年份、流派、音轨号等高级字段同步（不同来源文件的可用性和含义存在差异，暂不纳入）
- 封面写入音频文件
- 网络/AI 刮削元数据自动同步
- 整库同步、按歌手一键全量同步

### 后续版本规划

- 高级字段同步（在 Tag 读取兼容性增强后，可考虑引入 albumArtist/year/genre/trackNumber 同步）
- 审计记录回滚 UI
- 网络刮削元数据（接入 MusicBrainz 等在线元数据源，复用现有同步能力写回数据库）
- AI 元数据补全
- 封面写入音频文件

---

## v0.8.4 — 元数据提取与同步增强

**发布日期：** 2026-05-23

### 新增

- 音频文件内嵌 Tag 读取（`AudioMetadataService`，基于 `jaudiotagger`，支持 mp3/flac/wav/m4a/aac/ogg/opus）
- 数据库与音频文件 Tag 差异比较接口（`GET /api/music/{id}/metadata/compare`）
- 文件 Tag 覆盖数据库接口（`POST /api/music/{id}/metadata/apply-file-to-db`）
- 数据库元数据写回音频文件 Tag 接口（`POST /api/music/{id}/metadata/apply-db-to-file`）
- 批量差异比较接口（`POST /api/music/metadata/compare`，最多 100 条）
- 批量同步接口（`POST /api/music/metadata/apply-file-to-db` / `POST /api/music/metadata/apply-db-to-file`，最多 100 条）
- 新增 `tracks.metadataExtractedAt` 和 `tracks.metadataSource` 字段，记录元数据来源与提取时间
- 审计表 `music_metadata_sync_audit`：记录每次覆盖操作的完整快照（覆盖前数据库状态、文件状态、覆盖后状态、变更字段），用于后续回滚

### 风险提示

**数据库写回音频文件会修改本地音频文件。执行前应确认文件已备份。v0.8.5 已提供审计历史页面与回滚能力。**

`db_to_file`（数据库 → 文件 Tag）仅支持 jaudiotagger 3.0.1 已验证写入格式：mp3、flac、m4a、ogg（oga）、wav。aac 和 .opus 文件不支持写入，操作会返回明确失败，不会损坏文件。

### 后续版本规划

- 元数据回滚 UI（基于 `music_metadata_sync_audit` 记录回滚）
- 音频流播放接口
- 客户端元数据修改接口
- 基础鉴权（简单 token 验证）
- 基础限流
- 网络刮削元数据（接入 MusicBrainz 等在线元数据源）
- AI 元数据补全
- 封面写入音频文件
- 整库同步
- 按歌手一键全量同步
- 真实 album / artist 实体表

> **v0.8.5 已实现：** 审计历史页面（`GET /api/music/metadata/audits`）、单条回滚、批量回滚预览与执行。审计表结构已预留在 v0.8.4 中。高级字段（albumArtist/year/genre/trackNumber）同步暂不纳入，后续可考虑。

---

**发布日期：** 2026-05-22

### 新增

- 专辑列表接口 `GET /api/music/albums`
- 专辑详情接口 `GET /api/music/albums/detail?albumKey=...&artistKey=...`
- 歌曲列表支持按 `albumKey` + `artistKey` 组合过滤
- 专辑浏览页面 `/albums`
- 专辑详情页面 `/albums/detail`
- 专辑详情展示统计概览：专辑名、歌手、专辑歌手、专辑年份（取分组内最早年份）、歌曲数、歌词数、封面数、待整理数量
- 专辑详情页面通过 `GET /api/music?albumKey=...&artistKey=...` 获取该专辑曲目列表（含歌曲名称、歌手、时长、歌词状态、封面状态）
- 歌手详情页专辑分组支持点击跳转到对应专辑详情页

### 后续版本规划

- 专辑封面自动抓取
- 专辑元数据批量编辑
- 专辑别名合并
- 多歌手拆分
- 播放器能力

---

## v0.8.2 — 歌手详情页

**发布日期：** 2026-05-21

### 新增

- 歌手详情接口 `GET /api/music/artists/{artistKey}`
- 支持通过 URL-safe 编码的 `artistKey` 查询歌手详情
- 歌手详情展示统计概览：歌手名称、歌曲数、专辑数、歌词数量、封面数量、待整理数量
- 歌手详情展示该歌手专辑分组（按专辑名聚合，含每张专辑的曲目数、歌词数、封面数、待整理数量）
- 歌手详情专辑分组中包含专辑年份信息

---

## v0.8.1 — 歌手列表页

**发布日期：** 2026-05-21

### 新增

- 歌手聚合列表接口 `GET /api/music/artists`
- 歌手浏览页面 `/artists`
- 歌手搜索（按歌手名模糊搜索）
- 歌手排序（按歌曲数、专辑数、歌词数、封面数、待整理数量）
- 分页浏览（每页条数可配置）
- 歌手卡片展示歌曲数、专辑数、歌词数量、封面数量、待整理数量

### 后续版本规划

- v0.8.x 将实现专辑浏览视图（按专辑聚合）

---

## v0.8.0 — 导航瘦身与歌曲卡片视图

**发布日期：** 2026-05-20

### 新增

- 全部歌曲页面支持表格视图和卡片视图，可随时切换
- 卡片视图展示封面缩略图、歌名、歌手、专辑、时长
- 无封面时卡片显示默认占位图，不留空白

### 调整

- 左侧菜单「音乐库」更名为「全部歌曲」
- 移除「待处理队列」「扫描任务」「音乐文件」三个菜单入口（路由仍保留，原地隐藏）
- 左侧导航预留「歌手」「专辑」入口，暂显示空状态提示（功能后续版本实现）

### 后续版本规划

- v0.8.x 将实现歌手浏览视图（按歌手聚合）
- v0.8.x 将实现专辑浏览视图（按专辑聚合）

---

## v0.7.4 — 批量整理与待整理筛选

**发布日期：** 待补充

### 新增

- 音乐列表支持多选（勾选）
- 批量编辑共同元数据字段：artist / album / year / genre
- 支持按「待整理」「已整理」状态筛选歌曲
- 保存前提示本次操作将影响多少首歌曲

### 修复

- year 字段增加范围校验（1900–当前年份+1）

### 变更

- title 和 trackNo 不支持批量编辑（建议单首编辑）

---

## v0.7.3 — 管理后台 UI 体验优化

**发布日期：** 待补充

### 新增

- 概览统计卡片：总歌曲数、元数据不完整数、歌词就绪数、封面就绪数、回收站文件数
- 工具栏快捷操作入口
- 列表空状态友好提示
- 操作失败错误提示优化

### 调整

- 音乐列表列宽、字段排布、状态标签颜色优化

---

## v0.7.2 — 文件管理、安全删除与回收站

**发布日期：** 待补充

### 新增

- 查看单首音乐文件信息（路径、大小、删除状态）
- 安全删除：移动文件到 `.music-vault-trash/` 回收目录
- 回收站列表：查看已删除音乐
- 恢复：还原文件到原路径
- 彻底删除：永久删除回收站文件

### 约束

- 删除操作仅限音乐库根目录下的普通文件，不允许跨目录
- 回收站文件不会被扫描器重新入库

---

## v0.7.1 — 音乐元数据管理最小闭环

**发布日期：** 待补充

### 新增

- 基础元数据字段：title / artist / album / year / trackNo / genre
- 元数据编辑弹窗（展示 + 编辑 + 保存）
- title 为空时使用文件名兜底

---

## v0.6.2 — 封面能力稳定性与体验收口

**发布日期：** 待补充

### 新增

- 封面文件缺失时明确提示（fileExists = false）
- artworks 接口支持 boundStatus 筛选
- 图片加载失败时兜底展示

---

## v0.6.1 — 封面导入/选择/绑定体验优化

**发布日期：** 待补充

### 新增

- 支持从本地导入图片并立即绑定到歌曲
- 文件命名：`歌手 - 歌名.ext`，特殊字符自动清洗
- SHA-256 哈希去重，重复封面复用已有记录
- 10MB 文件大小限制，图片可读性三重校验

---

## v0.6 — 本地封面管理

**发布日期：** 待补充

### 新增

- 本地封面目录扫描（jpg/jpeg/png/webp）
- SHA-256 哈希去重
- 封面文件访问接口
- 按文件名自动绑定封面到音乐
- 手动绑定/解绑音乐主封面

---

## v0.5.2 — 歌词管理页 MVP

**发布日期：** 待补充

### 新增

- 前端歌词管理页面（歌词列表 + 详情查看）
- 歌词扫描触发与结果反馈
- LRC 原文查看弹窗
- 歌词状态展示：BOUND / NO_LYRIC / UNMATCHED / PARSE_FAILED / MISSING_FILE

---

## v0.5 — 歌词管理后端基础能力

**发布日期：** 待补充

### 新增

- 歌词数据模型（lyrics / song_lyrics）
- 本地 LRC 扫描导入
- 内容 hash 去重
- 基于标题/歌手的初步自动匹配

---

## v0.4 — 前端音乐列表页 MVP

**发布日期：** 待补充

### 新增

- 音乐列表页（`/music`）
- 触发扫描按钮
- 刷新列表按钮
- 分页浏览（支持切换每页条数）
- 加载中 / 空状态 / 错误提示

---

## v0.3 — 本地音乐扫描与入库

**发布日期：** 待补充

### 新增

- POST /api/music/scan：快捷扫描入口（异步）
- GET /api/music：音乐分页列表
- GET /api/music/{id}：音乐详情
- 文件名元数据兜底解析（`Artist - Title` 格式）
- 重复扫描跳过未变化文件（size + lastModifiedTime）

---

## v0.2.1 — 扫描稳定性与前端体验增强

**发布日期：** 待补充

### 新增

- ScanJob 分页查询与状态筛选
- TrackFile 分页查询
- 扫描任务重复运行保护（running/completed 状态返回 409）
- 扫描路径安全校验
- 前端 Token 设置页面

---

## v0.2 — 音乐库扫描

**发布日期：** 待补充

### 新增

- 音乐目录递归遍历
- 音频文件扩展名识别（mp3/flac/wav/m4a/aac/ogg/opus）
- 一曲多文件基础表（track_files）
- 重复扫描按 file_path 更新，不重复插入
- 扫描任务状态与统计字段更新

---

## v0.1 — 后端骨架与 Track CRUD

**发布日期：** 待补充

### 新增

- Quarkus 项目骨架
- SQLite 数据库集成
- REST API 骨架（含健康检查）
- Bearer Token 鉴权
- Track CRUD（创建/查询/更新/删除）
- 扫描任务实体与 CRUD
