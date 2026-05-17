# 系统架构

## 总体架构

```
┌─────────────────────────────────────────────────────┐
│                    Clients                           │
│         (Web UI / Mobile App / Third-party)          │
└─────────────────┬───────────────────────────────────┘
                  │ HTTP/REST
┌─────────────────▼───────────────────────────────────┐
│              Backend (Quarkus)                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │  Scan    │ │ Metadata │ │  Lyrics  │ │ Artwork │ │
│  │  Engine  │ │  Service │ │  Service │ │ Service │ │
│  └──────────┘ └──────────┘ └──────────┘ └─────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │  Match   │ │  Review  │ │   API    │              │
│  │  Engine  │ │  Workflow│ │  Layer   │              │
│  └──────────┘ └──────────┘ └──────────┘              │
└─────────────────┬───────────────────────────────────┘
                  │ JDBC
┌─────────────────▼───────────────────────────────────┐
│         SQLite (dev) / PostgreSQL (prod)            │
└─────────────────────────────────────────────────────┘
```

## 前后端分离设计

- **后端**：Quarkus，提供 RESTful API，监听 8080 端口
- **前端**：Vue 3 SPA，通过 `/api/*` 调用后端，不做服务端渲染
- **部署**：同进程或反向代理均可，前端静态资源由后端 Serve 或独立 Nginx

## 后端模块规划

| 模块 | 职责 | 状态 |
|------|------|------|
| Scan Engine | 扫描音乐目录，提取音频指纹和元数据 | 规划中 |
| Metadata Service | 歌手/专辑/曲目元数据的 CRUD | 规划中 |
| Lyrics Service | 本地 LRC 导入、存储、歌曲绑定、歌词状态查询 | 已实现基础能力 |
| Artwork Service | 封面刮削、缓存、存储 | 规划中 |
| Match Engine | 自动匹配音乐指纹与元数据源 | 规划中 |
| Review Workflow | 人工审核工作流与状态机 | 规划中 |
| API Layer | 统一 REST 入口，鉴权，日志 | 规划中 |

## 前端页面规划

| 页面 | 路由 | 状态 |
|------|------|------|
| 概览仪表盘 | `/dashboard` | 规划中 |
| 音乐库（列表/搜索） | `/tracks` | 规划中 |
| 歌曲详情 | `/tracks/:id` | 规划中 |
| 歌词管理 | `/lyrics` | 页面占位，后端基础能力已实现 |
| 封面管理 | `/artwork` | 规划中 |
| 待审核项 | `/review` | 规划中 |
| 扫描任务 | `/scan-jobs` | 规划中 |
| 设置 | `/settings` | 规划中 |

## 存储目录规划

```
/app/data/               # 数据目录（容器内）
├── music-vault.db       # SQLite 数据库文件
├── artworks/            # 封面存储
│   └── {track_id}/
├── lyrics/              # 歌词存储
│   └── {track_id}/
└── logs/                # 运行日志

/music                   # 音乐挂载目录（只读，容器内）
```

本地开发目录结构（仓库内占位目录）：

```
project-root/
└── backend/
    ├── data/            # 本地开发数据目录，可用于 MUSIC_VAULT_DATA_DIR
    ├── config/          # 本地开发配置目录，可用于 MUSIC_VAULT_CONFIG_DIR
    └── music/           # 本地开发音乐目录或软链接，可用于 MUSIC_VAULT_MUSIC_DIRS
```

Docker Compose 部署目录结构（相对于 `deploy/docker-compose.yml` 所在目录）：

```
deploy/
├── docker-compose.yml
├── data/                # 挂载到容器 /app/data
└── config/              # 挂载到容器 /app/config
```

## 安全删除设计（v0.7.2）

音乐库根目录是安全边界。删除操作只针对位于音乐库根目录下的普通音乐文件，不允许跨目录、不允许路径穿越。

**回收目录结构：**

```
{musicRoot}/
├── ...音乐文件...
└── .music-vault-trash/      # 回收目录，音乐扫描器忽略此目录
    └── {musicId}/
        └── {originalFileName}
```

**设计要点：**

- 数据库记录 `deletedAt`（删除时间）、`trashPath`（回收目录路径）、`deleteStatus`（`active` / `trashed`）
- 删除前校验文件真实路径位于配置的音乐库根目录内，且必须是普通文件
- 已在 `.music-vault-trash` 下的文件拒绝再次删除
- 回收目录由后端自动创建，无需人工干预
- 音乐扫描器在遍历时忽略 `.music-vault-trash` 目录，避免回收文件再次入库

**非目标：** 恢复功能、彻底删除（物理删除）、批量删除、文件重命名。
