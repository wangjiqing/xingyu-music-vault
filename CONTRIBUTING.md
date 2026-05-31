# Contributing

Thanks for helping improve Xingyu Music Vault.

The project is still before v1.0, so issues and pull requests that clarify deployment, OpenAPI behavior, data safety, and documentation are especially valuable.

## Issues

- Use GitHub Issues for bugs, deployment problems, documentation gaps, and feature discussion.
- Include the version or tag, deployment mode, operating system, and relevant logs.
- Do not attach real `.env` files, database files, music files, tokens, or private paths.

## Pull Requests

- Keep PRs focused on one topic.
- Prefer Chinese PR titles so the project history stays easy to scan.
- Update code and documentation together when behavior, deployment, or configuration changes.
- Add or update tests when changing backend behavior.
- Do not commit real `.env` files, music files, SQLite databases, tokens, or local-only Compose files.

## Local Development

Backend:

```bash
cd backend
mvn test
mvn package
```

Frontend:

```bash
cd frontend
npm ci
npm run build
```

Docker build from the repository root:

```bash
docker build \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com \
  --build-arg MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public \
  -t xingyu-music-vault:dev \
  .
```

Docker Compose local build:

```bash
cp .env.example .env
cp docker-compose.example.yml docker-compose.yml
docker compose up -d --build
```

For image pull deployment, see `docs/deployment/image-deploy.md`.
