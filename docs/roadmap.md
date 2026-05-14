# 开发路线图

## v0.1 — 后端骨架与 Track CRUD

> 阶段目标：搭建 Quarkus 项目骨架，实现 Track CRUD、扫描任务调度框架。

- [x] Quarkus 项目初始化
- [x] SQLite 数据库集成
- [x] REST API 骨架（含健康检查）
- [x] Bearer Token 鉴权
- [x] Track CRUD（创建/查询/更新/删除）
- [x] 扫描任务实体与 CRUD（仅 pending 状态，不含实际扫描）

**本阶段不包含**：音乐文件扫描、元数据提取、歌词抓取、封面刮削、歌手/专辑管理。

## v0.2 — 音乐库扫描

> 阶段目标：完成本地音乐文件扫描和 `track_files` 记录，为后续元数据识别打基础。

- [x] 音乐目录递归遍历
- [x] 音频文件扩展名识别（mp3/flac/wav/m4a/aac/ogg/opus）
- [x] 一曲多文件基础表（track_files）
- [x] 重复扫描按 `file_path` 更新，不重复插入
- [x] 扫描任务状态与统计字段更新

**本阶段不包含**：音频内嵌元数据提取、歌词抓取、封面刮削、MusicBrainz/LRCLIB 等外部网络调用、音频指纹、登录系统、复杂权限系统。

## v0.2.1 — 扫描稳定性与前端体验增强

> 阶段目标：在 v0.2 基础上补充扫描任务健壮性与前端易用性。

- [x] ScanJob 分页查询与状态筛选（`page`/`size`/`status`）
- [x] TrackFile 分页查询（`page`/`size`/`ext`/`keyword`）
- [x] 扫描任务重复运行保护（`running`/`completed` 状态任务返回 `409 Conflict`）
- [x] 扫描路径安全校验（禁止危险根目录、路径穿越、扫描目录外路径）
- [x] 前端 Token 设置页面（存储于 localStorage）
- [x] 前端扫描任务页面与文件列表页面体验增强

**本阶段不包含**：音频内嵌元数据提取（ffprobe）、歌词抓取、封面刮削、联网匹配、音频指纹、登录系统。

## v0.3 — 本地音乐扫描与入库

> 阶段目标：完成本地音乐文件扫描入库闭环，提供音乐列表和详情查询。

- [x] POST /api/music/scan：快捷扫描入口（异步，后台执行）
- [x] GET /api/music?page=0&size=20：音乐分页列表
- [x] GET /api/music/{id}：音乐详情
- [x] 文件名元数据兜底解析（`Artist - Title` 格式）
- [x] 重复扫描跳过未变化文件（size + lastModifiedTime，1秒容差）
- [x] 默认音乐扫描目录 /Users/wangjiqing/Project/Musics/Music

**本阶段不包含**：ffprobe 音频内嵌元数据提取、歌词抓取、封面刮削、联网匹配。

## v0.4 — 前端音乐列表页 MVP

> 阶段目标：前端音乐库列表页面，支持扫描触发和分页浏览。

- [x] 音乐列表页（`/music`）
- [x] 触发扫描按钮（调用 `POST /api/music/scan`）
- [x] 刷新列表按钮
- [x] 分页浏览（`el-pagination`，支持切换每页条数）
- [x] 加载中 / 空状态 / 错误提示

**本阶段不包含**：音乐详情页、批量操作、搜索过滤。

## v0.5 — 歌词管理

> 阶段目标：本地 LRC 导入、存储、歌曲绑定与歌词状态查询。

- [x] 歌词数据模型（lyrics / song_lyrics）
- [x] 本地 LRC 扫描导入
- [x] 内容 hash 去重
- [x] 基于标题/歌手的初步自动匹配
- [x] 音乐列表返回 `lyricStatus` / `lyricId`
- [x] `GET /api/songs/{songId}/lyrics`
- [x] 前端歌词状态展示（BOUND / NO_LYRIC / UNMATCHED / PARSE_FAILED / MISSING_FILE）
- [x] 前端歌词扫描触发与结果反馈
- [x] 前端 LRC 原文查看弹窗
- [ ] 在线歌词抓取（后续）
- [ ] 歌词多版本管理（后续）
- [ ] 歌词审核工作流（后续）

## v0.6 — 封面管理

> 阶段目标：封面刮削、存储、裁剪。

- [ ] 封面数据模型（artworks）
- [ ] 封面刮削服务（外部源）
- [ ] 封面存储与缓存
- [ ] 封面审核工作流

## v0.7 — Web 管理后台完善

> 阶段目标：Vue 3 管理界面，完成核心页面。

- [x] Vue 3 + TypeScript 项目初始化
- [x] 管理后台页面路由骨架
- [x] 音乐库列表页（v0.4 + v0.5 歌词状态）
- [x] 扫描任务页面
- [x] 系统设置页面（Token 配置）
- [ ] 概览仪表盘
- [ ] 歌词管理页面（独立页，含编辑/多版本）
- [ ] 封面管理页面
- [ ] 待审核工作流页面

## v0.8 — 客户端 API

> 阶段目标：完善对外 RESTful API，支持第三方客户端。

- [ ] 完整 REST API 实现（见 docs/api.md）
- [ ] 分页、搜索、排序
- [ ] API 文档（OpenAPI）
- [ ] 速率限制

## v1.0 — 开源发布稳定版

> 阶段目标：代码整理、文档完善、开源发布。

- [ ] PostgreSQL 生产适配
- [ ] 完整单元测试
- [ ] Docker 镜像构建
- [ ] README 与部署文档完善
- [ ] 开源许可与发布
