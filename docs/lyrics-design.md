# 歌词管理设计（v0.5）

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

1. 递归遍历歌词目录，收集所有 `.lrc` 文件
2. 读取文件内容，计算 SHA-256：已存在则跳过（去重）
3. 解析 `[ti:]`、`[ar:]`、`[al:]` 标签；标签缺失时从文件名 `歌手 - 歌名` 兜底
4. 根据 `normalized_title`（音乐扫描时从文件名生成）查找匹配的歌曲：
   - 标题完全匹配（归一化后）→ 候选
   - 标题 + 歌手都匹配 → 优先（`TITLE_ARTIST`，100分）
   - 仅标题匹配 → 兜底（`TITLE`，80分）
5. 若歌曲尚无主歌词绑定，则建立绑定；已有主歌词时跳过（除非请求指定 `overwritePrimary=true`）

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

## v0.5 暂不做

- 在线歌词刮削（LRCLIB、MusicBrainz 等外部服务）
- 歌词编辑器（网页端修改歌词内容）
- 多版本管理（一首歌多个歌词版本的人工审核流程）
- 播放器滚动歌词（逐行时间戳同步）
- 用户登录与权限系统
