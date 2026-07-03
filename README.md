# Xingyu Music Vault / 星语音库

> 自托管音乐元数据管理与 OpenAPI 服务，面向个人音乐库、NAS、家庭服务器和私有部署环境。

## 主题预览

![春日晨光 / Spring Dawn 主题预览](frontend/public/themes/spring-dawn/banner/readme-banner.png)

![仲夏星河 / Midsummer Starlight 主题预览](frontend/public/themes/midsummer-starlight/banner/readme-banner.png)

![秋日唱片 / Autumn Vinyl 主题预览](frontend/public/themes/autumn-vinyl/banner/readme-banner.png)

![冬夜雪境 / Winter Moonlight 主题预览](frontend/public/themes/winter-moonlight/banner/readme-banner.png)

当前已完成四套四季主题资源基础接入：「春日晨光 / Spring Dawn」「仲夏星河 / Midsummer Starlight」「秋日唱片 / Autumn Vinyl」与「冬夜雪境 / Winter Moonlight」。v1.0.3 新增冬季、春季主题素材目录、主题元数据和候选配置，既有管理后台 header 轻量主题切换入口已按春夏秋冬顺序覆盖四季主题，选择结果继续保存在浏览器本地。本版本仍不包含完整服务端主题管理系统，也不修改后端业务逻辑、OpenAPI 契约或数据库结构。资源说明见 [春日晨光主题资源接入](docs/themes/spring-dawn.md)、[仲夏星河主题资源试接](docs/themes/midsummer-starlight.md)、[秋日唱片主题资源接入](docs/themes/autumn-vinyl.md) 与 [冬夜雪境主题资源接入](docs/themes/winter-moonlight.md)。

## 项目定位

Xingyu Music Vault 是一个音乐库管理后台和只读 OpenAPI 服务，不是面向终端用户的播放器。它负责扫描本地音乐文件，管理歌曲元数据、歌词和封面，并向播放器客户端或其他工具提供稳定的音乐库数据接口。v1.2.1 新增的「歌曲工作台」仅用于管理端本地校验，可边播放边查看数据，不提供匿名或公开音频流服务；v1.2.2 补强了工作台在小屏和低高度环境下的自适应布局；v1.2.3 修复歌词扫描对已删除 LRC 源文件的同步行为；v1.2.4 修复同路径歌词恢复后重复创建记录的问题，并允许管理员清理未绑定歌词记录。

核心边界：

- 管理音乐库元数据、歌词、封面和文件状态
- 提供 Web 管理后台与 REST / OpenAPI 只读接口
- 支持 Docker Compose 自托管部署
- 不提供公开在线播放、客户端写入或公网托管能力

## 安全提醒

当前版本优先面向本地 / 局域网可信环境使用，不建议在未配置认证、HTTPS、反向代理保护的情况下直接暴露公网。

v1.1.4 已补充家庭网络反向代理、HTTPS 与非标准公网端口部署说明。公网访问建议使用 `https://example.com:18443` 进入反向代理，再转发到 `http://192.168.1.100:18081`，最终映射到容器内部 `8080`；不建议直接暴露后端端口。部署前请先阅读 [部署安全边界](docs/deployment.md#部署安全边界)。

v1.1.3 已补充 OpenAPI AK/SK 凭证管理与 HMAC-SHA256 签名认证。管理端继续使用账号密码 + Session Cookie；OpenAPI 客户端使用独立 Access Key / Secret Key，不再接受旧静态 OpenAPI Token。

首次启动且数据库中没有管理员账号时，请通过管理端初始化第一个管理员账号；初始化完成后不再开放注册，也不支持创建第二个管理员。

## 核心能力

- 本地音乐扫描、入库、重复扫描跳过
- 歌曲、歌手、专辑维度的浏览与管理
- 本地 LRC 歌词导入、绑定、删除同步与查询
- 歌词对齐任务创建、Worker 共享目录联调、人工审核与确认导入；导入后的 LRC 兼容现有播放，SWLRC 作为可选逐字歌词附加资产
- 本地封面扫描、导入、绑定与文件访问
- 音乐元数据编辑、批量整理、音频 Tag 差异比较与受控写回
- 管理端歌曲工作台：边播放边查看元数据、歌词、封面与 OpenAPI 输出预览（只读）
- 文件信息、安全删除、回收站与恢复
- 单管理员初始化、登录 / 登出与管理端 Session 保护
- 只读 OpenAPI：服务信息、同步状态、增量变更、曲目、歌词、封面、歌手、专辑与本地匹配
- Docker / Docker Compose 一体化部署

## 镜像部署

v1.0.0 推荐使用精确版本 tag 部署。`latest` 适合快速体验，不建议作为长期生产固定版本。

镜像地址：

- GHCR：`ghcr.io/wangjiqing/xingyu-music-vault`
- Docker Hub：`wangjiqing/xingyu-music-vault`

默认 GHCR image 模式：

```bash
cp deploy/.env.example deploy/.env
cp deploy/docker-compose.image.example.yml deploy/docker-compose.yml
cd deploy
docker compose pull
docker compose up -d
```

源码构建模式：

```bash
cp .env.example .env
cp docker-compose.example.yml docker-compose.yml
docker compose up -d --build
```

构建源策略：

- GitHub Actions 发布镜像使用官方 npm registry 与 Maven Central。
- 本地 Compose 模板默认使用国内镜像源，便于本地构建加速。

## 文档入口

- [Docker 部署](docs/deployment/docker.md)
- [镜像部署](docs/deployment/image-deploy.md)
- [OpenAPI 接入](docs/openapi-client-integration.md)
- [备份与升级](docs/deployment/backup-and-upgrade.md)
- [镜像发布](docs/release/image-publish.md)
- [春日晨光主题资源接入](docs/themes/spring-dawn.md)
- [仲夏星河主题资源试接](docs/themes/midsummer-starlight.md)
- [秋日唱片主题资源接入](docs/themes/autumn-vinyl.md)
- [冬夜雪境主题资源接入](docs/themes/winter-moonlight.md)
- [Release Notes](docs/release/v1.2.4-release-notes.md)
- [更新日志](docs/changelog.md)
- [贡献说明](CONTRIBUTING.md)
- [安全说明](SECURITY.md)

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| 后端 | Quarkus (Java 21) |
| 前端 | Vue 3 + TypeScript |
| 数据库 | SQLite |
| 存储 | 本地文件系统 |
| 部署 | Docker Compose |

## 版本里程碑

```text
v1.3.0 [ ] 歌词对齐任务、Worker 同步、人工审核与确认导入
v1.2.4 [x] 歌词 source_path 幂等恢复与未绑定记录清理
v1.2.3 [x] 歌词扫描删除同步修复
v1.2.2 [x] 歌曲工作台小屏布局修复
v1.2.1 [x] 歌曲工作台 MVP：边听边看，只读校验闭环
v1.1.4 [x] 反向代理、HTTPS 与家用宽带非标准端口部署说明
v1.1.3 [x] OpenAPI AK/SK 凭证管理与 HMAC-SHA256 签名认证
v1.1.2 [x] 管理员账号初始化与登录 / 登出后端能力
v1.1.1 [x] 安全边界文档与部署风险提示
v1.0.3 [x] 冬夜雪境与春日晨光主题接入，四季主题基础闭环
v1.0.2 [x] 秋日唱片主题素材接入
v1.0.1 [x] 仲夏星河主题资源试接与管理后台体验微调
v1.0.0 [x] 首个正式稳定版本
v0.9.9 [x] Docker Hub 发布与 v1.0 开源规范收口
v0.9.8 [x] GitHub Actions 与 GHCR 自动镜像发布
v0.9.7 [x] 镜像发布与 Packages 准备
v0.9.6 [x] Docker 一键部署与运行规范化
v0.9.5 [x] OpenAPI 联调反馈收口与契约稳定
v0.9.4 [x] 星语音乐盒本地联调部署说明
v0.9.3 [x] 打包部署与 Docker 基础验证
v0.9.2 [x] OpenAPI 安全与访问控制
v0.9.1 [x] 客户端缓存与增量同步增强
v0.9.0 [x] OpenAPI 与外部集成基础
v0.8.x [x] 歌手、专辑、歌曲浏览与元数据同步能力
v0.7.x [x] 元数据编辑、文件管理、回收站与管理后台体验
v0.6.x [x] 本地封面扫描、导入与绑定
v0.5.x [x] 本地 LRC 歌词导入、绑定与管理
v0.4.0 [x] 前端音乐列表页 MVP
v0.3.0 [x] 本地音乐扫描与入库
v0.2.x [x] 音乐库扫描与扫描稳定性增强
v0.1.0 [x] 后端骨架与 Track CRUD
```

完整历史见 [更新日志](docs/changelog.md) 与 [开发路线图](docs/roadmap.md)。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。
