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

v1.2.3 / v1.2.4 补齐本地歌词源文件同步闭环：

- v1.2.3：完整成功扫描后，以 lyricsDir 作为本地 LRC 可用性来源，删除源 `.lrc` 后会解除旧 `song_lyrics` 绑定，OpenAPI 随后返回无歌词。
- v1.2.4：恢复或更新同一路径 `.lrc` 时，扫描优先按规范化 `source_path` 复用既有 `lyrics` 记录，刷新正文、内容哈希和解析元数据，并重新匹配歌曲恢复绑定。
- v1.2.4：歌词管理页允许管理员删除真正不再需要的未绑定歌词记录；删除只清理数据库记录，不删除磁盘 `.lrc` 文件。

v1.3.0 引入歌词草稿提取、歌词对齐任务、人工审核与确认导入闭环：

- 音库只负责创建共享目录任务、同步 Worker 状态、展示结果、记录人工审核，并在管理员确认后导入结果。
- 草稿提取任务读取本地音频，Worker 生成未对齐候选文本；只有管理员人工确认后才会新建 `DRAFT_CONFIRMED` 来源可信歌词资产。
- Worker 通过共享任务目录生成 `lyrics.lrc`、`lyrics.swlrc`、`alignment.json` 和 `report.json`；音库不在容器内安装 WhisperX、PyTorch 或对齐依赖。
- 审核通过不会自动替换正式歌词，只有确认导入后才会新建 `ALIGNMENT` 来源的受控歌词资产。
- 原始可信歌词保留，不删除、不覆盖；导入后的 LRC 作为当前可用歌词，SWLRC 作为可选逐字歌词附加资产。

v1.3.2 优化歌词工作台体验：

- 支持跳过 Worker 草稿提取，直接粘贴歌词文本创建 `LYRIC_DRAFT_MANUAL` 手工草稿。
- 手工草稿使用 `MANUAL_PASTE` 来源语义，不伪装为 Worker 提取结果，不创建 Worker `READY` 信号。
- Brave Search 只用于候选来源发现，可将来源标题、URL、域名、查询词等元信息关联到草稿；音库不抓取、不下载、不缓存第三方歌词网页全文。
- 管理端工作台可读取 SWLRC 做逐字歌词试听；无 SWLRC、读取失败或解析失败时回退 LRC 行级歌词。
- 工作台质量摘要、质量报告和 Worker 状态摘要增加中文解释，但不实现 Worker `progress.json`、SSE、文件监听或伪进度。

## 数据模型

### lyrics 表

存储歌词原文和元数据。v1.2.4 起，扫描导入优先使用规范化后的 `source_path` 识别同一源文件；内容 SHA-256 仍作为无同路径记录时的去重兜底。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `title` | 歌词标题（从 `[ti:]` 标签或文件名解析） |
| `artist` | 歌手（从 `[ar:]` 标签或文件名解析） |
| `album` | 专辑（从 `[al:]` 标签解析） |
| `language` | 语言（预留，v0.5 为空） |
| `release_year` | 发行年份（预留，v0.5 为空） |
| `source_type` | 来源类型；本地扫描为 `LOCAL_FILE`，草稿确认后为 `DRAFT_CONFIRMED`，对齐导入为 `ALIGNMENT` |
| `source_path` | 原始 LRC 文件路径；本地扫描按规范化后的路径做幂等复用 |
| `content` | 歌词全文（LRC 格式） |
| `content_hash` | `content` 的 SHA-256，用于内容去重兜底 |
| `format` | 格式；本地扫描和对齐导入为 `LRC`，草稿确认可信歌词为 `TEXT` |
| `parse_status` | 解析状态，`PARSED` 或 `PARSE_FAILED` |
| `parse_message` | 解析失败时的错误信息 |
| `source_task_id` | 来源任务 ID；`DRAFT_CONFIRMED` 指向草稿任务，`ALIGNMENT` 指向对齐任务 |
| `source_draft_id` | 草稿确认来源 `lyric_drafts.id`；仅 `DRAFT_CONFIRMED` 来源写入 |
| `source_text_hash` | 草稿确认时 editable text 的 SHA-256；用于追溯草稿确认输入 |
| `parent_lyrics_id` | 对齐任务使用的原始可信歌词 ID，用于追溯，不表示覆盖 |
| `swlrc_path` | 导入后的受控 SWLRC 文件相对路径；没有逐字歌词的旧记录为空 |
| `swlrc_hash` | 导入时 SWLRC 文件 SHA-256 |
| `confirmed_at` | 对齐结果确认导入时间 |
| `confirmed_by` | 执行确认导入的管理员 |

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

### lyric_drafts 来源字段（v1.3.2）

`lyric_drafts` 保存草稿文本、状态和来源语义。v1.3.2 新增来源字段：

| 字段 | 说明 |
|------|------|
| `source_type` | 草稿来源，当前使用 `WORKER_EXTRACTION`、`MANUAL_PASTE`、`BRAVE_ASSISTED` |
| `source_metadata_json` | 来源元数据快照；手工草稿记录来源语义，Brave 辅助来源通过独立来源表追溯 |

`WORKER_EXTRACTION` 表示草稿来自 Worker 产物；`MANUAL_PASTE` 表示用户直接粘贴创建；`BRAVE_ASSISTED` 表示草稿整理过程关联过 Brave 候选来源。该字段不表示歌词内容已自动抓取。

### lyric_draft_sources 表（v1.3.2）

`lyric_draft_sources` 保存草稿关联的候选来源元信息，不保存第三方网页正文。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `draft_id` | 关联 `lyric_drafts.id` |
| `provider` | 来源提供方，例如 `BRAVE_SEARCH` |
| `query` | 搜索词 |
| `title` | 搜索结果标题 |
| `url` | 来源 URL |
| `domain` | 来源域名 |
| `selected_by` | 关联来源的管理员 |
| `selected_at` | 关联时间 |

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
3. 歌词扫描递归遍历歌词目录，收集所有实际存在的 `.lrc` 文件
4. 读取文件内容，规范化 `source_path`，优先查找同一路径的既有歌词记录
5. 若同一路径记录存在，则复用该 `lyrics` 行，更新正文、内容哈希、标题、歌手、专辑、解析状态和更新时间
6. 若同一路径记录不存在，再使用内容 SHA-256 查找重复内容记录；命中时刷新其本地来源路径和解析元数据
7. 未命中同路径或同内容记录时，新建 `lyrics` 行
8. 解析 `[ti:]`、`[ar:]`、`[al:]` 标签；标签缺失时从文件名 `歌手 - 歌名` 兜底
9. 根据 `normalized_title`（音乐扫描时从文件名生成）查找匹配的歌曲：
   - 标题完全匹配（归一化后）→ 候选
   - 标题 + 歌手都匹配 → 优先（`TITLE_ARTIST`，100分）
   - 仅标题匹配 → 兜底（`TITLE`，80分）
10. 若歌曲尚无主歌词绑定，则建立绑定；已有主歌词时跳过（除非请求指定 `overwritePrimary=true`）
11. 仅当本次歌词目录完整扫描且所有文件处理成功后，检查当前扫描目录范围内的 `LOCAL_FILE` 记录；若 `source_path` 对应的 LRC 已不存在，则解除相关 `song_lyrics` 绑定，使 OpenAPI 后续返回无歌词。旧 `lyrics` 记录可保留为无绑定历史记录；再次放回或生成同一路径 LRC 后，重新点击「扫描歌词」会复用原记录并恢复绑定，不会插入同路径重复行。
12. 历史数据中若存在同一规范化 `source_path` 的重复记录，扫描仅会在能确认重复记录未绑定时清理；无法安全判断的记录保留给管理员手动处理。

## v1.3.0 草稿提取确认流程

草稿提取任务使用 `lyric_alignment_jobs.task_type=LYRIC_DRAFT_EXTRACTION` 持久化，任务不需要 `lyric_id`、`trusted_lyrics_hash` 或 `trusted_lyrics_snapshot`。音库写入 v2 `request.json`，其中 `taskType=LYRIC_DRAFT_EXTRACTION`，只包含音频路径、输出目录、语言、CPU 设备和 ASR 选项，最后创建 `READY`。

Worker 成功后，音库读取 `result/transcript.cleaned.txt`，保存为 `lyric_drafts.original_text` 原始快照，并初始化 `editable_text`。草稿状态为 `PENDING_REVIEW`、`EDITING`、`CONFIRMED`、`REJECTED`。`original_text` 不允许被覆盖；人工保存只更新 `editable_text` 和 hash。

管理员确认草稿后，音库把 `editable_text` 写入受控歌词资产目录，并创建 `lyrics.source_type=DRAFT_CONFIRMED` 记录。该记录是后续逐字对齐的可信输入资产，不会自动绑定为当前主歌词，不会替换当前 LRC / SWLRC，也不会自动创建对齐任务。

## v1.3.2 手工草稿与 Brave 来源辅助

手工草稿流程：

```text
用户粘贴歌词文本
→ 音库创建 LYRIC_DRAFT_MANUAL 任务和 MANUAL_PASTE 草稿
→ 用户编辑 / 保存
→ 用户确认可信歌词
→ DRAFT_CONFIRMED 可信歌词
→ 用户手动创建逐字对齐任务
```

服务端只做 BOM、换行和首尾空白规范化，不改写、不翻译、不补全歌词内容。手工草稿任务状态为已完成，但不是 Worker 已完成任务，不会被同步器读取 Worker 状态。

Brave 来源辅助流程：

```text
管理员配置 Brave Key
→ 音库后端代理调用 Brave Search API
→ 返回候选来源标题 / URL / 域名 / 摘要
→ 用户打开外部网页自行查看和复制
→ 用户粘贴歌词到草稿
→ 可把候选来源元信息关联到草稿
```

该流程不采集第三方网页正文，`description` 搜索摘要也不会写入歌词正文。来源关联仅用于后续追溯。

## v1.3.0 对齐审核导入流程

歌词对齐任务不属于本地 LRC 扫描流程，也不直接修改既有可信歌词记录。任务创建时会保存可信歌词快照、可信歌词 hash、音频相对路径和请求快照，并在共享任务目录写入 `request.json`、`trusted-lyrics.txt`、可选 `sections.json`，最后创建 `READY`。

Worker 完成后，音库同步任务状态和结果摘要。`SUCCEEDED` 与 `NEEDS_REVIEW` 都映射为任务执行完成，人工审核状态仍为 `PENDING`；`NEEDS_REVIEW` 只是 Worker 执行结论，不代表人工审核已完成。

管理员审核通过后，仍需再次确认导入。导入时音库从任务 `result` 目录读取 `lyrics.lrc` 与 `lyrics.swlrc`，校验当前文件 hash 与同步保存的 hash 一致，再发布到歌词挂载目录下的受控 `alignment/{songId}/{jobId}` 子目录。正式歌词记录不会长期引用 `alignment-jobs` 中间目录。

v1.3.1 起，新的 `ALIGNMENT` LRC / SWLRC 会从 Worker jobs 中间目录发布到 `MUSIC_VAULT_ALIGNMENT_LYRICS_ROOT` 下的受控 alignment 子目录；普通歌词扫描会排除该目录，删除同步也只处理 `LOCAL_FILE`。旧 `alignment-assets-dir` 中的历史正式对齐资产不自动迁移、不删除，后续需要通过显式迁移方案复制文件、校验 hash 后再更新数据库路径。

导入成功后：

- 新建 `lyrics.source_type=ALIGNMENT` 的 LRC 记录，`content` 保存导入后的 LRC 正文。
- `source_task_id` 指向对齐任务 UUID，`parent_lyrics_id` 指向任务创建时使用的可信歌词。
- `swlrc_path` 与 `swlrc_hash` 保存逐字歌词附加资产。
- 新记录绑定为歌曲主歌词，使既有 LRC 播放接口继续返回当前可用 LRC。
- 旧可信歌词资产保留为历史记录，不删除、不覆盖。

没有导入 SWLRC 的旧歌曲继续按原有 LRC 行为工作。OpenAPI 新增逐字歌词可选能力，但旧 LRC 消费者不需要感知 SWLRC 字段。

## API

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/lyrics` | 歌词列表分页查询（v0.5.2），支持 keyword / bindStatus / parseStatus / sourceType |
| GET | `/api/lyrics/{id}` | 歌词详情查询（v0.5.2），返回歌词原文、绑定歌曲摘要和全部绑定记录 |
| DELETE | `/api/lyrics/{id}` | 删除未绑定歌词记录（v1.2.4），不删除磁盘 `.lrc` 源文件 |
| POST | `/api/lyrics/scan` | 扫描本地 LRC 歌词目录、尝试绑定歌曲，并同步删除已缺失的本地源歌词绑定 |
| GET | `/api/songs/{songId}/lyrics` | 获取音乐列表中某首歌的主歌词 |
| POST | `/api/admin/music/{musicId}/lyric-draft-jobs` | 创建歌词草稿提取任务 |
| POST | `/api/admin/music/{musicId}/lyric-drafts/manual` | 创建手工歌词草稿 |
| GET | `/api/admin/lyric-draft-jobs/{jobId}/draft` | 获取草稿详情 |
| PUT | `/api/admin/lyric-draft-jobs/{jobId}/draft` | 保存人工校对后的草稿文本 |
| POST | `/api/admin/lyric-draft-jobs/{jobId}/confirm` | 确认草稿并生成可信歌词资产 |
| POST | `/api/admin/lyric-draft-jobs/{jobId}/reject` | 驳回草稿 |
| POST | `/api/admin/lyric-draft-jobs/{jobId}/sources` | 关联候选来源元信息到草稿 |
| POST | `/api/lyric-alignment/jobs` | 创建歌词对齐任务 |
| GET | `/api/admin/lyric-alignment/jobs` | 查询歌词对齐任务列表 |
| GET | `/api/admin/lyric-alignment/jobs/{id}` | 查询歌词对齐任务详情 |
| POST | `/api/admin/lyric-alignment/jobs/{id}/approve` | 人工审核通过对齐结果 |
| POST | `/api/admin/lyric-alignment/jobs/{id}/reject` | 人工驳回对齐结果 |
| POST | `/api/admin/lyric-alignment/jobs/{id}/import` | 确认导入已审核通过的 LRC / SWLRC |
| GET | `/api/admin/brave-search/status` | 查询 Brave Search 配置状态 |
| POST | `/api/admin/brave-search/key` | 保存或替换控制台托管 Brave Key |
| PATCH | `/api/admin/brave-search/enabled` | 启用或暂停控制台托管 Brave Search |
| POST | `/api/admin/brave-search/test` | 测试 Brave Search 当前配置 |
| POST | `/api/admin/brave-search/search` | 搜索候选歌词来源，不返回第三方网页全文 |

`GET /api/music` 每行返回 `lyricStatus` 和 `lyricId`，用于音乐列表展示歌词状态。

### 歌词管理页状态字段

`GET /api/lyrics` 和 `GET /api/lyrics/{id}` 面向歌词管理页返回页面级状态：

| 字段 | 值 | 说明 |
|------|----|------|
| `bindStatus` | `BOUND` / `UNBOUND` | 是否已绑定歌曲 |
| `parseStatus` | `SUCCESS` / `FAILED` / `UNKNOWN` | 页面展示用解析状态，内部 `PARSED` 映射为 `SUCCESS`，`PARSE_FAILED` 映射为 `FAILED` |
| `sourceType` | `LOCAL_FILE` / `DRAFT_CONFIRMED` / `ALIGNMENT` / `MANUAL` / `ONLINE` | 当前本地扫描写入 `LOCAL_FILE`，草稿确认写入 `DRAFT_CONFIRMED`，歌词对齐导入写入 `ALIGNMENT`，其余为后续预留 |

列表接口不返回 `content`，避免页面加载时拉取全部歌词全文；查看详情时再调用 `GET /api/lyrics/{id}`。

## 配置

歌词扫描目录在 `application.yml` 中配置：

```yaml
music-vault:
  lyric-dirs:
    - /path/to/lyrics
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
- 在线歌词刮削（规划项，依赖外部 LRCLIB / MusicBrainz 等服务；v0.9.5 OpenAPI 客户端不应请求公网 LRCLIB）
- 多版本审核（同一歌曲多条歌词的优先选择）
- 歌词逐句时间轴编辑
- 播放器滚动歌词（当前仅元数据管理，无播放能力）
- 用户系统与权限控制
- 云端同步

## 后续版本计划

- v0.5.x：继续补齐歌词扫描边界场景、人工复核入口和未匹配结果查看。
- v0.6：封面管理基础能力。
- 后续规划：服务端在线歌词刮削（LRCLIB、MusicBrainz 等外部服务）、歌词编辑器、多版本审核、播放器滚动歌词、用户登录与权限系统；v0.9.5 不包含该能力。

关于歌手、专辑、曲目元数据扩展：

歌词、歌曲、专辑、歌手模型设计已预留 `release_year` / `language` 等字段（v0.5.2 中 `releaseYear` 已出现在 lyrics 表和 API 响应中，但歌词解析暂未填充）。后续歌手、专辑、曲目元数据管理会考虑这些年代字段。当前数据模型不应阻碍此类扩展。
