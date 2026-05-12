# Xingyu Music Vault Backend

## v0.1 当前能力

后端 v0.1 已实现以下功能：

- **Track CRUD**：曲目的创建、查询、更新、删除
- **Health API**：服务健康检查
- **Bearer Token 鉴权**：所有 `/api/*` 接口（除 `/api/health` 外）需要 Authorization header
- **SQLite + Flyway**：数据库自动迁移

**本阶段不包含**：音乐扫描、歌词抓取、封面刮削、歌手/专辑管理、登录系统、权限系统。

## Requirements

- JDK 21
- Maven 3.9+
- SQLite database file path writable by the app

## Local Start

```bash
cd backend
mvn quarkus:dev
```

In dev mode the SQLite database is stored at `backend/data/music-vault.db`, and Flyway runs automatically at startup. The service listens on `http://localhost:8080`.

You can override paths with environment variables when needed:

```bash
MUSIC_VAULT_DB_PATH=/tmp/xingyu-music-vault-data/music-vault.db \
MUSIC_VAULT_DATA_DIR=/tmp/xingyu-music-vault-data \
MUSIC_VAULT_API_TOKEN=change-me \
mvn quarkus:dev
```

## Test

```bash
cd backend
mvn test
```

## Build

```bash
cd backend
mvn package
```

This builds a JVM-mode Quarkus app under `target/quarkus-app`. Native Image is intentionally not configured for Phase 0.

## Swagger UI

Swagger UI is always included:

```text
http://localhost:8080/q/swagger-ui
```

## Health Check

`/api/health` is public:

```bash
curl -i http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "ok",
  "service": "xingyu-music-vault",
  "version": "0.1.0"
}
```

## Create Scan Job

All `/api/*` routes except `/api/health` require a bearer token. The default token is `change-me`.

```bash
curl -i -X POST http://localhost:8080/api/scan-jobs \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"jobType":"library_scan","musicDirs":["/music"]}'
```

This only creates a `pending` scan job. Phase 0 does not scan music files, call ffprobe, write metadata, or modify local music files.

## Track CRUD

All `/api/tracks` routes require `Authorization: Bearer change-me` by default.

Create a track:

```bash
curl -i -X POST http://localhost:8080/api/tracks \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"title":"First Song"}'
```

List tracks:

```bash
curl -i http://localhost:8080/api/tracks \
  -H 'Authorization: Bearer change-me'
```

Get one track:

```bash
curl -i http://localhost:8080/api/tracks/1 \
  -H 'Authorization: Bearer change-me'
```

Update a track:

```bash
curl -i -X PUT http://localhost:8080/api/tracks/1 \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Updated Song","metadataStatus":"matched"}'
```

Delete a track:

```bash
curl -i -X DELETE http://localhost:8080/api/tracks/1 \
  -H 'Authorization: Bearer change-me'
```

## Configuration

Environment variables:

| Variable | Default |
| --- | --- |
| `MUSIC_VAULT_DATA_DIR` | `/app/data` |
| `MUSIC_VAULT_CONFIG_DIR` | `/app/config` |
| `MUSIC_VAULT_MUSIC_DIRS` | `/music` |
| `MUSIC_VAULT_DB_PATH` | `/app/data/music-vault.db` |
| `MUSIC_VAULT_API_TOKEN` | `change-me` |
| `MUSIC_VAULT_FFPROBE_PATH` | `/usr/bin/ffprobe` |
| `MUSIC_VAULT_FFMPEG_PATH` | `/usr/bin/ffmpeg` |

The custom config prefix is `music-vault`, read by `MusicVaultConfig`.

## SQLite Note

This skeleton uses `quarkus-jdbc-sqlite` with Hibernate ORM Panache and `org.hibernate.community.dialect.SQLiteDialect`. If a future Quarkus or Hibernate release changes SQLite dialect packaging, keep the Flyway schema as the source of truth and adjust the dialect dependency/config first.

## Docker

```bash
cd backend
mvn package
docker build -t xingyu-music-vault-backend .
docker run --rm -p 8080:8080 \
  -e MUSIC_VAULT_DB_PATH=/app/data/music-vault.db \
  -e MUSIC_VAULT_API_TOKEN=change-me \
  -v "$PWD/data:/app/data" \
  xingyu-music-vault-backend
```
