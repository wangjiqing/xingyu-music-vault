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
| `lyric_drafts` | 歌词草稿记录 | 已实现（v1.3.0，v1.3.2 增加来源字段） |
| `lyric_draft_sources` | 歌词草稿候选来源记录 | 已实现（v1.3.2） |
| `lyric_alignment_jobs` | 歌词草稿提取 / 逐字对齐任务 | 已实现（v1.3.0） |
| `lyric_alignment_job_events` | 歌词对齐审核 / 导入事件 | 已实现（v1.3.0） |
| `lyric_versions` | 歌词多版本管理 | 规划中 |
| `app_settings` | 加密系统设置 | 已实现（v1.3.2，当前用于 Brave Search 托管 Key） |
| `artworks` | 封面图片记录 | 已实现 |
| `music_artwork_bindings` | 音乐文件与封面绑定关系 | 已实现 |
| `metadata_versions` | 元数据版本历史（审计） | 规划中 |
| `scan_jobs` | 扫描任务记录 | 已实现 |
| `match_records` | 自动匹配结果记录 | 规划中 |
| `review_items` | 待审核项（工作流） | 规划中 |
| `settings` | 系统配置键值对 | 规划中 |
| `music_metadata_sync_audit` | 元数据同步审计记录（v0.8.4） | 已实现 |

## 已实现表结构

### tracks

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `title` | text not null | 曲目标题 |
| `normalized_title` | text | 标准化标题，用于基础检索/排序扩展 |
| `artist` | text | 歌手名，当前为扫描解析得到的字符串 |
| `album` | text | 专辑名，当前为扫描解析得到的字符串 |
| `album_artist` | text | 专辑艺人，当前为扫描解析得到的字符串 |
| `year` | integer | 年份 |
| `genre` | text | 流派 |
| `track_no` | integer | 曲目号 |
| `duration` | bigint | 音频时长（秒） |
| `metadata_status` | varchar(32) not null default 'pending' | 元数据状态 |
| `metadata_updated_at` | timestamp | 元数据最近更新时间 |
| `metadata_extracted_at` | timestamp | 最近一次从音频文件提取元数据的时间（v0.8.4） |
| `metadata_source` | text | 元数据来源：`embedded_tag`（文件 Tag）、`database`（用户编辑/网络刮削），由同步操作写入（v0.8.4） |
| `lyrics_status` | varchar(32) not null default 'pending' | 歌词状态 |
| `artwork_status` | varchar(32) not null default 'pending' | 封面状态 |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

状态字段当前允许 `pending`、`matched`、`missing`、`ignored`，`synced` 为 v0.8.4 同步操作后置状态（v0.8.4）。

### music_metadata_sync_audit

记录 v0.8.4 元数据同步操作的完整审计快照，用于后续回滚能力（v0.8.5）。每次覆盖操作写入一条记录，包含操作前后数据库状态和文件 Tag 状态的完整快照。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `batch_id` | text | 批量操作批次 ID，用于关联同批次内的多条审计记录 |
| `music_id` | bigint not null | 对应的音乐记录 ID（外键关联 `track_files.id`） |
| `file_path` | text | 被操作音频文件的绝对路径 |
| `direction` | text not null | 同步方向：`file_to_db`（文件 Tag → 数据库）或 `db_to_file`（数据库 → 文件 Tag） |
| `source_type` | text not null | 来源类型：`embedded_tag`（文件 Tag）或 `database`（数据库） |
| `target_type` | text not null | 目标类型：`embedded_tag`（文件 Tag）或 `database`（数据库） |
| `mode` | text not null | 操作模式，当前为 `overwrite` |
| `operation_type` | text not null | 操作类型，当前为 `OVERWRITE` |
| `before_database_json` | text | 操作前数据库元数据快照（JSON） |
| `after_database_json` | text | 操作后数据库元数据快照（JSON） |
| `before_file_json` | text | 操作前文件 Tag 快照（JSON） |
| `after_file_json` | text | 操作后文件 Tag 快照（JSON） |
| `changed_fields_json` | text | 变更字段列表（JSON 数组） |
| `status` | text not null | 操作结果：`SUCCESS` 或 `FAILED` |
| `error_message` | text | 失败原因（失败时） |
| `rollback_status` | text default 'NOT_ROLLED_BACK' | 回滚状态：`NOT_ROLLED_BACK`（未回滚）、`ROLLED_BACK`（已回滚），v0.8.5 启用 |
| `rollback_of_audit_id` | bigint | 若此记录为回滚操作，则记录被回滚的原始审计记录 ID，v0.8.5 启用 |
| `created_at` | timestamp not null | 审计记录创建时间 |
| `created_by` | text | 操作来源，当前固定为 `api` |

索引：`idx_metadata_sync_audit_music_id`、`idx_metadata_sync_audit_batch_id`、`idx_metadata_sync_audit_created_at`。

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

记录歌词本体。v0.5 仅支持本地 LRC 文件导入，不做在线歌词刮削和逐句时间轴解析。v1.2.4 起，本地歌词扫描优先按规范化后的 `source_path` 复用同一源文件记录，内容哈希作为无同路径记录时的去重兜底。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `title` | text | 歌词标题，优先来自 LRC `[ti:]` 标签 |
| `artist` | text | 歌手，优先来自 LRC `[ar:]` 标签 |
| `album` | text | 专辑，优先来自 LRC `[al:]` 标签 |
| `language` | varchar(16) | 预留语言字段 |
| `release_year` | integer | 预留发行年份字段 |
| `source_type` | varchar(32) not null | 当前为 `LOCAL_FILE` |
| `source_path` | text | 本地歌词文件路径；本地扫描按规范化路径幂等复用 |
| `content` | text not null | 歌词全文 |
| `content_hash` | varchar(64) not null | SHA-256 内容哈希，用于内容去重兜底 |
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

### lyric_drafts

记录歌词草稿文本、状态和来源。v1.3.0 用于 Worker 草稿提取，v1.3.2 增加手工草稿来源语义。

V20 新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `source_type` | varchar(32) not null default `WORKER_EXTRACTION` | 草稿来源：`WORKER_EXTRACTION`、`MANUAL_PASTE`、`BRAVE_ASSISTED` |
| `source_metadata_json` | text | 来源元数据快照；不保存第三方网页正文 |

`MANUAL_PASTE` 手工草稿不会被标记为 Worker 提取结果。`BRAVE_ASSISTED` 只表示草稿关联过候选来源，不表示音库抓取了来源网页正文。

### lyric_draft_sources

记录草稿关联的候选来源元信息。该表只保存来源标题、URL、域名、查询词等追溯信息，不保存第三方歌词网页全文。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `draft_id` | bigint not null | 关联 `lyric_drafts.id`，删除草稿时级联删除来源记录 |
| `provider` | varchar(32) not null | 来源提供方，例如 `BRAVE_SEARCH` |
| `query` | text not null | 搜索词 |
| `title` | text not null | 搜索结果标题 |
| `url` | text not null | 来源 URL |
| `domain` | text not null | 来源域名 |
| `selected_by` | text not null | 关联来源的管理员 |
| `selected_at` | datetime not null | 关联时间 |

### app_settings

记录需要服务端托管的加密设置。v1.3.2 当前用于 Brave Search 控制台托管 API Key。

| 字段 | 类型 | 说明 |
|------|------|------|
| `setting_key` | varchar(128) primary key | 设置键 |
| `setting_value_encrypted` | text | 使用 `MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY` 加密后的设置值 |
| `enabled` | boolean not null default true | 是否启用 |
| `updated_by` | text | 最近更新人 |
| `updated_at` | datetime not null | 最近更新时间 |
| `last_error` | text | 最近一次测试或调用错误摘要，不包含完整 Key |
| `last_checked_at` | datetime | 最近一次测试时间 |

控制台托管 Brave Key 不允许 SQLite 明文保存；未配置 `MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY` 时，保存接口会拒绝请求。环境变量 `MUSIC_VAULT_BRAVE_SEARCH_API_KEY` 优先于该表中的托管配置。

### artworks

记录本地封面图片资产。v0.6 仅支持扫描配置目录中的本地 `jpg/jpeg/png/webp` 文件，不做在线刮削、AI 生成或复杂审核流。文件访问接口只按已入库 `artworks.id` 读取，并要求真实路径仍在 `app.artwork.scan-dir` 配置根目录内。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `file_path` | text not null unique | 真实绝对文件路径 |
| `file_name` | text not null | 文件名 |
| `file_ext` | varchar(16) not null | 小写扩展名 |
| `mime_type` | varchar(64) not null | MIME 类型 |
| `file_size` | bigint not null | 文件大小 |
| `width` | integer | 图片宽度，无法读取时为空 |
| `height` | integer | 图片高度，无法读取时为空 |
| `hash` | varchar(64) not null unique | SHA-256 文件哈希，用于去重 |
| `source_type` | varchar(32) not null | 当前为 `local` |
| `source_path` | text | 来源路径，当前等于本地文件路径 |
| `title` | text | 标题，当前来自文件名去后缀 |
| `description` | text | 描述，预留 |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

>`boundCount`（API 返回）是派生字段，通过 `COUNT(music_artwork_bindings)` 计算，不存储在表中。

### music_artwork_bindings

记录音乐文件与封面图片的绑定关系。v0.6 尚未引入独立 `songs` 表，因此 `music_id` 指向 `track_files.id`，也就是音乐列表 API 暴露的 `id`。后续如引入正式歌曲、专辑、歌手模型，可迁移该外键语义或新增对应绑定表。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | integer primary key autoincrement | 主键 |
| `music_id` | bigint not null | v0.6 对应 `track_files.id` |
| `artwork_id` | bigint not null | 关联 `artworks.id` |
| `relation_type` | varchar(32) not null | 当前为 `track_cover` |
| `is_primary` | boolean not null default true | 是否为当前主封面 |
| `created_at` | timestamp not null | 创建时间 |
| `updated_at` | timestamp not null | 更新时间 |

## 表关系（草稿）

```
artists 1───< albums
albums 1───< tracks
tracks 1───< track_files
track_files 1───< song_lyrics >───1 lyrics
track_files 1───< lyric_alignment_jobs 1───< lyric_drafts 1───< lyric_draft_sources
track_files 1───< music_artwork_bindings >───1 artworks
lyrics 1───< lyric_versions
tracks 1───< metadata_versions
tracks 1───< match_records
tracks 1───< review_items
tracks 1───< music_metadata_sync_audit
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
- `idx_lyric_drafts_source_type` — lyric_drafts 按来源类型查询
- `idx_lyric_draft_sources_draft_id` — lyric_draft_sources 按草稿查询
- `idx_artworks_file_path` — artworks 文件路径唯一去重
- `idx_artworks_hash` — artworks 文件内容哈希唯一去重
- `idx_artworks_source_type` — artworks 来源类型过滤
- `idx_artworks_file_name` — artworks 文件名查询预留
- `idx_music_artwork_bindings_music_id` — music_artwork_bindings 按音乐查询
- `idx_music_artwork_bindings_artwork_id` — music_artwork_bindings 按封面查询
- `idx_music_artwork_bindings_music_artwork_relation` — music_id + artwork_id + relation_type 唯一绑定
- `idx_music_artwork_bindings_primary_music_relation` — 每首音乐每种关系最多一个主封面
- `idx_metadata_sync_audit_music_id` — 审计记录按音乐 ID 查询（v0.8.4）
- `idx_metadata_sync_audit_batch_id` — 审计记录按批次 ID 查询（v0.8.4）
- `idx_metadata_sync_audit_created_at` — 审计记录按创建时间排序（v0.8.4）

## SQLite 注意事项

SQLite 数据库文件（`music-vault.db`）仅用于本地开发。**不要将数据库文件提交到 Git**，`.gitignore` 中应已排除 `*.db` 文件。

## 后续索引规划

- `tracks.title`
- `tracks.artist_id`
- `tracks.album_id`
- `scan_jobs.status`
- `review_items.status`

详细索引与外键约束在功能开发时补全。
