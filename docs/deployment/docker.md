# Docker 一键部署

本文档面向 v0.9.7 Docker / Docker Compose 本机、NAS、家庭服务器部署。当前不包含公网 HTTPS、域名反向代理、CI/CD、镜像仓库自动发布。若已发布镜像并希望直接拉取部署，请使用 [镜像拉取部署](image-deploy.md)。

## 部署安全边界

当前版本优先面向本机、家庭内网、局域网可信环境。Compose 模板中的 `APP_PORT=8080` 端口映射主要用于本地调试、NAS 或同一局域网内访问，不建议直接映射到公网。

不建议在未配置 HTTPS、反向代理保护、访问控制和网络层限制的情况下开放公网访问。v1.1.2 已提供管理端单管理员初始化、登录 / 登出与 Session Cookie 保护，但仍不提供可作为公网安全方案的完整 OpenAPI Token 与权限模型。

后续 v1.1.x 将继续补充 OpenAPI Token、只读 Token 与写操作权限区分预留、反向代理部署建议、HTTPS / 公网暴露风险说明和 Docker Compose 安全部署示例。当前说明为安全边界提示，不代表这些后续能力已经实现。

## 首次管理员初始化

首次启动后，浏览器访问管理端。如果数据库中还没有管理员账号，前端应进入初始化流程并创建第一个管理员账号。初始化完成后不再开放注册，也不允许创建第二个管理员。

管理端登录成功后，后端通过 `XINGYU_MUSIC_VAULT_SESSION` HttpOnly Cookie 保存 Session。刷新页面会保持登录态，登出后 Session 失效。Session 当前保存在服务端内存中，服务进程重启后所有已登录用户都需要重新登录。

当前版本只支持单管理员，不支持找回密码、OAuth2 / OIDC、NAS 第三方登录或细粒度 RBAC，也尚未实现登录失败次数限制、临时账户锁定或 IP 级登录限流。请继续优先部署在本地 / 局域网可信环境。

默认 `SameSite=Lax` 适合管理端 SPA 与服务端同 origin 的本地 / 局域网部署。如果通过反向代理将管理端暴露在不同域名或复杂跨站访问场景下，应重新评估 CSRF 风险。

OpenAPI Token 是后续独立能力，不与管理端 Session 混用。

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
```

Dockerfile 默认使用 `registry.npmmirror.com` 作为 npm 构建源、`maven.aliyun.com/repository/public` 作为 Maven 构建镜像源，并使用 BuildKit cache 缓存 `/root/.npm` 与 `/root/.m2/repository`。如果网络环境更适合官方源，可在 `.env` 中改为：

```dotenv
NPM_REGISTRY=https://registry.npmjs.org
MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
```

如果启用 OpenAPI 独立认证，请设置：

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

后台管理 API 默认需要管理员登录 Session。健康检查、初始化状态、初始化、登录、登出、静态资源和 OpenAPI 接口不走管理端 Session 保护。

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
  -H "Cookie: XINGYU_MUSIC_VAULT_SESSION=<session-cookie>" \
  -H "Content-Type: application/json" \
  -d '{}'
```

管理端写接口需要先登录；浏览器访问时会自动携带 Session Cookie。命令行验证时可从已登录浏览器请求中复制 `XINGYU_MUSIC_VAULT_SESSION` Cookie。如果 `MUSIC_DIR` 挂载为空目录，服务仍可启动，但扫描不会产生曲目。

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

确认已完成管理员初始化并登录。管理端接口依赖 `XINGYU_MUSIC_VAULT_SESSION` HttpOnly Cookie；如果登录态过期、登出或服务重启后 Session 丢失，需要重新登录。

### 服务重启后需要重新登录

当前版本的管理端 Session 保存在服务端内存中。容器或 Java 进程重启后，已有登录态会全部失效，用户需要重新登录。后续版本可考虑将 Session 持久化到 SQLite。

### 忘记管理员密码怎么办

当前版本不支持找回密码、重置邮件或第二管理员账号。若唯一管理员密码丢失，需要先备份 SQLite 数据库，再在停止服务后删除 `users` 表中的管理员记录，重新启动服务并走首次初始化流程。

示例步骤：

```bash
docker compose down
cp data/music-vault.db data/music-vault.db.bak
sqlite3 data/music-vault.db "delete from users;"
docker compose up -d
```

如果你的数据目录不是 `./data`，请将示例中的数据库路径替换为实际 `MUSIC_VAULT_DB_PATH` 对应的宿主机路径。该操作会清空当前管理员账号，不会删除音乐库、歌词、封面或曲目数据。

### 扫描提示路径不允许

扫描路径必须位于容器内 `MUSIC_VAULT_MUSIC_DIRS` 允许根目录下。标准 Docker 部署中应使用 `/music`，不要在 API 请求里填写宿主机路径。

### 封面 meta 显示不可用

确认封面文件位于容器内 `/artwork` 或允许的音乐根目录内，并且容器有读取权限。后台导入封面需要 `/artwork` 可写。

### 修改 `.env` 后没有生效

重新创建容器：

```bash
docker compose up -d --force-recreate
```
