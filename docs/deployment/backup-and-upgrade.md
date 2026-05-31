# 备份与升级

本文档面向 v0.9.6 Docker Compose 部署。当前项目尚未发布正式远程镜像，升级镜像部分是后续镜像发布后的推荐流程。

## 需要备份的内容

至少备份以下目录：

| 目录 | 内容 | 必要性 |
|------|------|--------|
| `DATA_DIR` | SQLite 数据库 `music-vault.db` 与运行数据 | 必须 |
| `CONFIG_DIR` | 本地配置文件 | 建议 |
| `LYRICS_DIR` | 本地 LRC 歌词文件 | 如果使用独立歌词目录则必须 |
| `ARTWORK_DIR` | 封面文件与后台导入封面 | 如果使用封面管理则必须 |
| `MUSIC_DIR` | 原始音乐库 | 不由容器生成，但升级前建议确认已有独立备份 |

`.env` 包含本地路径和 Token，也应备份到安全位置，但不要提交到 Git。

## SQLite 数据库备份

最稳妥方式是先停止服务再复制数据库：

```bash
docker compose down
cp data/music-vault.db "data/music-vault.db.bak-$(date +%Y%m%d-%H%M%S)"
docker compose up -d
```

如果服务运行中备份 SQLite，应同时处理 `-wal` 和 `-shm` 文件，或使用 SQLite 在线备份工具。v0.9.6 推荐停机复制，简单且不容易漏文件。

## 歌词、封面与元数据资源备份

歌词和封面是普通文件，可直接复制对应目录：

```bash
cp -a lyrics "lyrics.bak-$(date +%Y%m%d-%H%M%S)"
cp -a artwork "artwork.bak-$(date +%Y%m%d-%H%M%S)"
```

元数据主要存储在 SQLite 数据库中。若使用了“数据库元数据写回音频文件 Tag”，变更也会写入音乐文件本身，因此升级前建议确认音乐库有独立备份。

## 升级前检查

升级前建议：

- 确认 `docker compose ps` 中服务状态正常
- 执行一次数据库和资源目录备份
- 保存当前镜像标签和 `docker-compose.yml`
- 确认 `.env` 中的路径没有指向临时目录

## 后续镜像发布后的升级方式

当后续发布远程镜像后，推荐流程为：

```bash
docker compose pull
docker compose up -d
docker compose logs -f
```

如果使用本地构建镜像，则使用：

```bash
docker compose build --no-cache
docker compose up -d
```

升级后验证：

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/open/v1/server/info
```

再打开后台页面确认音乐、歌词、封面和 OpenAPI 状态正常。

## 回滚思路

如果升级后异常：

1. 停止当前服务：

```bash
docker compose down
```

2. 将 `docker-compose.yml` 中的镜像标签改回旧版本，或切回旧代码重新构建。

3. 如数据库已被新版本迁移且需要回退，先恢复升级前备份：

```bash
cp data/music-vault.db.bak-YYYYMMDD-HHMMSS data/music-vault.db
```

4. 重新启动：

```bash
docker compose up -d
```

数据库迁移通常不可逆，正式升级前务必先备份 `DATA_DIR`。

