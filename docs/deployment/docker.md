# Docker 一键部署

本文档面向 v0.9.7 Docker / Docker Compose 本机、NAS、家庭服务器部署。当前不包含公网 HTTPS、域名反向代理、CI/CD、镜像仓库自动发布。若已发布镜像并希望直接拉取部署，请使用 [镜像拉取部署](image-deploy.md)。

## 前置要求

- 已安装 Docker 与 Docker Compose v2
- 宿主机可访问待管理的音乐、歌词、封面目录
- 宿主机端口 `8080` 未被占用，或准备修改 `APP_PORT`

## 目录准备

在仓库根目录准备本地挂载目录：

```bash
mkdir -p data config logs music lyrics artwork
```

目录用途：

| 宿主机目录 | 容器目录 | 建议权限 | 说明 |
|------------|----------|----------|------|
| `./data` | `/app/data` | 读写 | SQLite 数据库与运行数据，必须持久化 |
| `./config` | `/app/config` | 读写 | 预留配置目录 |
| `./logs` | `/app/logs` | 读写 | 可选日志目录，当前主要日志仍输出到 `docker logs` |
| `./music` | `/music` | 默认只读 | 音乐扫描目录 |
| `./lyrics` | `/lyrics` | 默认只读 | 本地歌词扫描目录 |
| `./artwork` | `/artwork` | 读写 | 封面扫描与后台导入封面存储目录 |

如果音乐、歌词、封面目录已经在其他位置，请在 `.env` 中改为实际路径，不要把真实绝对路径提交到仓库。

## 复制配置

```bash
cp docker-compose.example.yml docker-compose.yml
cp .env.example .env
```

编辑 `.env`，至少确认：

```dotenv
APP_PORT=8080
NPM_REGISTRY=https://registry.npmmirror.com
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
DATA_DIR=./data
MUSIC_DIR=./music
LYRICS_DIR=./lyrics
ARTWORK_DIR=./artwork
MUSIC_VAULT_API_TOKEN=change-me
```

Dockerfile 默认使用 `registry.npmmirror.com` 作为 npm 构建源、`maven.aliyun.com/repository/public` 作为 Maven 构建镜像源，并使用 BuildKit cache 缓存 `/root/.npm` 与 `/root/.m2/repository`。如果网络环境更适合官方源，可在 `.env` 中改为：

```dotenv
NPM_REGISTRY=https://registry.npmjs.org
MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
```

部署时请修改 `MUSIC_VAULT_API_TOKEN`。如果启用 OpenAPI 独立认证，请设置：

```dotenv
XINGYU_OPENAPI_AUTH_ENABLED=true
XINGYU_OPENAPI_AUTH_TOKEN=your-openapi-token
```

## 启动服务

首次启动会构建前端、后端和运行镜像：

```bash
docker compose up -d --build
```

后续配置不变时可直接启动；如果只改了少量源码，Docker 会复用 npm / Maven 构建缓存：

```bash
docker compose up -d
```

查看日志：

```bash
docker compose logs -f
```

停止服务：

```bash
docker compose down
```

`docker compose down` 不会删除 bind mount 目录中的 `./data/music-vault.db`。不要使用会删除本地目录的清理脚本。

## 访问后台

浏览器访问：

```text
http://localhost:8080
```

如果部署在局域网主机或 NAS 上：

```text
http://<LAN-IP>:8080
```

后台管理 API 默认需要 Bearer Token，Token 来自 `.env` 中的 `MUSIC_VAULT_API_TOKEN`。

## 验证 OpenAPI

默认 OpenAPI 独立认证关闭，可直接验证：

```bash
curl -i http://localhost:8080/api/open/v1/server/info
curl -i http://localhost:8080/api/open/v1/sync/state
```

如果开启了 `XINGYU_OPENAPI_AUTH_ENABLED=true`：

```bash
curl -i http://localhost:8080/api/open/v1/server/info \
  -H "Authorization: Bearer ${XINGYU_OPENAPI_AUTH_TOKEN}"
```

## 验证挂载路径

确认数据库在持久化目录：

```bash
ls -lh data/music-vault.db
```

确认容器能看到音乐目录：

```bash
docker compose exec xingyu-music-vault ls -la /music
```

触发音乐扫描：

```bash
curl -X POST http://localhost:8080/api/music/scan \
  -H "Authorization: Bearer ${MUSIC_VAULT_API_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{}'
```

如果 `MUSIC_DIR` 挂载为空目录，服务仍可启动，但扫描不会产生曲目。

## 只读与读写挂载

默认 `MUSIC_DIR` 以 `/music:ro` 只读挂载，适合只扫描和读取音乐库，避免容器误改原始音乐文件。

如果需要使用“数据库元数据写回音频文件 Tag”或“删除 / 回收站”相关能力，音乐目录需要读写挂载。请先备份音乐库，再将 `docker-compose.yml` 中这一行：

```yaml
- ${MUSIC_DIR:-./music}:/music:ro
```

改为：

```yaml
- ${MUSIC_DIR:-./music}:/music
```

## 常见问题

### 后台页面能打开，但接口 401

确认浏览器设置页中的 API Token 与 `.env` 的 `MUSIC_VAULT_API_TOKEN` 一致。

### 扫描提示路径不允许

扫描路径必须位于容器内 `MUSIC_VAULT_MUSIC_DIRS` 允许根目录下。标准 Docker 部署中应使用 `/music`，不要在 API 请求里填写宿主机路径。

### 封面 meta 显示不可用

确认封面文件位于容器内 `/artwork` 或允许的音乐根目录内，并且容器有读取权限。后台导入封面需要 `/artwork` 可写。

### 修改 `.env` 后没有生效

重新创建容器：

```bash
docker compose up -d --force-recreate
```
