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
| `tracks` | 曲目元数据 | 规划中 |
| `track_files` | 物理音频文件记录（一曲多文件） | 规划中 |
| `lyrics` | 歌词主记录 | 规划中 |
| `lyric_versions` | 歌词多版本管理 | 规划中 |
| `artworks` | 封面图片记录 | 规划中 |
| `metadata_versions` | 元数据版本历史（审计） | 规划中 |
| `scan_jobs` | 扫描任务记录 | 规划中 |
| `match_records` | 自动匹配结果记录 | 规划中 |
| `review_items` | 待审核项（工作流） | 规划中 |
| `settings` | 系统配置键值对 | 规划中 |

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

## 索引规划

- `tracks.title`
- `tracks.artist_id`
- `tracks.album_id`
- `track_files.file_path` (UNIQUE)
- `scan_jobs.status`
- `review_items.status`

详细索引与外键约束在功能开发时补全。
