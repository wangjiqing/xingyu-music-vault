# syntax=docker/dockerfile:1

FROM node:22-bookworm-slim AS frontend-build

ARG NPM_REGISTRY=https://registry.npmmirror.com

WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --registry=${NPM_REGISTRY}
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build

ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public

WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml

WORKDIR /workspace/backend
RUN mkdir -p /root/.m2 \
    && printf '%s\n' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>build-mirror</id>' \
      '      <mirrorOf>central</mirrorOf>' \
      "      <url>${MAVEN_MIRROR_URL}</url>" \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' \
      > /root/.m2/settings.xml
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -DskipTests dependency:go-offline

WORKDIR /workspace
COPY backend/src backend/src
COPY --from=frontend-build /workspace/frontend/dist backend/src/main/resources/META-INF/resources

WORKDIR /workspace/backend
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B package -DskipTests

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
    MUSIC_VAULT_LYRIC_DIRS=/lyrics \
    MUSIC_VAULT_DB_PATH=/app/data/music-vault.db \
    MUSIC_SCAN_DEFAULT_PATH=/music \
    APP_ARTWORK_SCAN_DIR=/artwork \
    MUSIC_VAULT_FFPROBE_PATH=/usr/bin/ffprobe \
    MUSIC_VAULT_FFMPEG_PATH=/usr/bin/ffmpeg

RUN mkdir -p /app/data /app/config /app/logs /music /lyrics /artwork

COPY --from=backend-build /workspace/backend/target/quarkus-app/lib/ ./lib/
COPY --from=backend-build /workspace/backend/target/quarkus-app/*.jar ./
COPY --from=backend-build /workspace/backend/target/quarkus-app/app/ ./app/
COPY --from=backend-build /workspace/backend/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
