# Xingyu Music Vault / 星语音库 - 前端管理后台

## 技术栈

- Vue 3 + TypeScript + Vite
- Element Plus
- Vue Router
- Axios

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | API 基础地址 | `''`（空字符串，使用 Vite 代理） |

创建 `.env.local` 覆盖默认值：

```env
VITE_API_BASE_URL=http://your-api-server:8080
```

## API Token 配置

1. 启动项目后导航到 **系统设置** 页面
2. 输入 API Token 并点击保存
3. Token 存储在浏览器 localStorage 中，所有 API 请求会自动附带 `Authorization: Bearer <token>` 请求头

## 页面路由

默认地址：`http://localhost:5173`

| 路径 | 页面 |
|------|------|
| `/music` | 音乐库列表（v0.4 MVP + v0.5 歌词） |
| `/scan-jobs` | 扫描任务列表 |
| `/track-files` | 音乐文件记录 |
| `/settings` | 系统设置（Token 配置） |

## 音乐库页面

访问路径：`/music`

当前能力：

- 查看音乐列表（歌曲名、歌手、专辑、格式、大小、时长、文件名、文件路径、歌词状态）
- 分页浏览（每页 10/20/50/100 条）
- 点击「扫描音乐目录」触发音乐扫描任务（异步，不阻塞）
- 点击「扫描歌词」触发歌词扫描（同步返回，显示匹配统计）
- 歌词状态 Tag：BOUND / NO_LYRIC / UNMATCHED / PARSE_FAILED / MISSING_FILE
- BOUND 状态歌曲支持「查看歌词」弹窗，展示 LRC 原文
- 点击「刷新」重新加载列表
- 加载中 / 空状态 / 错误提示

### 对接的 API

| Method | Path | 用途 |
|--------|------|------|
| GET | `/api/music?page=0&size=20` | 音乐列表（含 lyricStatus / lyricId） |
| POST | `/api/music/scan` | 触发音乐扫描 |
| POST | `/api/lyrics/scan` | 触发歌词扫描 |
| GET | `/api/songs/{songId}/lyrics` | 获取歌曲歌词详情 |

> 注意：`POST /api/music/scan` 为异步任务风格。点击扫描后任务已提交，不代表扫描已完成。请稍后点击「刷新」查看最新数据。

Vite 开发服务器默认将 `/api` 和 `/q` 请求代理到 `http://localhost:8080`。

与 Quarkus 后端对联时，确保后端运行在 `8080` 端口。
