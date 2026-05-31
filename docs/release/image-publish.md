# 镜像发布说明（v0.9.9）

v0.9.9 起支持 GitHub Actions 自动发布 GHCR 与 Docker Hub 双仓库镜像，同时保留本地手动构建与手动发布流程。当前不创建 GitHub Release，不包含镜像签名与 SBOM。

构建源策略：

- 本地源码构建默认使用国内镜像源（`npmmirror` + 阿里云 Maven）以提升国内网络环境下构建速度。
- GitHub Actions（CI / 镜像自动发布）默认使用官方源（`https://registry.npmjs.org` + Maven Central）。
- Actions 中 `MAVEN_MIRROR_URL` 留空，Dockerfile 不会生成空 Maven mirror。
- 生产镜像发布以 GitHub Actions 官方源构建结果为准。

## 镜像命名规范

- 本地主镜像名：`xingyu-music-vault`
- GHCR：`ghcr.io/wangjiqing/xingyu-music-vault`
- Docker Hub：`wangjiqing/xingyu-music-vault`

## tag 规则

以 `v0.9.9` 为例，自动发布：

- `ghcr.io/wangjiqing/xingyu-music-vault:v0.9.9`
- `ghcr.io/wangjiqing/xingyu-music-vault:v0.9`
- `ghcr.io/wangjiqing/xingyu-music-vault:latest`
- `wangjiqing/xingyu-music-vault:v0.9.9`
- `wangjiqing/xingyu-music-vault:v0.9`
- `wangjiqing/xingyu-music-vault:latest`

生产部署推荐使用精确版本 tag，例如 `v0.9.9`。`latest` 适合快速体验，不建议作为长期生产固定版本。

## 自动发布（GitHub Actions）

工作流文件：`.github/workflows/publish-ghcr.yml`

触发方式：

- push tag `v*`（例如 `v0.9.9`）
- `workflow_dispatch` 手动触发（输入 `release_tag`）

工作流权限：

- `contents: read`
- `packages: write`

仓库 Secrets：

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

登录与推送策略：

- GHCR 使用 `GITHUB_TOKEN` 登录 `ghcr.io`。
- Docker Hub 使用 `DOCKERHUB_USERNAME` 与 `DOCKERHUB_TOKEN` 登录。
- 使用 `docker/build-push-action@v6` 一次构建、多 tag 推送。
- 不在日志中打印 token，不把凭证写入文件。

默认发布平台：

- `linux/amd64`
- `linux/arm64`

Apple Silicon 可直接拉取 `linux/arm64` 镜像。如需临时验证 `amd64`：

```bash
docker pull --platform linux/amd64 ghcr.io/wangjiqing/xingyu-music-vault:v0.9.9
docker pull --platform linux/amd64 wangjiqing/xingyu-music-vault:v0.9.9
```

## 本地构建

```bash
docker build \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com \
  --build-arg MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public \
  -t xingyu-music-vault:v0.9.9 \
  .
```

## 本地打 tag

```bash
docker tag xingyu-music-vault:v0.9.9 ghcr.io/wangjiqing/xingyu-music-vault:v0.9.9
docker tag xingyu-music-vault:v0.9.9 ghcr.io/wangjiqing/xingyu-music-vault:v0.9
docker tag xingyu-music-vault:v0.9.9 ghcr.io/wangjiqing/xingyu-music-vault:latest

docker tag xingyu-music-vault:v0.9.9 wangjiqing/xingyu-music-vault:v0.9.9
docker tag xingyu-music-vault:v0.9.9 wangjiqing/xingyu-music-vault:v0.9
docker tag xingyu-music-vault:v0.9.9 wangjiqing/xingyu-music-vault:latest
```

## 手动发布兜底流程

GHCR：

```bash
echo "<GITHUB_TOKEN>" | docker login ghcr.io -u wangjiqing --password-stdin
docker push ghcr.io/wangjiqing/xingyu-music-vault:v0.9.9
docker push ghcr.io/wangjiqing/xingyu-music-vault:v0.9
docker push ghcr.io/wangjiqing/xingyu-music-vault:latest
```

Docker Hub：

```bash
docker login
docker push wangjiqing/xingyu-music-vault:v0.9.9
docker push wangjiqing/xingyu-music-vault:v0.9
docker push wangjiqing/xingyu-music-vault:latest
```

注意事项：

- `GITHUB_TOKEN` 需要具备 Packages 写入权限（例如 `write:packages`）。
- Docker Hub 需要提前创建仓库，或账号具备自动创建权限。
- 首次发布后需确认 GHCR Package 可见性符合预期。
