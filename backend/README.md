# Xingyu Music Vault Backend

Phase 0 Quarkus backend skeleton for Xingyu Music Vault / 星语音库.

## Requirements

- JDK 21
- Maven 3.9+
- SQLite database file path writable by the app

## Local Start

```bash
cd backend
mkdir -p /tmp/xingyu-music-vault-data
MUSIC_VAULT_DB_PATH=/tmp/xingyu-music-vault-data/music-vault.db \
MUSIC_VAULT_DATA_DIR=/tmp/xingyu-music-vault-data \
MUSIC_VAULT_API_TOKEN=change-me \
mvn quarkus:dev
```

The service listens on `http://localhost:8080`.

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
