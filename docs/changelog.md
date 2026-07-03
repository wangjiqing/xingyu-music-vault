# 更新日志

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## Unreleased

### 新增 / 调整

- **歌词对齐 Worker 联调基础**：补充共享任务目录、Worker 结果读取和状态同步字段，`request.json` 调整为 aligner v0.3.0 的 Worker 协议字段；历史任务的旧 `requestSnapshotJson` 不会被覆盖。
- **部署示例补强**：Dockerfile 默认创建 `/alignment-jobs` 并提供对齐相关环境变量；Compose 示例新增 Worker 模型缓存环境变量，说明音库 `/alignment-jobs` 与 Worker `/jobs` 是同一宿主机目录的不同容器视角。

## v1.2.4 — 歌词 source_path 幂等恢复与未绑定记录清理

**发布日期：** 2026.06.21

v1.2.4 基于 v1.2.3 的歌词删除同步继续收口恢复闭环：同一 `source_path` 的 LRC 删除后再恢复或更新，扫描会复用原歌词记录并重新绑定歌曲，不再新建重复歌词行。

### 修复 / 补强

- **source_path 幂等扫描**：扫描本地 LRC 时优先按规范化 `source_path` 复用既有 `lyrics` 记录，即使该记录此前因源文件删除而解绑，也会刷新正文、哈希、标题、歌手、解析状态与更新时间。
- **恢复后重新绑定**：恢复同一路径 LRC 后，现有「扫描歌词」入口会重新匹配歌曲并恢复 `song_lyrics` 主绑定；OpenAPI `/lyrics/meta` 和正文接口继续以当前主绑定为准。
- **保守历史去重**：仅在扫描遇到同一规范化 `source_path` 且可确认重复记录未绑定时清理重复行；无法安全判断的数据保留给管理员处理。
- **未绑定记录删除**：歌词管理页仅对未绑定记录显示「删除记录」，二次确认文案明确不会删除磁盘 `.lrc` 源文件；后端 `DELETE /api/lyrics/{id}` 会再次校验无 `song_lyrics` 引用，已重新绑定时返回冲突。
- **版本展示统一**：健康检查、管理端服务信息、OpenAPI 服务信息、后端构建版本、前端包版本、README、CHANGELOG 与 release 文档同步到 `1.2.4`。

### 兼容性

- 不新增扫描入口、扫描 Job 类型或音乐盒改动。
- 不做高风险全库未绑定歌词清理；磁盘 `.lrc` 源文件不会被删除。

---

## v1.2.3 — 歌词扫描删除同步修复

**发布日期：** 2026.06.21

v1.2.3 聚焦现有「扫描歌词」入口的同步语义修复。lyricsDir 现在作为本地歌词可用性的事实来源：一次完整成功的歌词扫描会同步处理新增、更新和已删除的 `.lrc` 源文件，避免源文件删除后 OpenAPI 继续返回数据库中的旧歌词正文。

### 修复 / 补强

- **删除同步修复**：修复删除 lyricsDir 中 `.lrc` 后，重新扫描歌词不会清理旧 `song_lyrics` 绑定的问题。
- **歌词源文件同步闭环**：扫描歌词现可同步处理新增、更新和已删除的歌词源文件；同内容 LRC 放回后也可通过现有扫描入口重新绑定。
- **OpenAPI 可用性修复**：缺失源歌词同步后，`/api/open/v1/tracks/{id}/lyrics/meta` 返回 `available=false`，`/api/open/v1/tracks/{id}/lyrics` 不再返回旧 `lyrics.content`。
- **扫描安全性补强**：仅在本次歌词目录完整扫描且文件处理无失败时执行缺失源文件解绑；扫描失败时保留既有绑定，避免误判删除。
- **版本展示统一**：健康检查、管理端服务信息、OpenAPI 服务信息、后端构建版本、前端包版本、README、CHANGELOG 与 release 文档同步到 `1.2.3`。

### 兼容性

- 不新增管理入口、扫描 Job 类型、数据库迁移或音乐盒改动。
- 旧 `lyrics` 记录可作为无绑定历史记录保留；OpenAPI 可用性由当前主绑定决定。

---

## v1.2.2 — 歌曲工作台小屏布局修复

**发布日期：** 2026.06.15

v1.2.2 聚焦管理端「歌曲工作台」的小屏与低高度显示体验，修复播放器和长内容 Tab 在窄内容区下溢出或被裁切的问题。本版本不改变后端业务接口、OpenAPI 契约或数据库结构。

### 改进 / 修复

- **工作台整体缩放**：小屏或低高度环境下统一缩小页面间距、左侧列表、标题、Tab、歌词、封面和播放器控件。
- **播放器响应式修复**：播放器按组件自身宽度响应，播放控制、进度条、时间、播放模式和音量控件可换行或缩小，避免横向溢出。
- **音量滑块边界修复**：音量滑块增加内部留白，满格时手柄不再探出播放器边框。
- **长内容滚动**：元数据和 OpenAPI 输出 Tab 支持内容区滚动，避免表格和 JSON 预览被裁切。
- **image compose 封面路径修复**：默认镜像 tag 更新到 `v1.2.2`，封面目录挂载目标与 `APP_ARTWORK_SCAN_DIR` 保持一致，避免容器内封面路径不匹配导致页面提示封面缺失。
- **版本展示统一**：健康检查、管理端服务信息、OpenAPI 服务信息、README、CHANGELOG 与 release 文档同步到 `1.2.2`。

### 暂不支持

- metadataConfirmed / lyricsConfirmed / artworkConfirmed 字段
- 确认 / 取消确认、批量确认、审计历史页面
- AI 候选生成、网络刮削、公网歌词请求
- 音频文件 Tag 写回、封面写入音频文件
- 复杂播放队列、播放历史、播放统计、收藏系统

---

## v1.2.1 — 歌曲工作台 MVP：边听边看，只读校验闭环

**发布日期：** 2026.06.14

v1.2.1 新增管理端「歌曲工作台」MVP，用于在本地 / 局域网可信环境中边播放歌曲，边查看数据库元数据、歌词、封面与 OpenAPI 输出预览。本版本只读查看，不引入确认状态、AI 候选、网络刮削、批量整理或音频 Tag 写回。

### 新增 / 补强

- **新增歌曲工作台页面**：侧边栏新增「歌曲工作台」入口，歌曲列表增加「工作台」操作，可带当前歌曲进入。
- **轻量播放器**：基于 HTML5 audio 提供播放 / 暂停、进度展示、时间展示、进度跳转、上一首 / 下一首。
- **工作台只读面板**：展示当前歌曲数据库元数据、歌词正文、封面预览与格式化 OpenAPI 输出预览。
- **新增管理端工作台接口**：`GET /api/admin/music/{id}/workbench` 聚合歌曲、歌词、封面和 OpenAPI 预览；`GET /api/admin/music/{id}/openapi-preview` 单独返回 OpenAPI 预览 JSON。
- **新增受保护音频接口**：`GET /api/admin/music/{id}/audio` 仅按数据库 music id 读取已入库音乐文件，并限制在配置音乐目录内，支持基本 Range 请求。
- **错误与空状态**：播放失败、OpenAPI 预览失败显示明确错误；无歌词、无封面显示主题空状态。
- **测试覆盖**：新增工作台后端测试，覆盖未登录拒绝、工作台数据、音频读取边界、缺文件、OpenAPI 预览结构。

### 暂不支持

- metadataConfirmed / lyricsConfirmed / artworkConfirmed 字段
- 确认 / 取消确认、批量确认、审计历史页面
- AI 候选生成、网络刮削、公网歌词请求
- 音频文件 Tag 写回、封面写入音频文件
- 复杂播放队列、播放历史、播放统计、收藏系统
- 匿名资源浏览或公开音频流服务

---

## v1.1.4 — 反向代理、HTTPS 与非标准端口部署说明

**发布日期：** 2026.06.14

v1.1.4 聚焦部署文档沉淀，不涉及业务逻辑、数据库结构或 OpenAPI 契约变更。本版本记录星语音库已通过家庭网络反向代理完成公网 HTTPS 访问验证，星语音乐盒已验证出门不挂 VPN 也可以访问星语音库、播放歌曲并加载歌词。

### 新增 / 补强

- **新增反向代理链路说明**：推荐 `https://example.com:18443` → reverse proxy → `http://192.168.1.100:18081` → `container:8080`
- **补充家用宽带非标准 HTTPS 端口部署说明**：说明 `80` / `443` 不可用时可通过 `18443` 暴露 HTTPS 反向代理入口
- **更新 Docker Compose 默认宿主机端口**：示例默认 `APP_PORT=18081`，容器内部端口继续保持 `8080`
- **补充公网安全建议**：不建议公网直接暴露后端端口；公网访问建议启用管理员登录、OpenAPI HMAC 与 HTTPS
- **补充证书与 DNS 注意事项**：证书域名必须匹配访问域名，`CNAME` 不会自动继承证书
- **补充反向代理注意事项**：当前 WebSocket 不是必需项；HSTS 建议在 HTTPS 链路稳定后再启用
- **版本展示统一**：健康检查、管理端服务信息、OpenAPI 服务信息、README、CHANGELOG 与 release 文档同步到 `1.1.4`

### 暂不支持

- 自动申请证书或管理 DNS / DDNS
- 托管平台一键公网部署
- 反向代理访问控制、WAF 或 OAuth2 网关封装

---

## v1.1.3 — OpenAPI AK/SK 凭证管理与 HMAC 签名认证

**发布日期：** 2026.06.10

v1.1.3 将 OpenAPI 客户端认证从旧静态 Token 重构为 AK/SK + HMAC-SHA256。管理端继续使用账号密码登录 + Session Cookie，OpenAPI 使用独立凭证、timestamp 与 nonce 防重放，并支持 `OPENAPI_READ` / `OPENAPI_WRITE` scope。

### Breaking Change

- **OpenAPI 旧静态 Token 认证已移除**：`xingyu.openapi.auth.enabled` 与 `xingyu.openapi.auth.token` 仅保留为废弃兼容配置，不再参与 `/api/open/v1/**` 认证。无论旧部署中 `auth.enabled` 是 `true` 还是 `false`，升级到 v1.1.3 后都必须配置 `xingyu.openapi.credential.master-key`，并在管理后台创建 AK/SK 凭证；未配置 master key 时 OpenAPI 会返回 `500 OPENAPI_CONFIG_ERROR`。
- **客户端必须适配 HMAC 签名**：旧 `Authorization: Bearer <token>` 与 `X-Xingyu-Api-Token` 请求头不再可访问 OpenAPI。星语音乐盒或其他客户端适配前，请继续使用 v1.1.2 联调。

### 新增 / 补强

- **新增 OpenAPI 凭证表与 nonce 表**：新增 `openapi_credentials`、`openapi_request_nonces`
- **新增管理端凭证接口**：支持创建、列表、启用 / 禁用和删除 OpenAPI 凭证；Secret Key 只在创建响应返回一次
- **Secret 加密存储**：使用 master key + AES-GCM 保存 Secret，列表不返回明文或密文
- **HMAC Filter**：集中保护 `/api/open/v1/**`，验证 AK、timestamp、nonce、签名版本、签名和 scope
- **旧静态 Token 废弃**：不再接受 `Authorization: Bearer <legacy-token>` 或 `X-Xingyu-Api-Token`
- **文档与配置更新**：补充 master key、签名规范、body hash、query canonicalization、scope 和 HTTPS 提醒

### Review 结论（2026.06.10）

**结果：通过。**

v1.1.3 完整 review 覆盖以下维度，均符合预期：

- **数据库**：`openapi_credentials` 与 `openapi_request_nonces` 表结构合理；`access_key` 唯一；`secret_encrypted` + `secret_fingerprint` 设计正确；nonce 表含唯一约束与过期时间索引。
- **Secret 存储**：使用 master key + AES-GCM（随机 IV、GCM tag 128 bits）加密；Secret 只在创建响应返回一次；列表与详情不返回 `secretKey` 或 `secretEncrypted`；master key 来自配置、不在日志输出。
- **凭证管理接口**：需要管理端 Session 登录才能访问；name / scopes 校验完整，非法 scope 返回 400；禁用/删除后 OpenAPI 访问均返回 401。
- **HMAC 签名校验**：集中式 Filter 作用于 `/api/open/v1/**`；不影响管理端、静态资源与健康检查；要求完整 5 个头（`X-Xingyu-Access-Key`、`X-Xingyu-Timestamp`、`X-Xingyu-Nonce`、`X-Xingyu-Signature-Version: v1`、`X-Xingyu-Signature`）；使用 HMAC-SHA256 + `MessageDigest.isEqual` 常量时间比较；canonical string 为固定 5 行（METHOD / PATH_WITH_CANONICAL_QUERY / SHA256_HEX_BODY / TIMESTAMP / NONCE）；query 按参数名+值升序 + 统一 URL 编码；timestamp 默认 ±5 分钟窗口；nonce 防重放含双重防护（count 检查 + DB 唯一约束 + `PersistenceException` 兜底）；签名失败 401、scope 不足 403；错误码区分 `OPENAPI_UNAUTHORIZED` / `OPENAPI_CREDENTIAL_DISABLED` / `OPENAPI_CREDENTIAL_EXPIRED` / `OPENAPI_FORBIDDEN`。
- **旧静态 Token 移除**：`Authorization: Bearer <legacy-token>` 与 `X-Xingyu-Api-Token` 不再可访问 OpenAPI；配置标记 `@Deprecated`；旧测试已替换为 HMAC 测试。
- **last used**：成功请求更新 `lastUsedAt` / `lastUsedIp` / `lastUsedUserAgent`；更新失败不泄露 Secret。
- **测试覆盖**：无签名 401、旧 Token 401、错误签名/版本/AK/timestamp 401、重复 nonce 401、正确签名 200、scope 不足 403、POST body hash 签名、创建凭证/列表不含 Secret/禁用后不可用/删除后不可用、管理端接口需 Session、OpenAPI 不依赖 Cookie、非法 scope 被拒绝。
- **前端**：新增凭证管理页面需要管理端登录；创建成功展示 AK/SK 并明确提示"只显示一次"；关闭弹窗后需勾选确认，无法再次查看 Secret；列表不显示 Secret；支持启用/禁用/删除并各有二次确认；未将 AK/SK 存入 localStorage。
- **文档**：各文档统一更新为 v1.1.3 HMAC 签名规范；curl 示例改为 `openapi_get` bash 函数含完整签名计算；补充 query 规范化、body hash、timestamp/nonce 防重放、scope、HTTPS 提醒与星语音乐盒适配提醒。
- **部署安全**：`.env.example` / Compose 示例新增 `XINGYU_OPENAPI_CREDENTIAL_MASTER_KEY`、HMAC 配置项，且显式关闭 `XINGYU_ADMIN_TEST_LEGACY_TOKEN_ENABLED=false`；旧静态 Token 配置废弃。

### 音乐盒适配提醒

星语音乐盒当前客户端需要后续版本适配 AK/SK + HMAC 签名认证。适配前不应直接升级生产联调用音库到 v1.1.3，或可继续使用 v1.1.2（旧静态 Token）联调。

### 暂不支持

- OAuth2 / OIDC
- Token 自动轮换
- Secret 重置流程
- 星语音乐盒客户端适配

---

## v1.1.2 — 管理员账号初始化与登录 / 登出后端能力

**发布日期：** 2026.06.08

v1.1.2 实现管理端基础访问控制后端能力：首次启动可初始化单管理员账号，初始化后关闭入口，后续通过 Session + HttpOnly Cookie 登录保持管理端登录态，并支持登出和查询当前用户。本版本不实现 OpenAPI Token，不改变音乐盒 OpenAPI 契约。

### 新增 / 补强

- **新增管理员用户表**：新增 `users` 表，保存单管理员账号、PBKDF2 密码哈希、角色、启用状态和登录时间
- **新增管理端认证接口**：提供 setup-status、setup、login、logout、me 接口
- **新增 Session Cookie 登录态**：登录成功后下发 `XINGYU_MUSIC_VAULT_SESSION` HttpOnly Cookie，登出后服务端 Session 失效并清理 Cookie
- **保护管理端 API**：除健康检查、认证入口、静态资源、OpenAPI 与既有只读封面文件外，管理端 `/api/*` 接口需要登录 Session
- **补充测试**：覆盖初始化、重复初始化拒绝、密码哈希、登录失败、me、logout、受保护接口、OpenAPI 分离和无注册入口
- **OpenAPI 服务版本同步**：`/api/open/v1/server/info` 的 `serviceVersion` 更新为 `1.1.2`
- **更新文档**：README、部署文档、API 文档和 release notes 补充 v1.1.2 说明

### 暂不支持

- 多用户
- 开放注册
- 找回密码
- 邮箱验证
- OAuth2 / OIDC
- NAS 第三方登录
- 细粒度 RBAC
- OpenAPI Token 新能力
- JWT localStorage 登录态

---

## v1.1.1 — 安全边界文档与部署风险提示

**发布日期：** 2026.06.08

v1.1.1 聚焦安全边界说明与部署风险提示。本版本只更新文档与部署示例注释，不实现登录、管理员初始化、Token、反向代理、HTTPS、接口逻辑、数据库结构或权限模型变更。

### 新增 / 补强

- **README 增加安全提醒入口**：明确当前版本优先面向本地 / 局域网可信环境，并引导阅读部署安全边界文档
- **部署文档增加安全边界说明**：说明不建议在未配置认证、HTTPS、反向代理保护的情况下直接暴露公网
- **OpenAPI 文档增加访问风险提示**：明确 OpenAPI 主要服务于本地 / 局域网联调和访问，不建议裸露到公网
- **Docker Compose 示例增加端口暴露提示**：在 compose 与 `.env.example` 中补充端口映射风险说明，不新增实际认证变量或代理配置
- **明确后续 v1.1.x 规划**：管理员账号初始化、登录 / 登出、管理端页面访问控制、后端管理接口登录保护、OpenAPI Token、只读 Token 与写操作权限区分、反向代理部署建议、HTTPS / 公网暴露风险说明和 Docker Compose 安全部署示例均为后续规划

### 暂不支持

- 管理员账号初始化
- 登录 / 登出
- 管理端页面访问控制
- 完整 OpenAPI Token 与权限区分
- 公网 HTTPS 安全部署方案
- 反向代理实际配置或模板
- 新安全框架、权限模型、多租户、OAuth2 / OIDC 或 NAS 第三方登录

---

## v1.0.3 — 冬夜雪境与春日晨光主题接入

**发布日期：** 2026.06.07

v1.0.3 聚焦「冬夜雪境 / Winter Moonlight」与「春日晨光 / Spring Dawn」两套四季主题素材接入，完成四季主题资源与既有轻量主题切换入口的基础闭环。本版本不修改后端业务逻辑、OpenAPI 契约、数据库结构或音乐扫描、元数据、歌词、封面等业务能力。

### 新增 / 补强

- **接入冬夜雪境主题资源包**：新增 `frontend/public/themes/winter-moonlight/`，放置 README banner、Logo、favicon、小图标、背景图、页面空状态插画、主题色板、`theme.json`、`theme.css` 与 `manifest.json`
- **接入春日晨光主题资源包**：新增 `frontend/public/themes/spring-dawn/`，放置 README banner、Logo、favicon、小图标、背景图、页面空状态插画、主题色板、`theme.json`、`theme.css` 与 `manifest.json`
- **补齐四季主题候选配置**：前端主题配置新增 `winter-moonlight` 与 `spring-dawn` 候选项，管理后台 header 右侧轻量主题切换入口已按春夏秋冬顺序覆盖春日、仲夏、秋日、冬夜四套主题
- **保持主题偏好恢复能力**：主题选择继续写入浏览器 `localStorage`，刷新页面后恢复上次选择；默认主题仍为 `midsummer-starlight`
- **避免春季缺失资源 404**：春季源素材缺少 `metadata-pending.png`，运行时使用同主题 `empty-home.png` 兜底，并在主题文档中列为待确认素材
- **延续背景别名去重策略**：未重复拷贝冬季、春季素材包中的 4K/2K/1080p 背景别名文件，改由 `manifest.json` 的 `assets.background.aliases` 记录映射
- **补充主题文档与 README 展示**：README 展示四套主题资源，新增 `docs/themes/winter-moonlight.md` 与 `docs/themes/spring-dawn.md`
- **OpenAPI 服务版本同步**：`/api/open/v1/server/info` 的 `serviceVersion` 更新为 `1.0.3`

### 已知限制

- 当前仍未引入服务端主题管理系统；主题切换仅作为浏览器本地轻量入口
- Winter Moonlight 的 `background/background-desktop.png` 体积约 3.1MB；运行时使用 `.webp` 背景，PNG 作为素材留存，后续可用 `pngquant` 等工具压缩或评估是否继续保留
- Winter Moonlight 深色主题下仍可能遇到部分浅色硬编码组件对比度不理想的问题，后续页面组件深度美化时统一处理
- Spring Dawn 源素材缺少 `empty-states/metadata-pending.png` 与 `empty-states/syncing.png`，未伪造文件，已在文档和 manifest 中标记
- 四套主题 Logo 与空状态图仍带有生成素材工程化版本限制，后续进入正式主题系统前建议重新规范化输出
- 后续版本可继续推进主题偏好体验优化、主题预览、跟随季节 / 时间自动切换、页面组件深度美化和星语音乐盒侧主题同步

---

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
