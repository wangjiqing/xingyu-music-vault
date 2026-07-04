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
image: ghcr.io/wangjiqing/xingyu-music-vault:${IMAGE_TAG:-v1.3.1}
```

如需改用 Docker Hub，可改为：

```yaml
image: wangjiqing/xingyu-music-vault:${IMAGE_TAG:-v1.3.1}
```

两个 registry 均发布以下平台镜像：

- `linux/amd64`
- `linux/arm64`

## 修改 IMAGE_TAG

编辑 `deploy/.env`，推荐固定精确版本：

```dotenv
IMAGE_TAG=v1.3.1
```

`latest` 适合快速体验，不建议作为长期生产固定版本。

## 启动服务

首次启用歌词草稿提取 / 逐字对齐 Worker 前，先准备共享目录和模型缓存目录：

```bash
mkdir -p alignment-jobs alignment-models
```

音库容器将 `ALIGNMENT_JOBS_DIR` 挂载为 `/alignment-jobs`，Worker 将同一宿主机目录挂载为
`/jobs`；`request.json` 中的输入和输出路径使用 Worker 视角的 `/jobs/...`。Compose 默认使用
`wangjiqing/xingyu-lyrics-aligner:0.4.0`，同一 Worker 支持 `LYRIC_DRAFT_EXTRACTION` 和
`LYRICS_ALIGNMENT`。如果本机已经有 HuggingFace 模型缓存，可将 `ALIGNMENT_MODELS_DIR` 指向
`~/.cache/huggingface`，Compose 会把它挂载到 Worker 的 `/models`。

v1.3.1 起，image compose 默认将 `/lyrics` 以读写方式挂载，并设置
`MUSIC_VAULT_ALIGNMENT_LYRICS_ROOT=/lyrics`。新的审核通过对齐导入会把正式 LRC / SWLRC
发布到 `/lyrics/alignment/{songId}/{jobId}`，普通歌词扫描会排除该受控子目录。未配置
`MUSIC_VAULT_ALIGNMENT_LYRICS_ROOT` 时服务可启动并读取历史 `ALIGNMENT` 资产，但新的审核导入会被拒绝。

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

将 `IMAGE_TAG` 更新为新版本（例如 `v1.3.1`），然后执行：

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
