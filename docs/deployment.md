# 部署文档

## 部署方式

Docker Compose 部署，面向 NAS / 家庭服务器 / 自托管环境。

## 服务配置

| 配置项 | 值 |
|--------|---|
| 服务名 | `music-vault` |
| 镜像名 | `xingyu-music-vault:latest` |
| 容器端口 | `8080` |
| 宿主机端口 | `18080` |

## 目录挂载

`./data` 和 `./config` 均相对于 `docker-compose.yml` 所在目录（当前为 `deploy/`）。

| 宿主机路径 | 容器内路径 | 说明 |
|------------|------------|------|
| `./data` | `/app/data` | 数据目录（含数据库），相对于 `docker-compose.yml` 所在目录 |
| `./config` | `/app/config` | 配置目录，相对于 `docker-compose.yml` 所在目录 |
| `/path/to/music` | `/music:ro` | 音乐目录（只读） |

## 数据库

- SQLite 路径：`/app/data/music-vault.db`
- 初始化由容器启动时自动完成

## 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `TZ` | 时区 | `Asia/Shanghai` |
| `QUARKUS_HTTP_HOST` | 监听地址 | `0.0.0.0` |
| `MUSIC_VAULT_DATA_DIR` | 数据目录 | `/app/data` |
| `MUSIC_VAULT_CONFIG_DIR` | 配置目录 | `/app/config` |
| `MUSIC_VAULT_MUSIC_DIRS` | 音乐目录 | `/music` |
| `MUSIC_VAULT_DB_PATH` | 数据库路径 | `/app/data/music-vault.db` |
| `MUSIC_VAULT_API_TOKEN` | API 鉴权 Token | `change-me` |
| `MUSIC_VAULT_FFPROBE_PATH` | ffprobe 路径 | `/usr/bin/ffprobe` |
| `MUSIC_VAULT_FFMPEG_PATH` | ffmpeg 路径 | `/usr/bin/ffmpeg` |

> 生产环境请务必修改 `MUSIC_VAULT_API_TOKEN`。

## 部署检查清单

- [ ] 修改 `MUSIC_VAULT_API_TOKEN` 为强密码
- [ ] 确认音乐目录路径正确
- [ ] 确认端口 `18080` 未被占用
- [ ] 准备好 PostgreSQL（如需生产级部署）
