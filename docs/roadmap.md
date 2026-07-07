# 开发路线图

## 当前阶段：v1.3.3 — 歌词待办与覆盖率看板已完成

> 阶段目标：在既有歌词草稿、逐字对齐任务和歌词工作台基础上，让系统主动识别缺少 SWLRC 的歌曲，展示覆盖率，并引导用户进入正确的歌词工作流。

v1.3.3 已完成歌词覆盖率看板、每日稳定推荐、随机挑选待处理歌曲、歌曲列表歌词状态 Badge 和状态筛选。推荐优先面向缺少 SWLRC 的歌曲，会区分已有 LRC 与完全无歌词，不会自动创建大量歌词任务。

v1.3.2 仍限定个人自托管、本地 / 局域网可信使用为主；如需公网访问，继续沿用 HTTPS 反向代理、管理端登录和 OpenAPI HMAC 安全边界。Brave Search 仅用于候选来源发现，不抓取、不下载、不缓存第三方歌词网页全文。

## v1.3.4 / v1.4.0 — 后续收口方向

优先方向：

- 对接 `xingyu-lyrics-aligner` 后续真实 `progress.json` 协议：Worker 输出真实进度，音库读取真实状态。
- 在音库端展示真实 Worker 进度，并考虑 SSE 或等价实时状态推送；不显示伪进度，不估算百分比。
- 旧 `alignment-assets-dir` 历史正式对齐资产显式迁移：先复制文件、校验 hash，再更新数据库路径
- 迁移前诊断已有 `ALIGNMENT` 记录的 `source_path` / `swlrc_path` 完整性、hash 一致性和空 SWLRC 边界
- 继续保持普通扫描、删除同步与音库生成资产隔离
- 在工作台基础上继续设计元数据 / 封面确认状态与只读校验后的人工闭环
- 管理端体验收敛：降低日常整理、批量修正、状态筛选和错误恢复成本
- 音乐盒协作增强：围绕已验证的公网 HTTPS OpenAPI 链路，继续稳定客户端同步、缓存和播放相关契约
- 运维能力补强：备份、升级、诊断、日志和配置检查更清晰，减少家庭服务器长期运行的不确定性
- 数据治理增强：围绕元数据、歌词、封面和文件状态，补齐更可审计、更易回滚的整理流程

暂不把 v1.3.x 定义为托管平台、多用户系统、复杂权限中心或通用网关产品；这些能力应在个人自托管场景真正需要时再拆分设计。

## v1.3.2 — 歌词工作台体验优化

> 阶段目标：补齐手工草稿、候选来源搜索辅助、管理端逐字歌词试听和工作台可读性优化，不改变 Worker 主任务协议。

- [x] 支持跳过 Worker 草稿提取，直接粘贴歌词文本创建草稿
- [x] 手工草稿使用 `LYRIC_DRAFT_MANUAL` 和 `MANUAL_PASTE` 来源语义，不伪装为 Worker 提取结果
- [x] 手工草稿可编辑、保存、确认可信歌词，并继续创建逐字对齐任务
- [x] 新增 Brave Search 管理端配置、状态查询、启用 / 暂停、测试连接和搜索代理
- [x] 支持 `MUSIC_VAULT_BRAVE_SEARCH_API_KEY` 环境变量模式和控制台托管模式
- [x] 控制台托管 Brave Key 使用 `MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY` 服务端加密保存，不回显完整 Key
- [x] Brave 搜索结果仅作为候选来源展示，可将标题、URL、域名、查询词等元信息关联到草稿
- [x] 不抓取、不下载、不缓存第三方歌词网页全文，不把搜索摘要写入歌词正文
- [x] 管理端工作台读取 SWLRC 并逐字渲染；无 SWLRC、读取失败或解析失败时回退 LRC
- [x] 优化草稿编辑区、操作区、质量摘要、质量报告和 Worker 状态摘要布局
- [x] 保留现有任务状态轮询，不替换为 SSE

**本版本不包含**：Worker `progress.json`、真实 Worker 进度展示、伪进度或估算百分比、SSE、文件监听、第三方网页歌词自动抓取、逐字时间轴编辑器、批量全库对齐。

## v1.3.3 — 歌词待办与覆盖率看板

- [x] 首页展示歌词资产进度和覆盖率
- [x] 每日稳定推荐最多 5 首缺少 SWLRC 的歌曲
- [x] 推荐区分 `LRC_UPGRADE` 和 `NO_LYRICS`
- [x] 支持开始制作、今天跳过、换一首
- [x] 支持随机挑选 5 / 10 / 20 首待制作候选
- [x] 全部歌曲列表显示统一歌词状态 Badge
- [x] 全部歌曲列表支持按统一歌词状态筛选
- [x] 首页和列表快速跳转现有歌词工作台
- [x] 新增 `lyric_daily_recommendation` 数据表和后端测试

**本版本不包含**：AI 智能推荐、自动批量制作歌词、网络歌词抓取、播放行为推荐、永久忽略歌曲、整库一键生成歌词或第二套歌词编辑器。

## v1.3.4 — 真实 Worker 进度与实时状态规划

> 阶段目标：在 Worker 提供真实进度协议后，音库读取并展示真实任务进度。

- [ ] `xingyu-lyrics-aligner` 输出真实 `progress.json` 协议
- [ ] 音库读取真实 Worker 进度，不自行估算或模拟阶段
- [ ] 管理端展示真实任务进度
- [ ] 评估 SSE 或等价实时状态推送，作为现有轮询之上的后续增强
- [ ] 保持对无进度 Worker 的兼容降级：只展示已有真实任务状态

**边界**：不显示伪进度，不用文件监听替代协议，不把缺失进度包装成百分比。

## v1.3.1 — 正式对齐歌词资产目录收口

> 阶段目标：将审核通过后的正式对齐 LRC / SWLRC 发布到歌词挂载目录中的受控子目录，并阻止普通扫描和删除同步误处理 `ALIGNMENT` 资产。

- [x] 新增 `MUSIC_VAULT_ALIGNMENT_LYRICS_ROOT` 与 `MUSIC_VAULT_ALIGNMENT_LYRICS_SUBDIR`
- [x] 校验 `alignmentLyricsRoot` 必须是已配置歌词扫描目录之一
- [x] 审核导入发布到 `{alignmentLyricsRoot}/alignment/{songId}/{jobId}/lyrics.lrc` 与 `lyrics.swlrc`
- [x] 导入使用 staging 目录整体发布，支持 hash 一致的幂等重试并拒绝覆盖冲突文件
- [x] 普通歌词扫描排除受控 alignment 子目录
- [x] 普通扫描只复用 / 刷新 `LOCAL_FILE`，不降级 `ALIGNMENT` 或 `DRAFT_CONFIRMED`
- [x] 普通删除同步仅处理 `LOCAL_FILE`，不因 `ALIGNMENT` 文件缺失解绑当前歌词
- [x] Docker Compose 示例将 `/lyrics` 挂载为读写，并设置 `MUSIC_VAULT_ALIGNMENT_LYRICS_ROOT=/lyrics`

**本版本不包含**：旧 `alignment-assets-dir` 自动迁移、资产清理、Worker 协议变更、Docker Socket、HTTP 回调、消息队列、数据库队列、批量全库对齐、逐字时间轴编辑器或多 Worker 调度。

## v1.3.0 — 歌词草稿与逐字对齐任务闭环

> 阶段目标：完成候选歌词草稿、人工校对、可信歌词确认、逐字对齐审核和导入闭环。

- [x] 新增 `LYRIC_DRAFT_EXTRACTION` 草稿任务，Worker 生成未对齐候选歌词文本
- [x] 管理端支持边听边校对、保存草稿、恢复原始草稿、驳回和确认可信歌词
- [x] 草稿确认生成 `DRAFT_CONFIRMED` 来源可信歌词资产，不自动替换当前 LRC / SWLRC
- [x] 新增歌词对齐任务创建、共享目录输入写入、READY 最后创建和 Worker 状态同步
- [x] 接入 `xingyu-lyrics-aligner:0.4.0` Worker，支持 v1 / v2 协议和双任务类型
- [x] 管理端支持 LRC / SWLRC / report / alignment 结果预览
- [x] 对齐结果必须人工审核通过后确认导入
- [x] 导入后创建 `ALIGNMENT` 来源 LRC 记录，SWLRC 作为可选逐字歌词附加资产
- [x] Compose 示例提供音库 + Worker 双容器部署，Worker 使用 `/music:ro`、`/jobs`、`/models`，不暴露端口，不挂 Docker Socket

**本版本不包含**：在线逐字时间轴编辑器、批量全库转写、批量审核、批量导入、自动确认可信歌词、自动创建对齐任务、GPU / 多 Worker 调度、Docker Socket、HTTP 回调、消息队列或音库内置 Python / WhisperX / PyTorch。

## v1.2.4 — 歌词 source_path 幂等恢复与未绑定记录清理

> 阶段目标：修复同一路径歌词删除后恢复会重复创建记录的问题，并允许管理员清理未绑定歌词记录。

- [x] 歌词扫描优先按规范化 `source_path` 复用既有记录
- [x] 同一路径 LRC 恢复或更新后刷新正文、hash 和解析状态，并恢复绑定
- [x] 管理端允许删除真正未绑定的歌词记录，且不删除磁盘 `.lrc` 源文件

## v1.2.3 — 歌词扫描删除同步修复

> 阶段目标：让完整成功的歌词扫描同步处理已从歌词目录删除的本地 LRC。

- [x] 完整成功扫描后检查本地 `LOCAL_FILE` 源文件可用性
- [x] 源 `.lrc` 缺失时解除相关主歌词绑定
- [x] 扫描失败或目录不可访问时保留既有绑定，避免误解绑

## v1.2.2 — 歌曲工作台小屏布局修复

> 阶段目标：修复歌曲工作台在小屏、低高度和窄内容区下的溢出与裁切问题。

- [x] 工作台页面支持整体布局缩放
- [x] 播放器按组件自身宽度响应，播放控制、进度条和音量控件不再横向溢出
- [x] 歌词、封面和播放器控件跟随工作台比例缩小
- [x] 元数据和 OpenAPI 输出 Tab 支持内容区滚动
- [x] OpenAPI `server/info`、管理端服务信息和健康检查版本更新到 `1.2.2`
- [x] README、release notes、changelog、用户手册、API 说明和架构说明同步更新

**本版本不包含**：确认状态、确认按钮、AI 候选、网络刮削、歌词公网请求、音频 Tag 写回、封面写入音频文件、复杂播放队列、播放历史、播放统计和收藏系统。

## v1.2.1 — 歌曲工作台 MVP：边听边看，只读校验闭环

> 阶段目标：新增管理端只读工作台，降低人工校验成本，并为后续确认状态打基础。

- [x] 新增 `/music/workbench` 歌曲工作台页面
- [x] 从左侧导航和全部歌曲列表进入工作台
- [x] 支持选择当前歌曲、上一首 / 下一首
- [x] 集成 HTML5 audio 播放器，支持播放 / 暂停、进度展示、时间展示和进度跳转
- [x] 展示数据库元数据、歌词、封面、OpenAPI 输出预览
- [x] 新增管理端受保护音频接口，按 music id 读取已入库本地文件并支持 Range
- [x] 新增管理端 OpenAPI 输出预览接口
- [x] 保持管理端登录保护和 OpenAPI HMAC 策略不变
- [x] 文档、API 说明、架构说明和 changelog 同步更新

**本版本不包含**：确认状态、确认按钮、AI 候选、网络刮削、歌词公网请求、音频 Tag 写回、封面写入音频文件、复杂播放队列、播放历史、播放统计和收藏系统。

## v1.0.3 — 冬夜雪境与春日晨光主题接入

> 阶段目标：一次性接入冬季与春季主题素材，完成四季主题资源与基础主题切换体验闭环。

- [x] 新增 `frontend/public/themes/winter-moonlight/` 主题资源目录
- [x] 新增 `frontend/public/themes/spring-dawn/` 主题资源目录
- [x] 接入 README Banner、Logo、favicon、小图标、背景图、空状态插画和主题色板
- [x] 新增 Winter Moonlight 与 Spring Dawn 主题候选配置
- [x] 管理后台 header 轻量主题切换入口展示四套主题
- [x] 主题切换时同步更新背景、Logo、空状态图、favicon 与主题变量 CSS
- [x] 保持当前默认主题为 Midsummer Starlight，选择结果继续保存在浏览器本地
- [x] README、changelog、roadmap、用户手册和主题文档同步说明四套主题资源
- [x] 去重冬季、春季背景别名文件，使用 manifest alias 记录旧命名映射
- [x] OpenAPI `server/info` 服务版本更新到 `1.0.3`

**本版本不包含**：后端业务逻辑修改、OpenAPI 契约变更、数据库结构变更、音乐扫描/元数据/歌词/封面能力扩展、服务端主题管理系统、星语音乐盒侧主题同步。`serviceVersion` 仅同步发布版本号。

## v1.0.2 — 秋日唱片主题素材接入

> 阶段目标：接入第二套四季主题素材，验证 v1.0.1 建立的主题结构可复用。

- [x] 新增 `frontend/public/themes/autumn-vinyl/` 主题资源目录
- [x] 接入 README Banner、Logo、favicon、小图标、背景图、空状态插画和主题色板
- [x] 新增 Autumn Vinyl 主题候选配置
- [x] 管理后台 header 提供轻量主题切换入口，选择结果保存在浏览器本地
- [x] 主题切换时同步更新 favicon 与主题变量 CSS
- [x] 保持当前默认主题为 Midsummer Starlight，不扩大为服务端主题管理系统
- [x] 泛化主题 `archive/` 生产构建排除逻辑
- [x] README、changelog、主题文档同步说明两套主题资源
- [x] 去重秋季背景别名文件，使用 manifest alias 记录旧命名映射
- [x] OpenAPI `server/info` 服务版本更新到 `1.0.2`

**本版本不包含**：后端业务逻辑修改、OpenAPI 契约变更、数据库结构变更、音乐扫描/元数据/歌词/封面能力扩展、Winter Moonlight / Spring Dawn 接入、服务端主题管理系统。

## v1.0.1 — 仲夏星河主题资源试接与管理后台体验微调

> 阶段目标：低风险接入主题资源，补强管理后台视觉占位与列表滚动体验。

- [x] 主题资源目录建立
- [x] 管理后台主题化皮肤
- [x] 首页统计卡片与主题素材入口
- [x] 列表滚动体验统一
- [x] Footer 文案结构化
- [x] OpenAPI `server/info` 服务版本更新到 `1.0.1`
- [x] 构建产物排除主题 archive 备份目录

**本版本不包含**：复杂主题切换系统、后端业务逻辑重构、星语音乐盒接入、在线素材抓取。

## v1.0.0 — 首个正式稳定版本

> 阶段目标：首个正式稳定版本发布整理，不新增业务功能。

- [x] README 与文档入口最终整理
- [x] 版本历史改为倒序展示
- [x] 镜像部署示例更新到 `v1.0.0`
- [x] v1.0.0 Release Notes 准备
- [x] v1.0 发布检查清单完善
- [x] GHCR / Docker Hub 发布流程说明收口

**本版本不包含**：新业务接口、UI 大改、在线歌词抓取、在线封面刮削、AI 元数据补全、公网 HTTPS / 域名反向代理。

## v0.9.9 — Docker Hub 发布与 v1.0 开源规范收口

> 阶段目标：补齐 Docker Hub 自动发布和 v1.0 前开源发布规范。

- [x] Docker Hub 自动镜像发布
- [x] GHCR / Docker Hub 双仓库多架构镜像发布说明
- [x] Apache-2.0 LICENSE 与 NOTICE
- [x] SECURITY.md / CONTRIBUTING.md
- [x] Release Notes 模板
- [x] v1.0 发布检查清单

## v0.9.8 — GitHub Actions 与 GHCR 自动镜像发布

> 阶段目标：补齐基础 CI 与 GHCR 自动镜像发布。

- [x] GitHub Actions 基础 CI
- [x] tag `v*` 触发 GHCR 镜像发布
- [x] `workflow_dispatch` 手动发布
- [x] `linux/amd64` / `linux/arm64` 多架构发布
- [x] Actions 构建源使用官方 npm registry 与 Maven Central

## v0.9.7 — 镜像发布与 Packages 准备

> 阶段目标：为正式镜像发布准备命名、tag、部署示例和手动发布流程。

- [x] GHCR / Docker Hub 镜像命名规范
- [x] `vX.Y.Z` / `vX.Y` / `latest` tag 规则
- [x] image 模式 Compose 模板
- [x] 镜像拉取部署文档
- [x] 镜像发布说明与发布前检查清单

## v0.9.6 — Docker 一键部署与运行规范化

> 阶段目标：完成前后端一体镜像和 Docker Compose 一键部署。

- [x] 根目录前后端一体 Dockerfile
- [x] Docker Compose 部署模板
- [x] `.env.example` 环境变量模板
- [x] 数据、配置、歌词、封面和音乐目录挂载规范
- [x] Docker 部署、备份与升级文档

## v0.9.5 — OpenAPI 联调反馈收口与契约稳定

> 阶段目标：基于星语音乐盒局域网联调反馈，收敛 OpenAPI 资源状态契约。

- [x] 歌词 / 封面 available 降级语义收敛
- [x] 资源缺失和不可读状态处理
- [x] 客户端契约说明补强
- [x] OpenAPI 相关测试补强

## v0.9.4 — 星语音乐盒本地联调部署说明

> 阶段目标：记录局域网联调部署方式和已验证接口。

- [x] 本地联调 Compose 模板
- [x] 局域网访问说明
- [x] 星语音乐盒已验证 OpenAPI 接口记录

## v0.9.3 — 打包部署与 Docker 基础验证

> 阶段目标：确认后端打包、独立运行和 Docker 基础启动。

- [x] Maven package 与 Quarkus JVM 运行方式
- [x] 后端 Dockerfile
- [x] Compose 示例
- [x] 容器内健康检查与 OpenAPI 基础验证

## v0.9.2 — OpenAPI 安全与访问控制

> 阶段目标：为只读 OpenAPI 增加访问控制和运行安全能力。

- [x] 可选 API Token 认证
- [x] 简单 IP 限流
- [x] OpenAPI 访问日志
- [x] `OPENAPI_*` 错误码

## v0.9.1 — 客户端缓存与增量同步增强

> 阶段目标：增强 OpenAPI 客户端缓存判断和增量同步能力。

- [x] 音乐库版本号
- [x] 增量变更日志
- [x] 歌词 / 封面 ETag 与 hash
- [x] `updatedAfter` 查询语义说明
## v0.9.0 — OpenAPI 与外部集成基础

> 阶段目标：完善对外 RESTful API，支持第三方客户端接入。

- [x] 完整 REST API 实现（见 docs/api.md）
- [x] 分页、搜索、排序
- [x] API 文档（OpenAPI）
- [x] 速率限制

**非目标**：登录系统、OAuth、复杂权限体系。

## v0.8.7 — v0.8 功能冻结与回归测试

> 阶段目标：对 v0.8 音乐化浏览体验进行功能冻结与回归测试，确认完整链路稳定可用。

- [x] v0.8 功能回归测试（全部歌曲卡片视图、歌手列表、歌手详情、专辑列表、专辑详情、元数据同步与回滚）
- [x] 批量操作 100 条限制确认
- [x] 风险说明完整性确认
- [x] 文档封箱

**本版本不包含**：OpenAPI、新功能开发。

## v0.8.3 — 专辑浏览与专辑详情页

> 阶段目标：按专辑维度聚合歌曲，提供专辑视角浏览；歌手详情页专辑分组支持跳转专辑详情。

- [x] 专辑列表接口 `GET /api/music/albums`
- [x] 专辑详情接口 `GET /api/music/albums/detail`
- [x] 歌曲列表支持按 `albumKey` + `artistKey` 组合过滤
- [x] 专辑浏览页面 `/albums`
- [x] 专辑详情页面 `/albums/detail`
- [x] 专辑详情展示统计概览：专辑名、歌手、专辑歌手、专辑年份（取分组内最早年份）、歌曲数、歌词数、封面数、待整理数量
- [x] 专辑详情页面通过 `GET /api/music?albumKey=...&artistKey=...` 获取该专辑曲目列表
- [x] 歌手详情页专辑分组支持点击跳转到对应专辑详情页

**本版本不包含**：专辑封面自动抓取、专辑元数据批量编辑、专辑别名合并、多歌手拆分、播放器、AI 元数据补全、真实 album/artist 实体表。

**后续版本规划**：专辑封面自动抓取、专辑元数据批量编辑、专辑别名合并、多歌手拆分、播放器能力、AI 元数据补全、真实 album / artist 实体表。

## v0.8.2 — 歌手详情页

> 阶段目标：在歌手列表基础上，提供歌手详情浏览，含统计概览和专辑分组。

- [x] 歌手详情接口 `GET /api/music/artists/{artistKey}`
- [x] 歌手详情展示统计概览：歌手名称、歌曲数、专辑数、歌词数量、封面数量、待整理数量
- [x] 歌手详情展示该歌手专辑分组（按专辑名聚合，含每张专辑的曲目数、歌词数、封面数、待整理数量、年份）
- [x] 歌手详情专辑分组中包含专辑年份信息

**本版本不包含**：专辑详情页、专辑聚合视图、歌曲列表按 albumKey 过滤。

## v0.8.1 — 歌手浏览视图

> 阶段目标：按歌手维度聚合歌曲，提供歌手视角浏览。

- [x] 歌手聚合列表接口 `GET /api/music/artists`
- [x] 歌手浏览页面 `/artists`
- [x] 歌手搜索（按歌手名模糊搜索）
- [x] 歌手排序（按歌曲数、专辑数、歌词数、封面数、待整理数量）
- [x] 分页浏览（每页条数可配置）
- [x] 歌手卡片展示歌曲数、专辑数、歌词数量、封面数量、待整理数量

**本版本不包含**：歌手详情页、专辑聚合视图、播放器。

## v0.8.0 — 导航瘦身与歌曲卡片视图

> 阶段目标：左侧导航瘦身，引入歌曲卡片视图，为后续歌手/专辑浏览打基础。

- [x] 左侧菜单调整：移除「待处理队列」（`/review`）「扫描任务」（`/scan-jobs`）「音乐文件」（`/track-files`）三个菜单入口（路由仍保留）
- [x] 「音乐库」更名为「全部歌曲」
- [x] 表格视图 / 卡片视图切换
- [x] 歌曲卡片展示：封面、歌名、歌手、专辑、时长
- [x] 无封面时显示默认占位图
- [x] 卡片快捷操作：「编辑」打开元数据编辑弹窗，「歌词」查看歌词详情，更多操作提供封面管理和移入回收站
- [x] 预留「歌手」「专辑」导航入口（点击后显示空状态提示，分别在 v0.8.1 / v0.8.3 实现）

**本版本不包含**：歌手聚合视图、专辑聚合视图、播放器、在线播放流。

## v0.7.4 — 批量整理与待整理筛选

> 阶段目标：支持音乐列表多选和批量编辑共同元数据字段，支持按待整理/已整理状态筛选。

- [x] 音乐列表多选能力
- [x] 批量编辑共同元数据字段（`artist` / `album` / `year` / `genre`）
- [x] 不支持批量编辑 `title` 和 `trackNo`（建议单首编辑）
- [x] 批量编辑只更新用户填写的字段，空字段不更新
- [x] `PUT /api/music/metadata/batch` 批量更新接口
- [x] `year` 字段校验（1900–当前年份+1 之间的整数）
- [x] 待整理 / 已整理筛选（`metadata=incomplete` / `metadata=complete`）
- [x] 保存前提示影响数量

**非目标**：音频标签读取、音频标签写回、AI 补全、联网刮削、上传/批量上传、批量删除、专辑视图、歌手视图、卡片视图、播放器。

## v0.7.3 — 管理后台 UI 体验优化

> 阶段目标：Web 管理后台 UI 清爽化，减少维护负担。

- [x] 概览仪表盘（统计卡片：总歌曲数、总专辑数、封面绑定率、歌词绑定率）
- [x] 音乐列表体验优化（列宽、排序、默认排序策略）
- [x] 封面管理页面
- [x] 歌词管理页面（独立页）

## v0.7.2 — 文件管理、安全删除与回收站

> 阶段目标：支持单首音乐文件信息查看、安全删除和基础回收站管理。

- [x] `GET /api/music/{id}/file`：查看文件路径、大小、删除状态等信息
- [x] `DELETE /api/music/{id}`：安全删除，移动到音乐库根目录下 `.music-vault-trash/{musicId}/原文件名`
- [x] 删除前校验文件位于配置音乐库根目录内，拒绝目录、路径穿越和已在回收目录内的文件
- [x] `GET /api/music` 默认隐藏 `deleteStatus = trashed` 的音乐
- [x] 音乐扫描器忽略 `.music-vault-trash`，避免回收文件再次入库
- [x] `GET /api/music/trash`：查看回收站音乐记录和回收文件存在状态
- [x] `POST /api/music/{id}/restore`：从 `.music-vault-trash` 恢复到 `originalPath`
- [x] `DELETE /api/music/{id}/trash`：彻底删除回收站文件，数据库记录保留为 `deleteStatus = deleted`
- [x] `originalPath` 入库，支持安全恢复原路径

**非目标**：批量恢复、批量彻底删除、自动清理策略、上传、文件重命名、复杂回收站 UI。

## v0.7.1 — 音乐元数据管理最小闭环

> 阶段目标：音乐列表和 Web 管理后台支持基础元数据字段展示、编辑和保存。

- [x] 数据库层扩展 title / artist / album / year / trackNo / genre / metadataUpdatedAt
- [x] Flyway V7 migration 安全迁移（只增字段，不破坏旧数据）
- [x] `GET /api/music` 列表返回元数据字段
- [x] `GET /api/music/{id}` 详情返回元数据字段
- [x] `PUT /api/music/{id}/metadata` 元数据更新接口（trim、空字符串转 null、year/trackNo 校验）
- [x] title 为空时展示层使用文件名兜底，`normalizedTitle` 只基于真实 title 生成
- [x] Web 管理后台音乐列表展示元数据字段（年份、流派）
- [x] Web 管理后台「编辑元数据」弹窗（回显、保存、错误提示）
- [x] 元数据只保存到 SQLite，不读取、不写回真实音频文件标签

**非目标**：联网刮削、AI 自动补全、批量编辑、音频标签写回、上传/删除文件、播放器。

## v0.7 — Web 管理后台完善

> 阶段目标：Vue 3 管理界面，完成核心页面。

- [x] Vue 3 + TypeScript 项目初始化
- [x] 管理后台页面路由骨架
- [x] 音乐库列表页（v0.4 + v0.5 歌词状态）
- [x] 扫描任务页面
- [x] 系统设置页面（Token 配置）
- [x] 概览仪表盘
- [x] 歌词管理页面（独立页，含编辑/多版本）
- [x] 封面管理页面
- [ ] 待审核工作流页面

## v0.6.2 — 封面能力稳定性与体验收口

> 阶段目标：封面能力稳定性收口，处理边界情况和错误提示。

- [x] Artworks 目录不存在时自动创建
- [x] 封面文件缺失时 `fileExists = false`，API 文件访问返回 `404`
- [x] `/api/artworks` 支持 `boundStatus=all/bound/unbound` 筛选
- [x] `/api/artworks` 列表和详情返回 `fileExists` 字段
- [x] `/music` 列表返回 `artworkFileExists` 字段
- [x] 导入失败错误提示优化（文件过大、图片类型不支持、图片损坏、目录不可写）
- [x] 图片加载失败时兜底展示

**非目标**：URL 导入、在线封面搜索、AI 生成封面、删除未绑定封面、多版本封面。

## v0.6.1 — 封面导入/选择/绑定体验

> 阶段目标：优化封面选择与导入体验，支持从音乐列表页直接导入本地图片并立即绑定。

- [x] `POST /api/music/{musicId}/artwork/import`：上传本地图片，保存到受控 Artworks 目录，写入 artworks，立即绑定到歌曲
- [x] 文件命名：`歌手 - 歌名.ext`，特殊字符清洗、长度截断、序号追加
- [x] 按 SHA-256 hash 去重，hash 相同时复用已有 artwork 记录
- [x] 10MB 文件大小限制，扩展名 + Content-Type + 图片可读性三重校验
- [x] 替换已有主封面（无需先取消绑定）
- [x] `/artworks` 列表返回 `boundCount`，详情返回关联歌曲列表

**非目标**：URL 导入、在线封面搜索、AI 生成封面、批量审核、多版本封面。

**后续**：URL 导入（需 SSRF 防护）、在线封面搜索（版权和质量风险）。

## v0.6 — 封面管理

> 阶段目标：本地封面扫描、去重、文件访问和音乐绑定。

- [x] 封面数据模型（artworks / music_artwork_bindings）
- [x] 本地封面目录扫描（jpg/jpeg/png/webp）
- [x] SHA-256 文件哈希去重
- [x] 封面文件访问接口（`GET /api/artworks/{id}/file`）
- [x] 按文件名自动绑定封面到音乐
- [x] 手动绑定/解绑音乐主封面（`PUT/DELETE /api/music/{id}/artwork`）
- [x] 封面列表/详情查询 API
- [ ] 封面在线刮削（后续）
- [ ] 封面审核工作流（后续）
- [ ] 封面裁剪（后续）

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

## v0.4 — 前端音乐列表页 MVP

> 阶段目标：前端音乐库列表页面，支持扫描触发和分页浏览。

- [x] 音乐列表页（`/music`）
- [x] 触发扫描按钮（调用 `POST /api/music/scan`）
- [x] 刷新列表按钮
- [x] 分页浏览（`el-pagination`，支持切换每页条数）
- [x] 加载中 / 空状态 / 错误提示

**本阶段不包含**：音乐详情页、批量操作、搜索过滤。

## v0.3 — 本地音乐扫描与入库

> 阶段目标：完成本地音乐文件扫描入库闭环，提供音乐列表和详情查询。

- [x] POST /api/music/scan：快捷扫描入口（异步，后台执行）
- [x] GET /api/music?page=0&size=20：音乐分页列表
- [x] GET /api/music/{id}：音乐详情
- [x] 文件名元数据兜底解析（`Artist - Title` 格式）
- [x] 重复扫描跳过未变化文件（size + lastModifiedTime，1秒容差）
- [x] 默认音乐扫描目录 /path/to/music

**本阶段不包含**：ffprobe 音频内嵌元数据提取、歌词抓取、封面刮削、联网匹配。

## v0.2.1 — 扫描稳定性与前端体验增强

> 阶段目标：在 v0.2 基础上补充扫描任务健壮性与前端易用性。

- [x] ScanJob 分页查询与状态筛选（`page`/`size`/`status`）
- [x] TrackFile 分页查询（`page`/`size`/`ext`/`keyword`）
- [x] 扫描任务重复运行保护（`running`/`completed` 状态任务返回 `409 Conflict`）
- [x] 扫描路径安全校验（禁止危险根目录、路径穿越、扫描目录外路径）
- [x] 前端 Token 设置页面（存储于 localStorage）
- [x] 前端扫描任务页面与文件列表页面体验增强

**本阶段不包含**：音频内嵌元数据提取（ffprobe）、歌词抓取、封面刮削、联网匹配、音频指纹、登录系统。

## v0.2 — 音乐库扫描

> 阶段目标：完成本地音乐文件扫描和 `track_files` 记录，为后续元数据识别打基础。

- [x] 音乐目录递归遍历
- [x] 音频文件扩展名识别（mp3/flac/wav/m4a/aac/ogg/opus）
- [x] 一曲多文件基础表（track_files）
- [x] 重复扫描按 `file_path` 更新，不重复插入
- [x] 扫描任务状态与统计字段更新

**本阶段不包含**：音频内嵌元数据提取、歌词抓取、封面刮削、MusicBrainz/LRCLIB 等外部网络调用、音频指纹、登录系统、复杂权限系统。

## v0.1 — 后端骨架与 Track CRUD

> 阶段目标：搭建 Quarkus 项目骨架，实现 Track CRUD、扫描任务调度框架。

- [x] Quarkus 项目初始化
- [x] SQLite 数据库集成
- [x] REST API 骨架（含健康检查）
- [x] Bearer Token 鉴权
- [x] Track CRUD（创建/查询/更新/删除）
- [x] 扫描任务实体与 CRUD（仅 pending 状态，不含实际扫描）

**本阶段不包含**：音乐文件扫描、元数据提取、歌词抓取、封面刮削、歌手/专辑管理。
