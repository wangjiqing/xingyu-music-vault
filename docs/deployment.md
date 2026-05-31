# 部署文档

## 部署方式

v0.9.8 提供两种主部署模式：

- 源码构建部署：使用根目录 `Dockerfile`、`docker-compose.example.yml` 与 `.env.example` 本地构建镜像，见 [Docker 一键部署](deployment/docker.md)
- 镜像拉取部署：直接拉取 GHCR / Docker Hub 已发布镜像，见 [镜像拉取部署](deployment/image-deploy.md)

备份和升级策略见 [备份与升级](deployment/backup-and-upgrade.md)。

v0.9.3 已确认后端 Maven 打包、独立 Jar 启动方式，并完成 Docker 镜像构建与容器基础启动验证。当前部署方式面向本机、NAS、家庭服务器和自托管环境，以 Docker Compose 为主。

本版本不做公网 HTTPS、不做 Docker Hub 自动发布、不做多架构 buildx 强制发布，也不强制完成 NAS 实机部署硬验收。

## 后端打包运行

后端位于 `backend/`，采用 Quarkus JVM 模式打包：

```bash
cd backend
mvn package
```

打包产物目录：

```text
backend/target/quarkus-app
```

独立启动命令：

```bash
cd backend
java -jar target/quarkus-app/quarkus-run.jar
```

如需指定独立运行的数据目录和音乐目录：

```bash
cd backend
MUSIC_VAULT_DB_PATH=/private/tmp/xingyu-music-vault/music-vault.db \
MUSIC_VAULT_DATA_DIR=/private/tmp/xingyu-music-vault \
MUSIC_VAULT_MUSIC_DIRS=/your/music/path \
MUSIC_VAULT_LYRIC_DIRS=/your/music/path \
MUSIC_VAULT_API_TOKEN=change-me \
java -jar target/quarkus-app/quarkus-run.jar
```

启动后可验证：

```bash
curl -i http://localhost:8080/api/open/v1/server/info
curl -i http://localhost:8080/api/open/v1/sync/state
curl -i http://localhost:8080/api/open/v1/tracks/1/lyrics/meta
curl -i http://localhost:8080/api/open/v1/tracks/1/artwork/meta
```

歌词和封面示例使用曲目 ID `1`。如果本地数据库尚无该曲目，接口返回 `404` 也表示路由、认证开关和服务进程可访问。

## 服务配置

| 配置项 | 值 |
|--------|---|
| 服务名 | `xingyu-music-vault` |
| 镜像名 | `xingyu-music-vault:v0.9.8` |
| 容器端口 | `8080` |
| 宿主机端口 | `8080` |

## Docker 镜像

v0.9.8 根目录 `Dockerfile` 是推荐镜像构建入口，会构建前端 Vue 产物并复制到 Quarkus 静态资源目录，再打包后端：

```bash
docker build -t xingyu-music-vault:v0.9.8 .
```

运行时镜像只包含 Quarkus 运行产物、前端静态资源、JRE 21、`ffmpeg` / `ffprobe` 和 `curl`，不包含源码目录、本地音乐文件、SQLite 运行数据或本机缓存。

`backend/Dockerfile` 仍可用于只构建后端运行镜像，构建上下文必须使用 `backend/`，因为它复制的是实际打包产物 `target/quarkus-app`：

```bash
cd backend
mvn package
docker build -t xingyu-music-vault:latest .
```

镜像使用 JRE 21 运行环境，包含 `ffmpeg` / `ffprobe`，不内置本地音乐文件，不内置 SQLite 运行数据。运行数据通过 `/app/data` 挂载，音乐目录通过 `/music:ro` 只读挂载。

## Docker Compose

v0.9.8 推荐从仓库根目录复制模板启动：

```bash
cp docker-compose.example.yml docker-compose.yml
cp .env.example .env
docker compose up -d --build
```

根目录 Compose 模板默认映射：

```text
http://localhost:8080
```

并通过 `.env` 配置 `APP_PORT`、`DATA_DIR`、`MUSIC_DIR`、`LYRICS_DIR`、`ARTWORK_DIR`、`MUSIC_VAULT_API_TOKEN` 和 OpenAPI 安全配置。

兼容示例文件位于 `deploy/docker-compose.yml`：

```bash
cd deploy
docker compose up --build
```

该示例同样使用根目录 Dockerfile 构建镜像，并映射：

```text
http://localhost:8080
```

请在运行前通过 `.env` 或 shell 环境变量设置 `MUSIC_DIR`、`LYRICS_DIR`、`ARTWORK_DIR` 等路径。

## 本地联调模式

本地联调模式用于 Mac mini / 本机 Docker 场景，方便星语音乐盒真机在同一局域网内访问星语音库 OpenAPI。当前 Mac mini 局域网地址示例为：

```text
http://192.168.31.101:8080
```

可提交的模板文件为：

```text
deploy/debugging-docker-compose.example.yml
```

使用时复制成本地文件，并按本机实际路径修改：

```bash
cp deploy/debugging-docker-compose.example.yml deploy/debugging-docker-compose.local.yml
docker compose -f deploy/debugging-docker-compose.local.yml up --build
```

`deploy/debugging-docker-compose.local.yml` 允许包含本机绝对路径、临时 token 或本地调试数据库路径，但不应提交到仓库。`.gitignore` 已忽略 `deploy/*local*.yml` 和 `deploy/data/*.db*`。

本地联调模式下，如果复用已有 SQLite 数据库，容器内挂载路径应尽量与数据库中记录的文件路径一致。例如数据库记录的是宿主机绝对路径 `/path/to/local/music/a.flac`，则可将宿主机音乐目录挂载到容器内相同路径：

```yaml
volumes:
  - /path/to/local/music:/path/to/local/music:ro
environment:
  - MUSIC_VAULT_MUSIC_DIRS=/path/to/local/music
```

这种方式适合临时联调和验证路径兼容性，不适合正式 NAS / 生产部署，也不应把真实音乐目录、数据库或 token 写入可提交文件。

已验证的星语音乐盒局域网联调接口范围：

```text
GET /api/open/v1/server/info
GET /api/open/v1/sync/state
GET /api/open/v1/tracks
GET /api/open/v1/tracks/{id}
GET /api/open/v1/tracks/{id}/lyrics/meta
GET /api/open/v1/tracks/{id}/lyrics
GET /api/open/v1/tracks/{id}/artwork/meta
GET /api/open/v1/tracks/{id}/artwork
GET /api/open/v1/artists
GET /api/open/v1/artists/{artistName}/tracks
GET /api/open/v1/albums
GET /api/open/v1/albums/tracks
GET /api/open/v1/match/track
```

本地联调不新增 OpenAPI 语义，不代表 v0.9.4 业务能力完成，也不包含音频 stream 或客户端写入元数据。

## 正式 Docker / NAS 部署模式

正式 Docker / NAS 部署建议使用标准容器路径，避免依赖开发机绝对路径：

```text
/app/data    # SQLite 与运行数据
/music       # 音乐目录，只读挂载
/app/logs    # 日志目录，如需要文件化日志
```

标准 Compose 示例仍以 `deploy/docker-compose.yml` 为准。正式部署时建议将宿主机音乐目录挂载为 `/music:ro`，设置 `MUSIC_VAULT_MUSIC_DIRS=/music`，并将 SQLite 持久化到 `/app/data/music-vault.db`。如果歌词目录与音乐目录分离，再额外挂载歌词目录并覆盖 `MUSIC_VAULT_LYRIC_DIRS`。

## 目录挂载

`./data` 和 `./config` 均相对于 `docker-compose.yml` 所在目录（当前为 `deploy/`）。

| 宿主机路径 | 容器内路径 | 说明 |
|------------|------------|------|
| `./data` | `/app/data` | 数据目录（含数据库），相对于 `docker-compose.yml` 所在目录 |
| `./config` | `/app/config` | 配置目录，相对于 `docker-compose.yml` 所在目录 |
| `./logs` | `/app/logs` | 日志目录，可选挂载，当前应用主要输出到容器标准输出 |
| `/your/music/path` | `/music:ro` | 音乐目录（只读），需要替换为自己的实际路径 |

Docker Compose / 生产部署建议继续将宿主机音乐目录只读挂载到容器内 `/music:ro`，并将允许扫描目录配置为 `/music`。如需使用其他容器内路径，可通过 `MUSIC_VAULT_MUSIC_DIRS` 覆盖。

## 数据库

- SQLite 路径：`/app/data/music-vault.db`
- 初始化由容器启动时自动完成
- SQLite 文件必须随 `/app/data` 持久化，不应放在镜像内

## 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `TZ` | 时区 | `Asia/Shanghai` |
| `QUARKUS_HTTP_HOST` | 监听地址 | `0.0.0.0` |
| `MUSIC_VAULT_DATA_DIR` | 数据目录 | `/app/data` |
| `MUSIC_VAULT_CONFIG_DIR` | 配置目录 | `/app/config` |
| `MUSIC_VAULT_MUSIC_DIRS` | 音乐目录 | `/music` |
| `MUSIC_VAULT_LYRIC_DIRS` | 歌词目录 | `/lyrics` |
| `MUSIC_VAULT_DB_PATH` | 数据库路径 | `/app/data/music-vault.db` |
| `MUSIC_VAULT_API_TOKEN` | API 鉴权 Token | `change-me` |
| `MUSIC_VAULT_FFPROBE_PATH` | ffprobe 路径 | `/usr/bin/ffprobe` |
| `MUSIC_VAULT_FFMPEG_PATH` | ffmpeg 路径 | `/usr/bin/ffmpeg` |
| `XINGYU_OPENAPI_AUTH_ENABLED` | 是否启用 OpenAPI 独立 Token 认证 | `false` |
| `XINGYU_OPENAPI_AUTH_TOKEN` | OpenAPI 独立 Token，认证开启时必填 | `change-me-openapi` |
| `XINGYU_OPENAPI_RATE_LIMIT_ENABLED` | 是否启用 OpenAPI 简单 IP 限流 | `false` |
| `XINGYU_OPENAPI_RATE_LIMIT_REQUESTS_PER_MINUTE` | 每个客户端 IP 每分钟请求数 | `120` |
| `XINGYU_OPENAPI_ACCESS_LOG_ENABLED` | 是否记录 OpenAPI 访问日志 | `true` |

> 生产环境请务必修改 `MUSIC_VAULT_API_TOKEN` 和 `XINGYU_OPENAPI_AUTH_TOKEN`。

`MUSIC_VAULT_MUSIC_DIRS` 是扫描路径安全校验的允许根目录。扫描任务中的 `musicDirs` 必须位于该配置范围内；Docker Compose 场景通常保持 `/music`，并将宿主机真实音乐目录挂载为 `/music:ro`。

Docker 环境下 `MUSIC_VAULT_LYRIC_DIRS` 默认为 `/lyrics`（独立挂载于 `/lyrics:ro`）。如果歌词文件存放在音乐目录下，可改为 `/music` 并调整挂载路径。

OpenAPI 认证只作用于 `/api/open/v1/*`。当 `XINGYU_OPENAPI_AUTH_ENABLED=true` 时，请求需携带：

```http
Authorization: Bearer <XINGYU_OPENAPI_AUTH_TOKEN>
```

或：

```http
X-Xingyu-Api-Token: <XINGYU_OPENAPI_AUTH_TOKEN>
```

限流和访问日志同样只作用于 `/api/open/v1/*`。

## OpenAPI 访问地址

后续客户端接入应配置服务根地址 `musicVaultBaseUrl`，再拼接 OpenAPI 相对路径，不要写死具体主机、端口和完整接口地址。

| 场景 | `musicVaultBaseUrl` 示例 |
|------|--------------------------|
| 本机 | `http://localhost:8080` |
| Mac mini 局域网 | `http://<Mac-mini-LAN-IP>:8080` |
| NAS | `http://<NAS-LAN-IP>:8080` |

客户端拼接示例：

```text
{musicVaultBaseUrl}/api/open/v1/server/info
```

常用验证接口：

```text
{musicVaultBaseUrl}/api/open/v1/server/info
{musicVaultBaseUrl}/api/open/v1/sync/state
{musicVaultBaseUrl}/api/open/v1/tracks/{id}/lyrics/meta
{musicVaultBaseUrl}/api/open/v1/tracks/{id}/artwork/meta
```

## NAS 目录规划

群晖 NAS 可参考以下目录，实际部署时请按自己的卷名和音乐库位置调整：

```text
/volume1/docker/xingyu-music-vault/data
/volume1/docker/xingyu-music-vault/logs
/volume1/music
```

建议挂载关系：

| NAS 路径 | 容器路径 | 说明 |
|----------|----------|------|
| `/volume1/docker/xingyu-music-vault/data` | `/app/data` | 持久化 SQLite 和运行数据 |
| `/volume1/docker/xingyu-music-vault/logs` | `/app/logs` | 可选日志目录 |
| `/volume1/music` | `/music:ro` | 音乐目录，只读挂载 |

音乐目录建议只读挂载，避免容器误改原始音乐库。SQLite 和运行数据必须持久化到 NAS 数据目录。v0.9.3 只提供目录规划和 Docker 基础验证，不要求完成正式 NAS 实机部署验收。

## 日志

- HTTP access log 已开启，包含请求行、状态码和耗时，便于排查本地 API 调用。
- `com.xingyu.musicvault` 业务日志默认 `INFO`，dev 环境为 `DEBUG`。
- 扫描任务会记录创建、运行、允许扫描根、实际扫描目录、路径校验失败、扫描汇总和异常堆栈。
- 文件级扫描明细仅在 `DEBUG` 下输出，避免生产日志刷屏。
- 日志不会打印 Authorization header、Token、password 或 secret。

## 部署检查清单

- [ ] 修改 `MUSIC_VAULT_API_TOKEN` 为强密码
- [ ] 如开启 OpenAPI 认证，设置 `XINGYU_OPENAPI_AUTH_ENABLED=true` 并修改 `XINGYU_OPENAPI_AUTH_TOKEN`
- [ ] 如开启 OpenAPI 限流，确认 `XINGYU_OPENAPI_RATE_LIMIT_REQUESTS_PER_MINUTE` 合理
- [ ] 确认音乐目录路径正确，并以只读方式挂载为 `/music:ro`
- [ ] 确认 `/app/data` 已持久化，SQLite 文件不会随容器删除而丢失
- [ ] 确认端口 `8080` 未被占用，或按需调整宿主机端口映射
- [ ] 确认客户端只配置 `musicVaultBaseUrl`，接口路径由客户端拼接
