# 部署文档

## 部署方式

v1.3.0 提供两种主部署模式：

- 源码构建部署：使用根目录 `Dockerfile`、`docker-compose.example.yml` 与 `.env.example` 本地构建镜像，见 [Docker 一键部署](deployment/docker.md)
- 镜像拉取部署：直接拉取 GHCR / Docker Hub 已发布镜像，见 [镜像拉取部署](deployment/image-deploy.md)

备份和升级策略见 [备份与升级](deployment/backup-and-upgrade.md)。

v0.9.3 已确认后端 Maven 打包、独立 Jar 启动方式，并完成 Docker 镜像构建与容器基础启动验证。当前部署方式面向本机、NAS、家庭服务器和自托管环境，以 Docker Compose 为主。

v1.3.0 继续沿用家庭网络反向代理、HTTPS 与非标准公网端口部署建议，并新增音库 + 歌词 Worker 双容器部署。GHCR 与 Docker Hub 镜像发布说明见 [镜像发布说明](release/image-publish.md)。

## 部署安全边界

当前版本优先面向本机、家庭内网、局域网可信环境部署。Docker Compose 示例中的端口映射适合本地调试、NAS 或同一局域网内的星语音乐盒联调，不建议直接将容器端口映射到公网。

v1.1.4 已在家庭网络反向代理场景验证公网 HTTPS 访问：星语音乐盒在不连接 VPN 的情况下可访问星语音库、播放歌曲并加载歌词。推荐公网链路为：

```text
https://example.com:18443
→ reverse proxy
→ http://192.168.1.100:18081
→ container:8080
```

仍不建议将后端端口直接暴露到公网，例如直接开放 `18081` 或容器 `8080`。公网访问时建议同时启用管理员登录、OpenAPI AK/SK + HMAC、HTTPS 与必要的网络层访问控制；HMAC 只用于请求认证与防重放，不能替代传输加密。

当前文档为自托管部署建议，不代表以下能力已经完成：

- OpenAPI Secret Key 轮换 / 重置流程
- 多用户、细粒度 RBAC 或多租户凭证隔离
- 自动证书申请、DNS 托管或 DDNS 平台封装
- 托管平台一键公网部署

后续版本将继续补齐安全部署能力。公网部署前请按本文清单逐项确认，不应把裸露后端端口视为安全方案。

## 首次管理员初始化

首次启动且数据库中不存在管理员账号时，管理端应先调用初始化状态接口：

```text
GET /api/admin/auth/setup-status
```

当返回 `initialized=false` 时，可初始化第一个管理员账号。初始化完成后入口关闭，后续只能登录，不开放注册，也不允许创建第二个管理员。

```text
POST /api/admin/auth/setup
POST /api/admin/auth/login
POST /api/admin/auth/logout
GET /api/admin/auth/me
```

管理端登录态使用服务端内存 Session 和 `HttpOnly` Cookie 保存。刷新页面会保持登录态；服务进程重启后内存 Session 会丢失，已登录用户需要重新登录。当前版本只支持单管理员，不支持找回密码、OAuth2 / OIDC、NAS 第三方登录或细粒度权限模型。OpenAPI 使用独立 AK/SK + HMAC-SHA256，不与管理端 Session 混用。

当前版本尚未实现登录失败次数限制、临时账户锁定或 IP 级登录限流。请继续优先部署在本地 / 局域网可信环境；后续版本可补充失败计数、临时锁定或登录限流。

默认 `SameSite=Lax` 适合管理端 SPA 与服务端同 origin 的本地 / 局域网部署。如果通过反向代理将管理端暴露在不同域名或复杂跨站访问场景下，应重新评估 CSRF 风险，并结合 HTTPS、访问控制和 Cookie 配置一起处理。

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
MUSIC_VAULT_DB_PATH=/path/to/data/music-vault.db \
MUSIC_VAULT_DATA_DIR=/path/to/data \
MUSIC_VAULT_MUSIC_DIRS=/your/music/path \
MUSIC_VAULT_LYRIC_DIRS=/your/music/path \
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
| 镜像名 | `xingyu-music-vault:${IMAGE_TAG}` |
| 容器端口 | `8080` |
| 宿主机端口 | `18081` |

## Docker 镜像

根目录 `Dockerfile` 是推荐镜像构建入口，会构建前端 Vue 产物并复制到 Quarkus 静态资源目录，再打包后端：

```bash
docker build -t xingyu-music-vault:${IMAGE_TAG:-v1.3.0} .
```

运行时镜像只包含 Quarkus 运行产物、前端静态资源、JRE 21、`ffmpeg` / `ffprobe` 和 `curl`，不包含源码目录、本地音乐文件、SQLite 运行数据或本机缓存。

`backend/Dockerfile` 仍可用于只构建后端运行镜像，构建上下文必须使用 `backend/`，因为它复制的是实际打包产物 `target/quarkus-app`：

```bash
cd backend
mvn package
docker build -t xingyu-music-vault:v1.3.0 .
```

镜像使用 JRE 21 运行环境，包含 `ffmpeg` / `ffprobe`，不内置本地音乐文件，不内置 SQLite 运行数据。运行数据通过 `/app/data` 挂载，音乐目录通过 `/music:ro` 只读挂载。

## Docker Compose

v1.3.0 推荐从仓库根目录复制模板启动：

```bash
cp docker-compose.example.yml docker-compose.yml
cp .env.example .env
docker compose up -d --build
```

根目录 Compose 模板默认映射：

```text
http://localhost:18081
```

并通过 `.env` 配置 `APP_PORT`、`DATA_DIR`、`MUSIC_DIR`、`LYRICS_DIR`、`ARTWORK_DIR`、`ALIGNMENT_JOBS_DIR`、`ALIGNMENT_MODELS_DIR`、`ALIGNMENT_WORKER_IMAGE` 和 OpenAPI 安全配置。歌词 Worker 固定使用 `wangjiqing/xingyu-lyrics-aligner:0.4.0`，命令为 `xingyu-align worker run --jobs-dir /jobs --music-dir /music --device cpu`，Worker 不暴露端口、不挂 Docker Socket，并以 `/music:ro` 只读方式访问音乐目录。

兼容示例文件位于 `deploy/docker-compose.yml`：

```bash
cd deploy
docker compose up --build
```

该示例同样使用根目录 Dockerfile 构建镜像，并映射：

```text
http://localhost:18081
```

请在运行前通过 `.env` 或 shell 环境变量设置 `MUSIC_DIR`、`LYRICS_DIR`、`ARTWORK_DIR` 等路径。

## 本地联调模式

本地联调模式用于 Mac mini / 本机 Docker 场景，方便星语音乐盒真机在同一局域网内访问星语音库 OpenAPI。当前 Mac mini 局域网地址示例为：

```text
http://192.168.1.100:18081
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
| `MUSIC_VAULT_API_TOKEN` | 历史管理 API Token 配置，v1.1.2 管理端登录不再依赖它 | `change-me` |
| `XINGYU_ADMIN_COOKIE_SECURE` | 管理端 Session Cookie 是否启用 Secure，HTTPS 反向代理后可设为 `true` | `false` |
| `XINGYU_ADMIN_COOKIE_SAME_SITE` | 管理端 Session Cookie SameSite 策略 | `Lax` |
| `XINGYU_ADMIN_SESSION_TTL_MINUTES` | 管理端 Session 有效期，单位分钟 | `1440` |
| `XINGYU_ADMIN_TEST_LEGACY_TOKEN_ENABLED` | 测试兼容旧 Bearer Token 分支，生产环境必须保持关闭 | `false` |
| `MUSIC_VAULT_FFPROBE_PATH` | ffprobe 路径 | `/usr/bin/ffprobe` |
| `MUSIC_VAULT_FFMPEG_PATH` | ffmpeg 路径 | `/usr/bin/ffmpeg` |
| `XINGYU_OPENAPI_CREDENTIAL_MASTER_KEY` | OpenAPI Secret Key AES-GCM 加密 master key，生产环境必须改成高强度随机值 | 无 |
| `XINGYU_OPENAPI_HMAC_TIMESTAMP_WINDOW_SECONDS` | OpenAPI HMAC timestamp 允许偏差，单位秒 | `300` |
| `XINGYU_OPENAPI_HMAC_NONCE_TTL_SECONDS` | OpenAPI nonce 防重放保存时间，单位秒 | `600` |
| `XINGYU_OPENAPI_HMAC_MAX_BODY_BYTES` | OpenAPI HMAC 签名前最多读取的请求 body 字节数 | `1048576` |
| `XINGYU_OPENAPI_AUTH_ENABLED` | 旧静态 OpenAPI Token 开关，v1.1.3 起废弃且不再参与认证 | `false` |
| `XINGYU_OPENAPI_AUTH_TOKEN` | 旧静态 OpenAPI Token，v1.1.3 起废弃 | 空 |
| `XINGYU_OPENAPI_RATE_LIMIT_ENABLED` | 是否启用 OpenAPI 简单 IP 限流 | `false` |
| `XINGYU_OPENAPI_RATE_LIMIT_REQUESTS_PER_MINUTE` | 每个客户端 IP 每分钟请求数 | `120` |
| `XINGYU_OPENAPI_ACCESS_LOG_ENABLED` | 是否记录 OpenAPI 访问日志 | `true` |

> 管理端登录密码在首次初始化时设置，不通过 `.env` 配置。生产环境必须保持 `XINGYU_ADMIN_TEST_LEGACY_TOKEN_ENABLED=false`。OpenAPI Secret Key 会加密保存，请务必设置并妥善备份 `XINGYU_OPENAPI_CREDENTIAL_MASTER_KEY`。

`MUSIC_VAULT_MUSIC_DIRS` 是扫描路径安全校验的允许根目录。扫描任务中的 `musicDirs` 必须位于该配置范围内；Docker Compose 场景通常保持 `/music`，并将宿主机真实音乐目录挂载为 `/music:ro`。

Docker 环境下 `MUSIC_VAULT_LYRIC_DIRS` 默认为 `/lyrics`（独立挂载于 `/lyrics:ro`）。如果歌词文件存放在音乐目录下，可改为 `/music` 并调整挂载路径。

OpenAPI 认证只作用于 `/api/open/v1/*`。v1.1.3 起必须使用管理后台创建的 AK/SK + HMAC 签名，不再接受 `Authorization: Bearer <legacy-token>` 或 `X-Xingyu-Api-Token`。

> 升级提醒：`XINGYU_OPENAPI_AUTH_ENABLED` 与 `XINGYU_OPENAPI_AUTH_TOKEN` 已废弃且不再参与认证。升级到 v1.1.3 后必须配置 `XINGYU_OPENAPI_CREDENTIAL_MASTER_KEY`，并重新创建客户端 AK/SK；未适配 HMAC 的客户端请继续使用 v1.1.2 联调。

```http
X-Xingyu-Access-Key: xmv_ak_xxx
X-Xingyu-Timestamp: 1717890000000
X-Xingyu-Nonce: 550e8400-e29b-41d4-a716-446655440000
X-Xingyu-Signature-Version: v1
X-Xingyu-Signature: <lowercase-hex-hmac-sha256>
```

签名原文固定为 5 行：`METHOD`、`PATH_WITH_CANONICAL_QUERY`、`SHA256_HEX_BODY`、`TIMESTAMP`、`NONCE`。query 按参数名升序、同名按值升序并统一 URL 编码；无 body 时使用空字符串 SHA-256。timestamp 默认允许 ±5 分钟偏差，nonce 在有效期内同一 Access Key 只能使用一次。

限流和访问日志同样只作用于 `/api/open/v1/*`。HMAC 不能替代 HTTPS，公网部署仍需 HTTPS / 反向代理 / 网络层限制。

## 反向代理与非标准 HTTPS 端口

家庭宽带环境常见限制是公网 `80` / `443` 端口不可用，或路由器只允许映射非标准端口。v1.1.4 推荐使用非标准 HTTPS 端口暴露反向代理入口，后端服务只监听内网或宿主机端口。

推荐链路：

```text
https://example.com:18443
→ reverse proxy
→ http://192.168.1.100:18081
→ container:8080
```

端口含义：

| 位置 | 示例 | 说明 |
|------|------|------|
| 公网访问地址 | `https://example.com:18443` | 客户端、浏览器和星语音乐盒配置的服务根地址 |
| 反向代理监听 | `18443` | 路由器将公网 `18443` 转发到反向代理 HTTPS 入口 |
| 后端宿主机端口 | `192.168.1.100:18081` | Docker Compose `APP_PORT=18081`，仅供反向代理访问 |
| 容器内部端口 | `8080` | Quarkus 服务端口，保持不变 |

Nginx 示例：

```nginx
server {
    listen 18443 ssl;
    server_name example.com;

    ssl_certificate /path/to/example.com/fullchain.pem;
    ssl_certificate_key /path/to/example.com/privkey.pem;

    location / {
        proxy_pass http://192.168.1.100:18081;
        proxy_set_header Host $host:$server_port;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

部署注意事项：

- 不要把真实域名、公网 IP、内网 IP、证书路径或密钥内容提交到仓库；示例统一使用 `example.com`、`192.168.1.100`、`18443`、`18081`。
- 证书域名必须匹配用户实际访问的域名。`CNAME` 只影响 DNS 解析，不会让目标域名自动继承另一个域名的 TLS 证书。
- 如果访问地址带非标准端口，星语音乐盒和浏览器都应使用完整根地址 `https://example.com:18443`。
- 当前星语音库和星语音乐盒公网访问验证不依赖 WebSocket；反向代理无需额外配置 WebSocket 转发。
- HSTS 建议等 HTTPS 证书、端口映射、客户端访问都稳定后再启用，避免错误配置被浏览器长期缓存。
- 通过 HTTPS 反向代理访问管理端时，可将 `XINGYU_ADMIN_COOKIE_SECURE=true`；若仍通过本地 HTTP 直连调试，则保持 `false`。

## OpenAPI 访问地址

后续客户端接入应配置服务根地址 `musicVaultBaseUrl`，再拼接 OpenAPI 相对路径，不要写死具体主机、端口和完整接口地址。

| 场景 | `musicVaultBaseUrl` 示例 |
|------|--------------------------|
| 本机 | `http://localhost:18081` |
| 家庭内网 | `http://192.168.1.100:18081` |
| 公网 HTTPS | `https://example.com:18443` |

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

- [ ] 修改 `MUSIC_VAULT_API_TOKEN` 为强密码（历史兼容配置）
- [ ] 确认 `XINGYU_ADMIN_TEST_LEGACY_TOKEN_ENABLED=false`
- [ ] 设置 `XINGYU_OPENAPI_CREDENTIAL_MASTER_KEY` 为高强度随机值并妥善备份
- [ ] 在管理后台创建 OpenAPI AK/SK 凭证，并按客户端用途配置 scope
- [ ] 如开启 OpenAPI 限流，确认 `XINGYU_OPENAPI_RATE_LIMIT_REQUESTS_PER_MINUTE` 合理
- [ ] 确认音乐目录路径正确，并以只读方式挂载为 `/music:ro`
- [ ] 确认 `/app/data` 已持久化，SQLite 文件不会随容器删除而丢失
- [ ] 确认宿主机端口 `18081` 未被占用，容器内部端口 `8080` 保持不变
- [ ] 确认客户端只配置 `musicVaultBaseUrl`，接口路径由客户端拼接
