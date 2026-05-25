# 更新日志

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

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

- OpenAPI（v0.9.x）
- 网络刮削元数据
- AI 元数据补全
- 全库同步 / 全库回滚
- 按歌手一键同步 / 按歌手一键回滚
- 高级字段（albumArtist/year/genre/trackNumber）同步

### 后续版本规划

- v0.9.x：客户端 OpenAPI 开发（v0.8.6 / v0.8.7 收口后开始）
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
