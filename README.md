# Xingyu Music Vault / 星语音库

> 音乐元数据管理后台系统，面向个人音乐库、NAS、家庭服务器、自托管环境。

## 项目定位

Xingyu Music Vault 是一个**后台元数据管理系统**，而非播放器。它负责：

- 扫描音乐文件，提取元数据
- 清洗、确认、补充元数据（歌手、专辑、封面、歌词）
- 提供 API 供客户端消费
- 支持自托管环境下的私有部署

## 核心功能

- [ ] 音乐库扫描与指纹识别（规划中）
- [x] 本地音乐扫描与入库（v0.3，文件信息 + 文件名兜底元数据，重复扫描跳过）
- [ ] 歌手/专辑/曲目元数据管理（规划中）
- [ ] 歌词抓取与多版本管理（规划中）
- [ ] 封面刮削与存储（规划中）
- [ ] 元数据审核工作流（规划中）
- [ ] RESTful API 开放（规划中）
- [ ] Web 管理后台（规划中）

## 技术栈

| 层级   | 技术选型                |
|--------|------------------------|
| 后端   | Quarkus (Java 21)      |
| 前端   | Vue 3 + TypeScript      |
| 数据库 | SQLite（开发）/ PostgreSQL（生产） |
| 存储   | 本地文件系统            |
| 部署   | Docker Compose         |

## 仓库结构

```
xingyu-music-vault/
├── backend/           # Quarkus 后端服务
├── frontend/          # Vue 3 管理后台
├── docs/              # 项目文档
├── deploy/            # 部署配置
├── README.md
└── .gitignore
```

## 快速开始

后端本地开发默认扫描目录：

```text
/Users/wangjiqing/Project/Musics
```

启动后端后可触发一次本地音乐扫描：

```bash
curl -X POST http://localhost:8080/api/music/scan \
  -H "Authorization: Bearer change-me" \
  -H "Content-Type: application/json" \
  -d '{}'
```

查询扫描入库结果：

```bash
curl "http://localhost:8080/api/music?page=0&size=20" \
  -H "Authorization: Bearer change-me"
```

## 开发状态

项目处于 **早期开发阶段**，后端 v0.1/v0.2/v0.2.1/v0.3 已完成，核心功能陆续实现中。

```
v0.1 [✓] 后端骨架与 Track CRUD
v0.2 [✓] 音乐库扫描
v0.2.1 [✓] 扫描稳定性与前端体验增强
v0.3 [✓] 本地音乐扫描与入库
v0.4 [ ] 歌词管理
v0.5 [ ] 封面管理
v0.6 [ ] Web 管理后台
v0.7 [ ] 客户端 API
v1.0 [ ] 开源发布稳定版
```
