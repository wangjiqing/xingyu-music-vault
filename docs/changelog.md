# 更新日志

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## v1.0.2 — 秋日唱片主题素材接入

**发布日期：** 2026.06.07

v1.0.2 聚焦「秋日唱片 / Autumn Vinyl」主题素材接入，验证第二套四季主题能否复用 v1.0.1 建立的主题资源目录、主题元数据配置、前端静态引用候选和文档展示方式。本版本不修改后端业务逻辑、OpenAPI 契约、数据库结构或音乐扫描、元数据、歌词、封面等业务能力。

### 新增 / 补强

- **接入秋日唱片主题资源包**：新增 `frontend/public/themes/autumn-vinyl/`，放置 README banner、Logo、favicon、小图标、背景图、页面空状态插画、主题色板、`theme.json`、`theme.css` 与 `manifest.json`
- **补充主题候选配置与入口**：前端主题配置新增 `autumn-vinyl` 候选项，管理后台 header 右侧提供轻量主题切换入口，选择结果写入浏览器 `localStorage`，页面皮肤、空状态图、favicon 和 `theme.css` 跟随当前主题更新
- **集中当前主题资源引用**：管理后台当前主题素材路径改由轻量 helper 输出，为后续 Winter Moonlight / Spring Dawn 继续接入做准备
- **泛化主题 archive 构建排除**：Vite 构建会排除所有主题目录下的 `archive/` 备份目录，避免源预览素材进入生产产物
- **补充主题文档与 README 展示**：README 展示两套主题资源，新增 `docs/themes/autumn-vinyl.md`，并说明 Autumn Vinyl 是四季主题体系中的秋季主题
- **处理重复背景别名**：未重复拷贝秋季素材包中的 4K/2K/1080p 背景别名文件，改由 `manifest.json` 的 `assets.background.aliases` 记录映射
- **OpenAPI 服务版本同步**：`/api/open/v1/server/info` 的 `serviceVersion` 更新为 `1.0.2`

### 已知限制

- 当前仍未引入服务端主题管理系统；主题切换仅作为浏览器本地轻量入口
- Autumn Vinyl 背景不是原生 4K 终稿，不应作为 4K 品牌背景交付物标注
- Logo 与空状态图仍带有生成素材工程化版本限制，后续进入正式主题系统前建议重新规范化输出
- Winter Moonlight / Spring Dawn 尚未接入，后续应沿用本次主题目录和文档结构

---

## v1.0.1 — 仲夏星河主题资源试接与管理后台体验微调

**发布日期：** 2026.06.06

v1.0.1 聚焦「仲夏星河 / Midsummer Starlight」主题资源的低风险试接和管理后台页面体验微调。本版本不重构主题系统，不修改后端业务逻辑，不接入星语音乐盒项目。

### 新增 / 补强

- **接入仲夏星河主题资源包**：将 `themes/midsummer-starlight/` 工程化资源放入 `frontend/public/themes/midsummer-starlight/`，保留 `theme.json`、`theme.css`、`manifest.json` 与主题 README 作为后续主题系统扩展基础
- **补充 README 主题预览**：README 顶部新增主题预览图和资源试接说明，不覆盖现有项目标题
- **接入主题 favicon 与可选主题变量**：前端入口引用主题 favicon 与 `theme.css`，仅作为静态变量和资源入口使用
- **管理后台整体皮肤试接**：侧边栏、header、footer、主内容底板低风险引用主题背景与 banner 资源，保留现有布局与业务导航
- **首页主题化改造**：首页迁入音乐统计卡片，使用主题 banner、logo 和空状态素材作为欢迎区与功能入口视觉素材，并补充卡片 hover 动效
- **列表页滚动体验统一**：全部歌曲、歌手、专辑、歌词、封面以及歌手/专辑详情列表改为内部滚动区域，默认预取两页并在滚动接近底部时追加下一页
- **列表信息密度调整**：全部歌曲列表移除顶部统计卡片，封面缩略图改为状态与统一预览入口；歌手/专辑详情页操作列加宽并统一按钮单行展示
- **OpenAPI 页面收束高度**：OpenAPI 页面改为左右两栏内部滚动布局，隐藏请求示例，避免浏览器纵向滚动条破坏主题背景覆盖
- **footer 文案结构化**：footer 固定展示项目名，版本优先从 `/api/open/v1/server/info` 的 `serviceVersion` 获取，主题名从当前主题配置读取，末尾展示 Apache License 2.0
- **OpenAPI 服务版本同步**：`/api/open/v1/server/info` 的 `serviceVersion` 更新为 `1.0.1`
- **主题资源冗余清理**：移除重复背景二进制文件，旧 4K/2K/1080p 名称改由 `manifest.json` alias 记录；生产构建排除主题 `archive/` 备份目录

### 已知限制

- 当前主题资源为生成素材工程化试点版，还不是最终品牌资产
- Logo 来源于展示板裁切，后续进入正式主题系统前建议重新制作或规范化输出
- 空状态图已用于多个页面，但仍建议后续按页面语义重绘
- 背景图不是原生 4K 终稿，不应作为 4K 品牌背景交付物标注
- 当前仍未引入复杂主题切换系统；footer 已预留从当前主题配置读取名称的轻量结构

---

## v1.0.0 — 首个正式稳定版本

**发布日期：** 2026.05.31

v1.0.0 不新增业务能力，聚焦首个正式稳定版本的发布整理、文档入口、版本历史展示和发布检查清单。

### 新增 / 补强

- **完成首个正式稳定版本发布整理**：明确 v1.0.0 作为首个正式稳定版本的定位与发布边界
- **整理 README 与文档入口**：README 收敛为项目定位、镜像部署、源码构建和核心文档入口
- **将版本历史调整为倒序展示**：README 版本里程碑与 roadmap 按最新版本在前展示
- **更新镜像部署示例到 v1.0.0**：`IMAGE_TAG` 与 image fallback 默认值更新为 `v1.0.0`
- **完善 v1.0.0 Release Notes**：新增 `docs/release/v1.0.0-release-notes.md`
- **完善 v1.0 发布检查清单**：补充 GHCR / Docker Hub、manifest、Mac mini arm64、GitHub Release 与 tag 验证项
- **发布流程说明收口**：明确 tag `v1.0.0` 会触发 GHCR / Docker Hub 镜像发布，GitHub Release 可手动创建

### 暂不支持

- 新业务接口
- 在线歌词抓取 / 在线封面刮削 / AI 元数据补全
- UI 大改
- 公网 HTTPS / 域名反向代理
- 镜像签名 / SBOM

---

## v0.9.9 — Docker Hub 发布与 v1.0 开源规范收口

**发布日期：** 2026.05.31

v0.9.9 不新增业务能力，聚焦 Docker Hub 自动发布与 v1.0 前开源发布规范收口。

### 新增 / 补强

- **支持 Docker Hub 自动镜像发布**：发布 workflow 增加 Docker Hub 登录与推送，使用 `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` 仓库 Secrets
- **保持 GHCR 与 Docker Hub 多架构镜像发布**：同一次 buildx 构建推送 GHCR 与 Docker Hub，平台为 `linux/amd64`、`linux/arm64`
- **更新 image compose 示例与镜像部署文档**：默认继续推荐 GHCR，补充 Docker Hub image 替换方式，默认 `IMAGE_TAG` 更新为 `v0.9.9`
- **新增 Apache-2.0 LICENSE 与 NOTICE**：明确项目开源许可证与版权声明
- **新增 SECURITY.md / CONTRIBUTING.md**：补充安全问题处理、Issue / PR 协作、开发与 Docker 构建基本命令
- **新增 Release Notes 模板**：提供版本说明、部署说明、镜像信息、兼容性、已知限制与验证清单结构
- **新增 v1.0 发布检查清单**：覆盖开源文件、镜像发布、多架构拉取、部署验证与发布流程确认
- **README 收口**：增加许可证说明、镜像地址、部署 / 发布 / OpenAPI / 贡献 / 安全文档入口

### 暂不支持

- GitHub Release 正式发布
- v1.0.0 正式发布
- 镜像签名 / SBOM
- 新业务接口与协议扩展

---

## v0.9.8 — GitHub Actions 与 GHCR 自动镜像发布

**发布日期：** 2026-05-31

v0.9.8 不新增业务能力，聚焦基础 CI 与 GHCR 自动镜像发布能力，为后续 v1.0 正式发布做准备。

### 新增 / 补强

- **基础 CI workflow**：新增 `.github/workflows/ci.yml`，在 `push -> main`、`pull_request -> main` 与 `workflow_dispatch` 触发，执行后端 Maven 测试、前端 `npm ci && npm run build`、Docker build（不推送）
- **GHCR 自动发布 workflow**：新增 `.github/workflows/publish-ghcr.yml`，支持 `push tag v*` 和 `workflow_dispatch`，使用 `GITHUB_TOKEN` 推送 GHCR
- **发布 tag 规则自动化**：当 tag 为 `v0.9.8` 时，自动发布 `v0.9.8`、`v0.9`、`latest`
- **镜像构建参数对齐**：Actions 构建复用 `NPM_REGISTRY` 与 `MAVEN_MIRROR_URL` 参数
- **构建源策略分层**：GitHub Actions 默认使用官方源（npmjs + Maven Central），本地 Docker/Compose 模板保留国内镜像源默认值用于本地加速
- **缓存补强**：CI 持续使用 Maven / npm 缓存与 Docker layer cache，GHCR 发布 workflow 增加 Docker layer cache
- **GHCR 多架构发布**：GHCR 自动发布支持 `linux/amd64` 与 `linux/arm64` manifest，Apple Silicon 可直接拉取 `arm64` 镜像
- **发布与部署文档更新**：补充 GHCR 自动发布说明、镜像拉取部署说明与 v0.9.8 发布检查清单
- **Docker Hub 策略明确**：Docker Hub 仍为手动发布流程，后续版本再评估自动化

### 暂不支持

- Docker Hub 自动发布
- GitHub Release 正式发布
- 镜像签名 / SBOM
- 新业务接口与协议扩展

---

## v0.9.7 — 镜像发布与 Packages 准备

**发布日期：** 2026-05-31

v0.9.7 不新增业务能力，聚焦正式镜像发布链路准备，补齐 GHCR / Docker Hub 手动发布与镜像拉取部署文档。

### 新增 / 补强

- **镜像命名规范明确**：统一主镜像 `xingyu-music-vault`，并明确 GHCR `ghcr.io/wangjiqing/xingyu-music-vault` 与 Docker Hub `wangjiqing/xingyu-music-vault` 命名
- **镜像 tag 规则明确**：定义 `v0.9.7`（精确版本）、`v0.9`（系列稳定）、`latest`（最新稳定）三类 tag，用于发布与回滚策略
- **新增 image 模式 Compose 模板**：新增 `deploy/docker-compose.image.example.yml`，支持直接拉取已发布镜像部署，不依赖源码构建
- **新增镜像拉取部署文档**：新增 `docs/deployment/image-deploy.md`，覆盖 pull / up、验证、升级与回滚流程
- **新增镜像手动发布文档**：新增 `docs/release/image-publish.md`，覆盖本地 build、打 tag、GHCR 登录推送、Docker Hub 登录推送
- **新增发布前检查清单**：新增 `docs/release/v0.9.7-checklist.md`，用于发布前自检
- **README 部署入口补全**：README 增加 Docker 镜像部署、源码构建部署、镜像发布说明入口

### 暂不支持

- GitHub Actions / 自动发布镜像
- GitHub Release
- 公网 HTTPS / 域名反向代理
- 多架构 buildx 强制流程
- 镜像签名 / SBOM
- 新业务接口与协议扩展

---

## v0.9.6 — Docker 一键部署与运行规范化

**发布日期：** 2026-05-31

v0.9.6 聚焦 Docker / Docker Compose 一键部署形态，为 v1.0 正式发布前的运行规范化做准备。本版本不新增业务接口，不修改星语音乐盒项目，不发布 Docker Hub / GHCR 镜像，不做公网 HTTPS 或 CI/CD。

### 新增 / 补强

- **前后端一体 Dockerfile**：新增根目录 `Dockerfile`，多阶段构建 Vue 管理后台与 Quarkus 后端，并将前端产物打入后端静态资源目录
- **国内构建源与缓存**：Dockerfile 默认使用 `registry.npmmirror.com` 与阿里云 Maven public 仓库，并通过 BuildKit cache 缓存 npm 与 Maven 依赖；`docker-compose.example.yml` 可通过 `.env` 覆盖构建源
- **Compose 一键部署模板**：新增根目录 `docker-compose.example.yml`，通过 `.env` 配置端口、数据目录、音乐目录、歌词目录、封面目录、OpenAPI 认证、限流和访问日志
- **环境变量模板**：新增 `.env.example`，所有关键路径使用示例值，不包含个人本机路径或真实 token
- **路径规范化**：Docker 默认运行路径统一为 `/app/data`、`/app/config`、`/music`、`/lyrics`、`/artwork`，SQLite 必须通过数据目录持久化
- **部署文档**：新增 `docs/deployment/docker.md`，说明目录准备、复制模板、启动、访问后台、验证 OpenAPI、查看日志、停止服务和常见问题
- **备份与升级说明**：新增 `docs/deployment/backup-and-upgrade.md`，说明 SQLite、歌词、封面、配置和音乐库的备份建议，以及后续镜像发布后的升级 / 回滚流程
- **旧 Compose 示例去个人路径化**：`deploy/docker-compose.yml` 改为变量化路径，避免提交本机绝对路径

### 暂不支持

- GitHub Actions / CI/CD
- Docker Hub / GHCR 自动发布
- GitHub Release
- 公网 HTTPS / 域名反向代理
- 新业务接口或新数据库类型

---

## v0.9.5 — OpenAPI 联调反馈收口与契约稳定

**发布日期：** 2026-05-30

v0.9.5 基于星语音乐盒局域网真机联调反馈收口 OpenAPI 资源状态契约。本版本不新增写接口，不修改星语音乐盒项目，不做公网 HTTPS、正式 NAS 部署、AI 刮削或多端协议扩展。

### 新增 / 补强

- **封面可用状态一致性修复**：`tracks` 列表和详情只有在主封面绑定存在、文件真实可读且路径位于允许根目录内时才返回 `artworkAvailable=true`、`artworkId` 和 `artworkUrl`
- **封面 meta 降级语义收敛**：存在封面绑定但文件缺失、不可读或 Docker / local-dev 路径不一致导致不可访问时，`/artwork/meta` 返回 `available=false`；封面二进制接口仍返回 `404 OPENAPI_ARTWORK_NOT_FOUND`
- **歌词状态契约固定**：补充测试确认 `lyricsAvailable=true` 时 `/lyrics/meta` 返回 `available=true` 且正文可取；无歌词时 `/lyrics/meta` 返回 `available=false`，正文 `404` 是可降级业务状态
- **客户端降级契约说明**：文档明确 `available=true` 表示客户端可继续请求资源，`available=false` 表示应降级为无歌词 / 无封面，`404` 对客户端是可降级状态，`500` 才表示服务端异常
- **星语音乐盒真实联调反馈记录**：文档记录已验证 `server/info`、`sync/state`、`match/track`、歌词、封面、基础元数据同步，以及控制面修改元数据 / 歌词 / 封面后客户端刷新可见
- **测试补强**：补充列表 available 字段、歌词 / 封面 meta、封面文件缺失、match/track 和控制面元数据变更后的 OpenAPI 可见性测试

### 暂不支持

- OpenAPI 写接口
- 公网 HTTPS / 正式 NAS 部署
- 音频 stream
- 客户端写入元数据
- AI 刮削
- 完整客户端同步中心
- 多端协议扩展
- 前端大改

---

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
