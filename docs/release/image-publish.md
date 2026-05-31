# 镜像发布说明（手动）

本文档用于 v0.9.7 镜像手动构建、打 tag、推送到 GHCR / Docker Hub 的发布准备流程。当前不包含 GitHub Actions、自动发布、多架构 buildx、镜像签名与 SBOM。

## 镜像命名规范

- 本地主镜像名：`xingyu-music-vault`
- GHCR：`ghcr.io/wangjiqing/xingyu-music-vault`
- Docker Hub：`wangjiqing/xingyu-music-vault`

如果后续仓库组织名或 Docker Hub 用户名发生变化，请按实际账户更新命名。

## tag 规则

- `v0.9.7`：精确版本 tag，推荐生产部署使用
- `v0.9`：`v0.9` 系列最新稳定版本
- `latest`：当前最新稳定版本

生产部署推荐使用精确版本 tag，例如 `v0.9.7`。`latest` 适合快速体验，不建议用于需要稳定回滚的长期部署。

## 本地构建

```bash
docker build \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com \
  --build-arg MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public \
  -t xingyu-music-vault:v0.9.7 \
  .
```

## 本地打 tag

```bash
docker tag xingyu-music-vault:v0.9.7 ghcr.io/wangjiqing/xingyu-music-vault:v0.9.7
docker tag xingyu-music-vault:v0.9.7 ghcr.io/wangjiqing/xingyu-music-vault:v0.9
docker tag xingyu-music-vault:v0.9.7 ghcr.io/wangjiqing/xingyu-music-vault:latest

docker tag xingyu-music-vault:v0.9.7 wangjiqing/xingyu-music-vault:v0.9.7
docker tag xingyu-music-vault:v0.9.7 wangjiqing/xingyu-music-vault:v0.9
docker tag xingyu-music-vault:v0.9.7 wangjiqing/xingyu-music-vault:latest
```

## GHCR 手动发布流程

登录 GHCR：

```bash
echo "<GITHUB_TOKEN>" | docker login ghcr.io -u wangjiqing --password-stdin
```

推送镜像：

```bash
docker push ghcr.io/wangjiqing/xingyu-music-vault:v0.9.7
docker push ghcr.io/wangjiqing/xingyu-music-vault:v0.9
docker push ghcr.io/wangjiqing/xingyu-music-vault:latest
```

注意事项：

- `GITHUB_TOKEN` 需要具备 Packages 写入权限（例如 `write:packages`）。
- 首次发布后需在 GitHub Packages 页面确认镜像可见性。
- 如果尚未准备公开发布，可先保持 private。

## Docker Hub 手动发布流程

登录 Docker Hub：

```bash
docker login
```

推送镜像：

```bash
docker push wangjiqing/xingyu-music-vault:v0.9.7
docker push wangjiqing/xingyu-music-vault:v0.9
docker push wangjiqing/xingyu-music-vault:latest
```

注意事项：

- Docker Hub 需要提前创建仓库，或账号具备自动创建权限。
- 生产部署推荐使用精确版本 tag。

