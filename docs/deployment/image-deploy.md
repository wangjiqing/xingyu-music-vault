# 镜像拉取部署（不从源码构建）

本文档面向“直接拉取已发布镜像”部署模式。该模式不依赖本地源码构建，适合 NAS / 服务器稳定部署与回滚。项目继续使用 GHCR 与 Docker Hub 双仓库多架构镜像。

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
image: ghcr.io/wangjiqing/xingyu-music-vault:${IMAGE_TAG:-v1.2.4}
```

如需改用 Docker Hub，可改为：

```yaml
image: wangjiqing/xingyu-music-vault:${IMAGE_TAG:-v1.2.4}
```

两个 registry 均发布以下平台镜像：

- `linux/amd64`
- `linux/arm64`

## 修改 IMAGE_TAG

编辑 `deploy/.env`，推荐固定精确版本：

```dotenv
IMAGE_TAG=v1.2.4
```

`latest` 适合快速体验，不建议作为长期生产固定版本。

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

默认 image compose 会将宿主机 `ARTWORK_DIR` 挂载到容器内 `APP_ARTWORK_SCAN_DIR`，默认值为 `/artwork`，与 `application.yml` 的 `app.artwork.scan-dir` 保持一致。远端部署后如果页面提示封面缺失，请优先确认 `ARTWORK_DIR` 指向宿主机真实封面目录，且 `APP_ARTWORK_SCAN_DIR` 与容器内挂载目标一致。

## 验证 OpenAPI 与健康检查

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/open/v1/server/info
curl -i http://localhost:8080/api/open/v1/sync/state
```

## 升级镜像

将 `IMAGE_TAG` 更新为新版本（例如 `v1.2.4`），然后执行：

```bash
cd deploy
docker compose pull
docker compose up -d
```

## 回滚到旧 tag

将 `IMAGE_TAG` 改回旧版本（例如 `v0.9.9`），然后执行：

```bash
cd deploy
docker compose pull
docker compose up -d
```

由于数据库和资源目录通过挂载持久化，回滚通常只涉及镜像版本切换。回滚前建议先备份 `deploy/data`、`deploy/lyrics`、`deploy/artwork`。

## 构建源说明

已发布镜像由 GitHub Actions 使用官方 npm registry 与 Maven Central 构建。本地 compose 源码构建模板继续默认使用国内镜像源，用于本地构建加速。
