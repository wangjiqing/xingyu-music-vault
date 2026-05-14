# 歌词管理设计（v0.5 / v0.5.2）

## 当前状态

v0.5 已完成歌词管理后端基础能力：

- 本地 LRC 文件扫描
- 歌词入库与内容 SHA-256 去重
- 歌词与歌曲自动绑定
- 音乐列表展示歌词状态
- 歌词状态查询接口

v0.5.1 聚焦稳定性修复与文档整理：

- 检查并规避 Flyway V5 迁移 SQL 注释风险
- 整理 README、后端 README 和歌词设计文档的状态描述
- 补齐本地验证说明
- 为后续歌词管理页面做准备

v0.5.2 补齐前端「歌词管理」独立页面 MVP：

- 歌词列表分页查询（支持 keyword / bindStatus / parseStatus / sourceType 筛选）
- 歌词详情查询（包含歌词原文和全部绑定歌曲）
- 从页面触发歌词扫描
- 查看歌词 LRC 原文
- 绑定状态、解析状态、来源类型展示
- 关联歌曲信息展示（标题 + 歌手 + 匹配方式 + 匹配分数）

## 数据模型

### lyrics 表

存储歌词原文和元数据。同一歌词内容（SHA-256 去重）只存一条记录。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `title` | 歌词标题（从 `[ti:]` 标签或文件名解析） |
| `artist` | 歌手（从 `[ar:]` 标签或文件名解析） |
| `album` | 专辑（从 `[al:]` 标签解析） |
| `language` | 语言（预留，v0.5 为空） |
| `release_year` | 发行年份（预留，v0.5 为空） |
| `source_type` | 来源类型，目前固定为 `LOCAL_FILE` |
| `source_path` | 原始 LRC 文件路径 |
| `content` | 歌词全文（LRC 格式） |
| `content_hash` | `content` 的 SHA-256，用于去重 |
| `format` | 格式，固定为 `LRC` |
| `parse_status` | 解析状态，`PARSED` 或 `PARSE_FAILED` |
| `parse_message` | 解析失败时的错误信息 |

### song_lyrics 表

歌词与歌曲的多对多绑定表。一首歌可以有多条歌词记录，通过 `is_primary` 标记主歌词。
v0.5 尚未引入正式 `songs` 表，`song_id` 当前对应 `track_files.id`，也就是 `GET /api/music` 返回的音乐列表行 ID。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `song_id` | 歌曲 ID（目前对应 `track_files.id`） |
| `lyric_id` | 歌词 ID（关联 `lyrics.id`） |
| `match_type` | 匹配类型：`TITLE`（80分）或 `TITLE_ARTIST`（100分） |
| `match_score` | 匹配得分（0-100） |
| `is_primary` | 是否为主歌词，每首歌只能有一条主歌词 |
| `created_at` | 绑定时间 |

### 关系

```
lyrics (1) ←→ (N) song_lyrics (N) ←→ (1) track_files (as song)
```

V5 migration 已随 v0.5 发布。v0.5.1 检查确认 `V5__create_lyrics.sql` 内说明文字均为合法 SQL 注释；除非需要新建后续 migration，不应改动已发布的 V5 文件内容，以免已有本地数据库出现 Flyway checksum mismatch。

## 歌词状态（LyricStatus）

`GET /api/music` 和 `GET /api/songs/{songId}/lyrics` 返回 `lyricStatus` 字段：

| 状态 | 含义 |
|------|------|
| `BOUND` | 已绑定歌词，状态正常 |
| `NO_LYRIC` | 无歌词（song_lyrics 无记录） |
| `UNMATCHED` | 歌词文件已导入但未找到匹配的歌曲（仅扫描统计语义，不写入此状态） |
| `PARSE_FAILED` | 歌词解析失败 |
| `MISSING_FILE` | 歌词文件已不存在（source_path 指向的 LRC 被删除） |

## 本地 LRC 扫描流程

首次导入或旧库升级时，请先执行音乐扫描，再执行歌词扫描。歌词自动绑定依赖音乐扫描生成的 `tracks.normalized_title`；如果历史 `track_files.track_id` 为空，重复音乐扫描会补齐它。

1. 执行 `POST /api/music/scan`，等待扫描任务 `completed`
2. 执行 `POST /api/lyrics/scan`
3. 歌词扫描递归遍历歌词目录，收集所有 `.lrc` 文件
4. 读取文件内容，计算 SHA-256：已存在则跳过重复入库，但仍可继续尝试绑定
5. 解析 `[ti:]`、`[ar:]`、`[al:]` 标签；标签缺失时从文件名 `歌手 - 歌名` 兜底
6. 根据 `normalized_title`（音乐扫描时从文件名生成）查找匹配的歌曲：
   - 标题完全匹配（归一化后）→ 候选
   - 标题 + 歌手都匹配 → 优先（`TITLE_ARTIST`，100分）
   - 仅标题匹配 → 兜底（`TITLE`，80分）
7. 若歌曲尚无主歌词绑定，则建立绑定；已有主歌词时跳过（除非请求指定 `overwritePrimary=true`）

## API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/lyrics` | 歌词列表分页查询（v0.5.2），支持 keyword / bindStatus / parseStatus / sourceType |
| GET | `/api/lyrics/{id}` | 歌词详情查询（v0.5.2），返回歌词原文、绑定歌曲摘要和全部绑定记录 |
| POST | `/api/lyrics/scan` | 扫描本地 LRC 歌词目录并尝试绑定歌曲 |
| GET | `/api/songs/{songId}/lyrics` | 获取音乐列表中某首歌的主歌词 |

`GET /api/music` 每行返回 `lyricStatus` 和 `lyricId`，用于音乐列表展示歌词状态。

### 歌词管理页状态字段

`GET /api/lyrics` 和 `GET /api/lyrics/{id}` 面向歌词管理页返回页面级状态：

| 字段 | 值 | 说明 |
|------|----|------|
| `bindStatus` | `BOUND` / `UNBOUND` | 是否已绑定歌曲 |
| `parseStatus` | `SUCCESS` / `FAILED` / `UNKNOWN` | 页面展示用解析状态，内部 `PARSED` 映射为 `SUCCESS`，`PARSE_FAILED` 映射为 `FAILED` |
| `sourceType` | `LOCAL_FILE` / `MANUAL` / `ONLINE` | 当前实际写入 `LOCAL_FILE`，其余为后续预留 |

列表接口不返回 `content`，避免页面加载时拉取全部歌词全文；查看详情时再调用 `GET /api/lyrics/{id}`。

## 配置

歌词扫描目录在 `application.yml` 中配置：

```yaml
music-vault:
  lyric-dirs:
    - /Users/wangjiqing/Project/Musics/Lyrics
```

也可以通过环境变量覆盖：

```bash
MUSIC_VAULT_LYRIC_DIRS=/path/to/lyrics
```

## v0.5.2 歌词管理页面

访问路径：`/lyrics`

当前能力：

- 查看歌词列表（标题、歌手、专辑、来源类型、绑定状态、解析状态、关联歌曲、匹配方式、文件路径、更新时间）
- 筛选条件：关键词（标题/歌手/专辑/文件名）、绑定状态、解析状态、来源类型
- 分页浏览（每页 10/20/50/100 条）
- 点击「扫描歌词」触发歌词扫描（同步返回，显示匹配统计）
- 点击「查看歌词」弹窗展示歌词详情（LRC 原文、全部绑定歌曲、匹配分数）
- 点击「刷新」重新加载列表
- 加载中 / 空状态 / 错误提示

v0.5.2 暂不做：

- 歌词编辑器（手动修改 LRC 内容）
- 手动绑定（人工选择歌词与歌曲的对应关系）
- 在线歌词刮削（依赖外部 LRCLIB / MusicBrainz 等服务）
- 多版本审核（同一歌曲多条歌词的优先选择）
- 歌词逐句时间轴编辑
- 播放器滚动歌词（当前仅元数据管理，无播放能力）
- 用户系统与权限控制
- 云端同步

## 后续版本计划

- v0.5.x：继续补齐歌词扫描边界场景、人工复核入口和未匹配结果查看。
- v0.6：封面管理基础能力。
- 后续：在线歌词刮削（LRCLIB、MusicBrainz 等外部服务）、歌词编辑器、多版本审核、播放器滚动歌词、用户登录与权限系统。

关于歌手、专辑、曲目元数据扩展：

歌词、歌曲、专辑、歌手模型设计已预留 `release_year` / `language` 等字段（v0.5.2 中 `releaseYear` 已出现在 lyrics 表和 API 响应中，但歌词解析暂未填充）。后续歌手、专辑、曲目元数据管理会考虑这些年代字段。当前数据模型不应阻碍此类扩展。
