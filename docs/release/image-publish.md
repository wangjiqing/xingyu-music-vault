# 镜像发布说明（v0.9.8）

v0.9.8 起支持 GitHub Actions 自动发布 GHCR 镜像，同时保留本地手动构建与手动发布流程。当前不包含 Docker Hub 自动发布、多架构 buildx、镜像签名与 SBOM。

构建源策略：

- 本地源码构建默认使用国内镜像源（`npmmirror` + 阿里云 Maven）以提升国内网络环境下构建速度。
- GitHub Actions（CI / GHCR 自动发布）默认使用官方源（`https://registry.npmjs.org` + Maven Central）。
- 如使用自建 runner 或特定网络环境，可按需覆盖 `NPM_REGISTRY` 与 `MAVEN_MIRROR_URL`。
- 生产镜像发布以 GitHub Actions 官方源构建结果为准。

## 镜像命名规范

- 本地主镜像名：`xingyu-music-vault`
- GHCR：`ghcr.io/wangjiqing/xingyu-music-vault`
- Docker Hub：`wangjiqing/xingyu-music-vault`

如果后续仓库组织名或 Docker Hub 用户名发生变化，请按实际账户更新命名。

## tag 规则

- `v0.9.8`：精确版本 tag，推荐生产部署使用
- `v0.9`：`v0.9` 系列最新稳定版本
- `latest`：当前最新稳定版本

生产部署推荐使用精确版本 tag，例如 `v0.9.8`。`latest` 适合快速体验，不建议用于需要稳定回滚的长期部署。

## GHCR 自动发布（GitHub Actions）

工作流文件：`.github/workflows/publish-ghcr.yml`

触发方式：

- push tag `v*`（例如 `v0.9.8`）
- `workflow_dispatch` 手动触发（输入 `release_tag`）

补充说明：

- 如果需要对已存在的版本 tag 重新构建，可直接使用 `workflow_dispatch` 输入相同 `release_tag` 重新发布同名镜像 tag（例如再次发布 `v0.9.8`）。

工作流权限：

- `contents: read`
- `packages: write`

构建并推送：

- `ghcr.io/wangjiqing/xingyu-music-vault:v0.9.8`
- `ghcr.io/wangjiqing/xingyu-music-vault:v0.9`
- `ghcr.io/wangjiqing/xingyu-music-vault:latest`

构建参数默认值：

- `NPM_REGISTRY=https://registry.npmjs.org`
- `MAVEN_MIRROR_URL=`（留空即使用 Maven Central）

首次自动发布后需要到 GitHub Packages 页面确认镜像可见性（public/private）设置是否符合预期。

## 基础 CI（GitHub Actions）

工作流文件：`.github/workflows/ci.yml`

触发方式：

- push 到 `main`
- `pull_request` 到 `main`
- `workflow_dispatch` 手动触发（用于不改代码时复跑 CI）

校验内容：

- 后端 Maven 测试
- 前端 `npm ci` 与 `npm run build`
- Docker 镜像构建（仅构建，不推送，启用 GitHub Actions layer cache）

构建参数默认值（Actions）：

- `NPM_REGISTRY=https://registry.npmjs.org`
- `MAVEN_MIRROR_URL=`（留空即使用 Maven Central）

权限：

- `contents: read`

## 本地构建

```bash
docker build \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com \
  --build-arg MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public \
  -t xingyu-music-vault:v0.9.8 \
  .
```

## 本地打 tag

```bash
docker tag xingyu-music-vault:v0.9.8 ghcr.io/wangjiqing/xingyu-music-vault:v0.9.8
docker tag xingyu-music-vault:v0.9.8 ghcr.io/wangjiqing/xingyu-music-vault:v0.9
docker tag xingyu-music-vault:v0.9.8 ghcr.io/wangjiqing/xingyu-music-vault:latest

docker tag xingyu-music-vault:v0.9.8 wangjiqing/xingyu-music-vault:v0.9.8
docker tag xingyu-music-vault:v0.9.8 wangjiqing/xingyu-music-vault:v0.9
docker tag xingyu-music-vault:v0.9.8 wangjiqing/xingyu-music-vault:latest
```

## GHCR 手动发布流程

登录 GHCR：

```bash
echo "<GITHUB_TOKEN>" | docker login ghcr.io -u wangjiqing --password-stdin
```

推送镜像：

```bash
docker push ghcr.io/wangjiqing/xingyu-music-vault:v0.9.8
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
docker push wangjiqing/xingyu-music-vault:v0.9.8
docker push wangjiqing/xingyu-music-vault:v0.9
docker push wangjiqing/xingyu-music-vault:latest
```

注意事项：

- Docker Hub 需要提前创建仓库，或账号具备自动创建权限。
- 生产部署推荐使用精确版本 tag。
- Docker Hub 自动发布不在 v0.9.8 范围内，当前仍为手动流程或后续版本处理。
