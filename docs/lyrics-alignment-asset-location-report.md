# 歌词对齐导入资产落点记录

## 背景

v1.3.0 歌词对齐链路已经可以完成：

1. 候选歌词草稿确认生成可信歌词资产；
2. 使用可信歌词资产创建逐字对齐任务；
3. Worker 输出 `lyrics.lrc` 与 `lyrics.swlrc`；
4. 管理员审核通过并确认导入；
5. 音库创建 `source_type = ALIGNMENT` 的歌词记录，并绑定为歌曲当前歌词。

当前实现中，对齐结果导入后会从 Worker 中间目录复制到：

```text
{alignment-assets-dir}/{musicId}/{jobId}/lyrics.lrc
{alignment-assets-dir}/{musicId}/{jobId}/lyrics.swlrc
```

示例：

```text
/Users/wangjiqing/Project/Musics/alignment-assets/529/6acaff7a-c505-4b14-b311-e3f2f48fc966/lyrics.lrc
/Users/wangjiqing/Project/Musics/alignment-assets/529/6acaff7a-c505-4b14-b311-e3f2f48fc966/lyrics.swlrc
```

这已经满足“不长期引用 alignment-jobs 中间目录”的安全要求，但还没有进入现有歌词挂载扫描目录。

## 当前状态

当前导入结果不是写入 `LYRICS_DIR` / `MUSIC_VAULT_LYRIC_DIRS` 对应的歌词扫描目录，而是写入独立的 `alignment-assets-dir`。

数据库中会保存：

- `lyrics.source_type = ALIGNMENT`
- `lyrics.source_task_id = {jobId}`
- `lyrics.parent_lyrics_id = {sourceTrustedLyricsId}`
- `lyrics.source_path = {alignment-assets-dir}/.../lyrics.lrc`
- `lyrics.swlrc_path = {alignment-assets-dir}/.../lyrics.swlrc`
- `lyrics.content_hash`
- `lyrics.swlrc_hash`
- `lyrics.confirmed_at`
- `lyrics.confirmed_by`

因此 OpenAPI 与主歌词绑定可以读取新导入歌词，但文件落点与传统歌词扫描目录不是同一个目录体系。

## 期望收口方向

最终更合理的落点应是歌词挂载的扫描目录，也就是部署中配置的歌词目录：

```text
LYRICS_DIR
MUSIC_VAULT_LYRIC_DIRS
```

建议后续将“审核导入后的正式 LRC/SWLRC 资产”写入该歌词目录下的受控子目录，例如：

```text
{lyrics-dir}/alignment/{musicId}/{jobId}/lyrics.lrc
{lyrics-dir}/alignment/{musicId}/{jobId}/lyrics.swlrc
```

或使用更贴近现有命名策略的文件名：

```text
{lyrics-dir}/alignment/{artist} - {title}/{jobId}.lrc
{lyrics-dir}/alignment/{artist} - {title}/{jobId}.swlrc
```

具体命名需要后续结合当前歌词扫描、重复文件处理、删除同步和跨平台路径兼容策略再定。

## 为什么建议进入歌词扫描目录

进入歌词扫描目录后，资产语义更统一：

- 手工维护的 LRC、扫描导入的 LRC、对齐导入的 LRC 都处在同一个歌词资产根目录下；
- 备份与迁移时只需要关注音乐目录、歌词目录、封面目录和数据库；
- 管理员从宿主机文件系统查看时更容易理解“这些是正式歌词资产”；
- 后续歌词扫描、缺失文件同步、重新绑定等能力可以逐步覆盖对齐导入资产；
- 避免 `alignment-assets-dir` 同时承担“草稿可信歌词资产”和“正式 LRC/SWLRC 资产”两种含义。

## 需要注意的兼容风险

不能简单把文件写入歌词目录后立即交给现有扫描逻辑处理，否则可能出现：

- 扫描器把已由导入流程创建的 `ALIGNMENT` 记录再次识别为普通 `LOCAL_FILE`，产生重复歌词记录；
- 删除同步把仍在数据库中绑定的导入资产误判为外部本地文件；
- SWLRC 作为 LRC 附加资产的关系在传统 LRC 扫描流程中丢失；
- 文件命名若与已有歌词文件冲突，可能误覆盖用户手工维护资产；
- 导入失败时需要保证临时文件不会被扫描器提前发现。

## 后续改造建议

建议作为 v1.3.x 或 v1.4 的收口任务处理：

1. 新增配置，明确正式对齐歌词资产目录，默认落在歌词扫描目录下的受控子目录。
2. 导入时继续使用临时文件 + 原子移动，避免扫描器看到半成品。
3. 文件名使用 jobId 或 lyricsId 保证唯一，不使用用户输入直接组成路径。
4. 扫描逻辑识别受控对齐子目录，避免把 `ALIGNMENT` 资产重复创建为 `LOCAL_FILE`。
5. 删除同步逻辑区分外部扫描歌词与音库生成歌词，避免误解绑或误删。
6. 保留数据库中的 `source_task_id`、`parent_lyrics_id`、`swlrc_path`、hash 和确认人信息。
7. 迁移已有 `alignment-assets-dir` 中的正式导入 LRC/SWLRC 时，先复制到新目录并校验 hash，再更新数据库路径。

## 当前结论

当前 v1.3.0 的实现已经完成了“从 Worker 中间目录导入到音库受控资产目录”的目标，但从产品语义看，正式导入后的 LRC/SWLRC 最终应该放到歌词挂载扫描目录下的受控子目录中。

这不是当前链路阻塞问题，但应记录为后续收口项。
