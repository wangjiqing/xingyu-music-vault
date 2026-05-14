# 数据库设计

## 选型策略

- **第一版**：SQLite，开发与轻量部署首选
- **后续扩展**：PostgreSQL，支持更大规模数据与并发

表结构设计与具体 SQL 将在对应功能模块开发时完善。

## 表清单

| 表名 | 说明 | 状态 |
|------|------|------|
| `artists` | 歌手信息 | 规划中 |
| `albums` | 专辑信息 | 规划中 |
| `tracks` | 曲目基础元数据 | 已实现 |
| `track_files` | 物理音频文件记录（一曲多文件） | 已实现 |
| `lyrics` | 歌词主记录 | 已实现 |
| `song_lyrics` | 音乐文件与歌词绑定关系 | 已实现 |
| `lyric_versions` | 歌词多版本管理 | 规划中 |
| `artworks` | 封面图片记录 | 规划中 |
| `metadata_versions` | 元数据版本历史（审计） | 规划中 |
| `scan_jobs` | 扫描任务记录 | 已实现 |
| `match_records` | 自动匹配结果记录 | 规划中 |
| `review_items` | 待审核项（工作流） | 规划中 |
| `settings` | 系统配置键值对 | 规划中 |

## 已实现表结构

### tracks

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `title` | text not null | 曲目标题 |
| `normalized_title` | text | 标准化标题，用于基础检索/排序扩展 |
| `metadata_status` | varchar(32) not null default 'pending' | 元数据状态 |
| `lyrics_status` | varchar(32) not null default 'pending' | 歌词状态 |
| `artwork_status` | varchar(32) not null default 'pending' | 封面状态 |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

状态字段当前允许 `pending`、`matched`、`missing`、`ignored`。

### scan_jobs

记录本地音乐库扫描任务。状态流转为 `pending` -> `running` -> `completed` 或 `failed`。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `job_type` | varchar(64) not null | 当前为 `library_scan` |
| `status` | varchar(32) not null | `pending`、`running`、`completed`、`failed` |
| `music_dirs` | text | 逗号分隔的扫描目录 |
| `total_files` | bigint not null default 0 | 遍历到的普通文件数 |
| `scanned_files` | bigint not null default 0 | 成功记录的音频文件数 |
| `new_files` | bigint not null default 0 | 新增 `track_files` 行数 |
| `updated_files` | bigint not null default 0 | 已存在 `file_path` 的更新次数 |
| `skipped_files` | bigint not null default 0 | 非音频文件或不可用目录等跳过数 |
| `error_files` | bigint not null default 0 | 文件读取/目录遍历错误数 |
| `error_message` | text | 扫描错误或跳过原因汇总 |
| `started_at` | timestamp | 开始时间 |
| `finished_at` | timestamp | 结束时间 |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

### track_files

记录扫描发现的本地音频文件。v0.2 不解析音频内嵌标签，不抓歌词，不抓封面，不做联网匹配。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `track_id` | bigint nullable | 未来关联 `tracks` |
| `file_path` | text not null unique | 绝对文件路径，唯一 |
| `file_name` | text not null | 文件名 |
| `file_ext` | varchar(16) not null | 小写扩展名 |
| `file_size` | bigint not null default 0 | 文件大小 |
| `last_modified_at` | timestamp | 文件最后修改时间 |
| `scan_job_id` | bigint | 最近一次扫描任务 ID |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

### lyrics

记录歌词本体。v0.5 仅支持本地 LRC 文件导入，不做在线歌词刮削和逐句时间轴解析。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `title` | text | 歌词标题，优先来自 LRC `[ti:]` 标签 |
| `artist` | text | 歌手，优先来自 LRC `[ar:]` 标签 |
| `album` | text | 专辑，优先来自 LRC `[al:]` 标签 |
| `language` | varchar(16) | 预留语言字段 |
| `release_year` | integer | 预留发行年份字段 |
| `source_type` | varchar(32) not null | 当前为 `LOCAL_FILE` |
| `source_path` | text | 本地歌词文件路径 |
| `content` | text not null | 歌词全文 |
| `content_hash` | varchar(64) not null | SHA-256 内容哈希，用于去重 |
| `format` | varchar(16) not null | 当前为 `LRC` |
| `parse_status` | varchar(32) not null | 当前为 `PARSED`，预留 `PARSE_FAILED` |
| `parse_message` | text | 解析说明或错误信息 |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

### song_lyrics

记录歌曲与歌词的绑定关系。v0.5 尚未引入独立 `songs` 表，因此这里的 `song_id` 指向 `track_files.id`，也就是音乐列表 API 暴露的 `id`。后续如果引入正式 `songs` 表，需要迁移该外键语义。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `song_id` | bigint not null | v0.5 对应 `track_files.id` |
| `lyric_id` | bigint not null | 关联 `lyrics.id` |
| `match_type` | varchar(32) not null | 当前为 `TITLE` 或 `TITLE_ARTIST` |
| `match_score` | integer not null default 0 | 自动匹配评分 |
| `is_primary` | boolean not null default false | 是否为当前主歌词 |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

## 表关系（草稿）

```
artists 1───< albums
albums 1───< tracks
tracks 1───< track_files
track_files 1───< song_lyrics >───1 lyrics
tracks 1───< artworks
lyrics 1───< lyric_versions
tracks 1───< metadata_versions
tracks 1───< match_records
tracks 1───< review_items
```

## 索引

- `idx_tracks_normalized_title` — normalized_title 查询优化
- `idx_tracks_created_at` — 创建时间排序
- `idx_scan_jobs_status` — scan_jobs 状态查询
- `idx_scan_jobs_created_at` — scan_jobs 创建时间排序
- `idx_track_files_track_id` — track_id 查询优化
- `idx_track_files_scan_job_id` — scan_job_id 查询优化
- `idx_track_files_file_ext` — 扩展名过滤
- `idx_lyrics_content_hash` — lyrics 内容去重
- `idx_lyrics_source_path` — lyrics 来源文件查询
- `idx_lyrics_title_artist` — lyrics 标题/歌手查询
- `idx_lyrics_parse_status` — lyrics 解析状态查询
- `idx_song_lyrics_song_id` — song_lyrics 按歌曲查询
- `idx_song_lyrics_lyric_id` — song_lyrics 按歌词查询
- `idx_song_lyrics_song_lyric` — song_id + lyric_id 唯一绑定
- `idx_song_lyrics_primary_song` — 每首歌最多一个主歌词

## SQLite 注意事项

SQLite 数据库文件（`music-vault.db`）仅用于本地开发。**不要将数据库文件提交到 Git**，`.gitignore` 中应已排除 `*.db` 文件。

## 后续索引规划

- `tracks.title`
- `tracks.artist_id`
- `tracks.album_id`
- `scan_jobs.status`
- `review_items.status`

详细索引与外键约束在功能开发时补全。
