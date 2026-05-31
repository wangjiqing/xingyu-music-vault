# 镜像拉取部署（不从源码构建）

本文档面向“直接拉取已发布镜像”部署模式。该模式不依赖本地源码构建，适合 NAS / 服务器稳定部署与回滚。v0.9.8 起 GHCR 支持 GitHub Actions 自动发布。

## 前置要求

- 已安装 Docker 与 Docker Compose v2
- 可访问镜像仓库（GHCR 或 Docker Hub）
- 已准备宿主机目录：`data`、`config`、`logs`、`music`、`lyrics`、`artwork`

## 准备 deploy 目录

在仓库根目录执行：

```bash
cp deploy/.env.example deploy/.env
cp deploy/docker-compose.image.example.yml deploy/docker-compose.yml
```

## 选择镜像来源

默认 `deploy/docker-compose.yml` 使用 GHCR：

```yaml
image: ghcr.io/wangjiqing/xingyu-music-vault:${IMAGE_TAG:-v0.9.8}
```

如需改用 Docker Hub，可改为：

```yaml
image: wangjiqing/xingyu-music-vault:${IMAGE_TAG:-v0.9.8}
```

## 修改 IMAGE_TAG

编辑 `deploy/.env`，推荐固定精确版本：

```dotenv
IMAGE_TAG=v0.9.8
```

`latest` 适合快速体验，不建议用于长期稳定部署。

## 启动服务

```bash
cd deploy
docker compose pull
docker compose up -d
docker compose ps
docker compose logs -f
```

## 验证后台页面

浏览器访问：

```text
http://localhost:8080
```

默认部署中 `MUSIC_DIR` 以只读方式挂载（`/music:ro`）。如需使用音频 Tag 写回相关能力，请改为读写挂载，具体注意事项见 [Docker 一键部署 - 只读与读写挂载](docker.md#只读与读写挂载)。

## 验证 OpenAPI 与健康检查

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/open/v1/server/info
curl -i http://localhost:8080/api/open/v1/sync/state
```

## 升级镜像

将 `IMAGE_TAG` 更新为新版本（例如 `v0.9.8`），然后执行：

```bash
cd deploy
docker compose pull
docker compose up -d
```

## 回滚到旧 tag

将 `IMAGE_TAG` 改回旧版本（例如 `v0.9.7`），然后执行：

```bash
cd deploy
docker compose pull
docker compose up -d
```

由于数据库和资源目录通过挂载持久化，回滚通常只涉及镜像版本切换。回滚前建议先备份 `deploy/data`、`deploy/lyrics`、`deploy/artwork`。
