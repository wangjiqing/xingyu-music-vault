FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

ENV QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8080 \
    MUSIC_VAULT_DATA_DIR=/app/data \
    MUSIC_VAULT_CONFIG_DIR=/app/config \
    MUSIC_VAULT_MUSIC_DIRS=/music \
    MUSIC_VAULT_LYRIC_DIRS=/music \
    MUSIC_VAULT_DB_PATH=/app/data/music-vault.db \
    MUSIC_VAULT_FFPROBE_PATH=/usr/bin/ffprobe \
    MUSIC_VAULT_FFMPEG_PATH=/usr/bin/ffmpeg \
    XINGYU_OPENAPI_AUTH_ENABLED=false \
    XINGYU_OPENAPI_RATE_LIMIT_ENABLED=false \
    XINGYU_OPENAPI_RATE_LIMIT_REQUESTS_PER_MINUTE=120 \
    XINGYU_OPENAPI_ACCESS_LOG_ENABLED=true

RUN mkdir -p /app/data /app/config /music

COPY target/quarkus-app/lib/ ./lib/
COPY target/quarkus-app/*.jar ./
COPY target/quarkus-app/app/ ./app/
COPY target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
