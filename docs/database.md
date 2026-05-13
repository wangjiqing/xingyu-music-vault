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
| `lyrics` | 歌词主记录 | 规划中 |
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

## 表关系（草稿）

```
artists 1───< albums
albums 1───< tracks
tracks 1───< track_files
tracks 1───< lyrics
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

## SQLite 注意事项

SQLite 数据库文件（`music-vault.db`）仅用于本地开发。**不要将数据库文件提交到 Git**，`.gitignore` 中应已排除 `*.db` 文件。

## 后续索引规划

- `tracks.title`
- `tracks.artist_id`
- `tracks.album_id`
- `scan_jobs.status`
- `review_items.status`

详细索引与外键约束在功能开发时补全。
