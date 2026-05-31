# Xingyu Music Vault / 星语音库

> 自托管音乐元数据管理与 OpenAPI 服务，面向个人音乐库、NAS、家庭服务器和私有部署环境。

## 项目定位

Xingyu Music Vault 是一个音乐库管理后台和只读 OpenAPI 服务，不是播放器。它负责扫描本地音乐文件，管理歌曲元数据、歌词和封面，并向播放器客户端或其他工具提供稳定的音乐库数据接口。

核心边界：

- 管理音乐库元数据、歌词、封面和文件状态
- 提供 Web 管理后台与 REST / OpenAPI 只读接口
- 支持 Docker Compose 自托管部署
- 不提供在线播放、音频流、客户端写入或公网托管能力

## 核心能力

- 本地音乐扫描、入库、重复扫描跳过
- 歌曲、歌手、专辑维度的浏览与管理
- 本地 LRC 歌词导入、绑定与查询
- 本地封面扫描、导入、绑定与文件访问
- 音乐元数据编辑、批量整理、音频 Tag 差异比较与受控写回
- 文件信息、安全删除、回收站与恢复
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
- [Release Notes](docs/release/v1.0.0-release-notes.md)
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
v1.0.0 [ ] 首个正式稳定版本
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
