# 本地开发验证

## 1. 启动后端

```bash
cd backend
mvn quarkus:dev
```

服务监听 `http://localhost:8080`。开发环境默认音乐目录为 `/path/to/music`，默认歌词目录为 `/path/to/lyrics`。

## 2. 触发扫描

```bash
curl -i -X POST http://localhost:8080/api/music/scan \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{}'
```

返回 `202 Accepted`：

```json
{
  "accepted": true,
  "scanJobId": 1,
  "message": "Scan accepted"
}
```

## 3. 查询扫描任务状态

```bash
curl 'http://localhost:8080/api/scan-jobs/1' \
  -H 'Authorization: Bearer change-me'
```

等待 `status` 从 `running` 变为 `completed` 或 `failed`。

## 4. 查询音乐列表

```bash
curl 'http://localhost:8080/api/music?page=0&size=20' \
  -H 'Authorization: Bearer change-me'
```

## 5. 查询音乐详情

```bash
curl 'http://localhost:8080/api/music/1' \
  -H 'Authorization: Bearer change-me'
```

## 6. 重复扫描验证

再次执行 `POST /api/music/scan`，观察：

- `totalFiles`：总数不变
- `newFiles`：0
- `updatedFiles`：0
- `skippedFiles`：等于文件总数

说明未变化文件被正确跳过。

## 7. 歌词扫描验证

首次导入或旧库升级时，先完成音乐扫描，确认 `GET /api/music` 中标题和歌手已经从文件名解析出来，再扫描歌词：

```bash
curl -i -X POST http://localhost:8080/api/lyrics/scan \
  -H 'Authorization: Bearer change-me' \
  -H 'Content-Type: application/json' \
  -d '{"path": "/path/to/lyrics"}'
```

歌词扫描会返回 `matched`、`unmatched`、`duplicateFiles` 等统计。只有生成了 `song_lyrics` 主绑定且歌词文件仍可用的歌曲，`GET /api/music` 才会显示歌曲级歌词状态，例如 `lyricStatus = LRC_READY`、`lyricId`、`hasLrc=true`。当本次扫描完整成功且 `failed=0` 时，当前扫描歌词目录也是可用性同步来源：目录中已删除的本地 LRC 会触发旧绑定解绑，OpenAPI 随后返回无歌词；扫描失败或目录不可访问时不会做删除清理。

v1.2.4 起，歌词扫描按规范化后的 `source_path` 幂等更新。删除同一路径 `.lrc` 后扫描会解绑旧记录；将 `.lrc` 恢复到同一路径后再次扫描，应复用原 `lyrics.id`，更新正文和 hash，并恢复 `song_lyrics` 绑定，不应新增同路径重复记录。

```bash
curl 'http://localhost:8080/api/songs/1/lyrics' \
  -H 'Authorization: Bearer change-me'
```

## 验证检查清单

- [ ] 服务启动正常，`/api/health` 返回 200
- [ ] `POST /api/music/scan` 返回 202，获取 `scanJobId`
- [ ] 扫描完成后 `GET /api/scan-jobs/{id}` status 为 `completed`
- [ ] `GET /api/music` 返回非空列表
- [ ] `GET /api/music/{id}` 返回音乐详情
- [ ] 重复扫描 `skippedFiles` > 0
- [ ] `POST /api/lyrics/scan` 返回 200，且 `matched` 反映自动绑定数量
- [ ] 已绑定且文件可用的 LRC 歌曲在 `GET /api/music` 中返回 `lyricStatus = LRC_READY`

## v0.4 前后端联调验证

### 1. 启动后端

```bash
cd backend
mvn quarkus:dev
```

### 2. 启动前端

```bash
cd frontend
npm run dev
```

前端监听 `http://localhost:5173`，Vite 代理 `/api` 到 `http://localhost:8080`。

### 3. 配置 Token

1. 访问 `http://localhost:5173/settings`
2. 输入 API Token（默认 `change-me`）并保存

### 4. 访问音乐列表页

路径：`/music`

初始状态应为空或显示已有数据。

### 5. 点击扫描按钮

点击「扫描音乐目录」：

- 按钮变为 loading 状态
- 提交成功后弹出成功提示（显示 `scanJobId`）
- 按钮恢复可点击

> 注意：提示"扫描任务已提交"不代表扫描已完成，扫描在后端异步执行。

### 6. 刷新列表

扫描完成后（可等待几秒），点击「刷新」加载最新数据，验证列表是否更新。

### 7. 验证分页

- 切换每页条数（10/20/50/100）
- 翻页，确认列表数据随分页变化

### 8. 验证后端异常时错误提示

停止后端服务后，在前端点击「刷新」或「扫描」，应显示错误提示"加载音乐列表失败，请检查后端服务是否运行"。

## v0.5.2 歌词管理页前后端联调验证

### 1. 启动后端

```bash
cd backend
mvn quarkus:dev
```

### 2. 启动前端

```bash
cd frontend
npm run dev
```

### 3. 配置 Token

1. 访问 `http://localhost:5173/settings`
2. 输入 API Token（默认 `change-me`）并保存

### 4. 访问歌词管理页

路径：`/lyrics`

初始状态应显示歌词列表或空状态提示"暂无歌词数据，可先扫描本地 LRC 文件"。

### 5. 触发歌词扫描

1. 确保本地 LRC 文件存放在配置目录（如 `/path/to/lyrics`）
2. 点击「扫描歌词」按钮
3. 等待扫描完成（按钮恢复可点击）
4. 扫描完成后自动刷新列表，显示扫描结果统计

> 注意：歌词扫描为同步操作，扫描期间按钮保持 loading 状态，耗时取决于歌词文件数量。

### 6. 查看歌词列表

- 验证列表展示：标题、歌手、专辑、来源类型、绑定状态、解析状态、关联歌曲、匹配方式
- 切换分页条数（10/20/50/100），验证分页正常
- 使用筛选条件：绑定状态（已绑定/未绑定）、解析状态（成功/失败/未知）、来源类型

### 7. 查看歌词详情

1. 点击列表右侧「查看歌词」按钮
2. 弹窗展示歌词详情：标题、歌手、专辑、语言、来源类型、格式、解析状态、绑定状态
3. 若已绑定歌曲，显示歌曲标题、歌手、匹配方式、匹配分数
4. 若有多个绑定，显示全部绑定记录列表
5. 底部展示 LRC 原文（带滚动条）

### 8. 验证后端异常时错误提示

停止后端服务后，在前端点击「刷新」或「扫描歌词」，应显示错误提示"加载歌词列表失败，请检查后端服务是否运行"。

### 9. 验证 source_path 恢复幂等性（v1.2.4）

1. 准备一首已入库歌曲和同名 `.lrc`，点击「扫描歌词」，确认歌曲显示已绑定歌词。
2. 记录该歌词详情中的 `id` 和 `sourcePath`。
3. 删除磁盘上的该 `.lrc`，再次点击「扫描歌词」。
4. 确认歌曲变为无歌词，原歌词记录在歌词管理页显示为未绑定。
5. 将 `.lrc` 恢复到相同路径，可修改歌词正文。
6. 再次点击「扫描歌词」。
7. 确认歌词记录 `id` 未变化，正文 / hash 已更新，歌曲重新绑定，列表中不存在同一 `sourcePath` 的重复歌词记录。

### 10. 验证未绑定歌词记录删除（v1.2.4）

1. 在歌词管理页筛选「未绑定」。
2. 对未绑定记录点击「删除记录」。
3. 二次确认弹窗应明确提示只删除数据库记录，不删除磁盘 `.lrc` 源文件。
4. 确认删除后，列表中不再显示该记录。
5. 检查原 `.lrc` 文件仍保留在磁盘上。
6. 已绑定歌词行不应显示「删除记录」入口；直接调用 `DELETE /api/lyrics/{id}` 应返回 `409 conflict`。

## 验证检查清单

歌词管理页补充：

- [ ] `GET /api/lyrics` 返回歌词列表（含 bindStatus / parseStatus / sourceType）
- [ ] `GET /api/lyrics/{id}` 返回歌词详情（含 content / boundSongs）
- [ ] `DELETE /api/lyrics/{id}` 可删除未绑定歌词记录，且不删除磁盘 `.lrc` 源文件
- [ ] 已绑定歌词调用 `DELETE /api/lyrics/{id}` 返回冲突
- [ ] 列表分页正常（page 从 0 开始）
- [ ] keyword 搜索生效
- [ ] bindStatus / parseStatus / sourceType 筛选生效
- [ ] 点击「查看歌词」弹窗展示 LRC 原文
- [ ] 点击「扫描歌词」触发扫描并刷新列表
- [ ] 删除 `.lrc` 后扫描会解绑，恢复同一路径 `.lrc` 后扫描复用原歌词记录并重新绑定
- [ ] 未配置 Token 时 API 请求携带 Authorization 头

## v0.7.1 音乐元数据编辑前后端联调验证

### 1. 启动后端

```bash
cd backend
mvn quarkus:dev
```

### 2. 启动前端

```bash
cd frontend
npm run dev
```

### 3. 配置 Token

1. 访问 `http://localhost:5173/settings`
2. 输入 API Token（默认 `change-me`）并保存

### 4. 访问音乐列表页

路径：`/music`

验证列表中新增「年份」「流派」两列，且已有元数据的歌曲正确显示数值。

### 5. 点击「编辑」按钮

点击任意行的「编辑」按钮，验证弹窗正确回显当前行的元数据字段（标题、歌手、专辑、年份、曲目号、流派）。

### 6. 保存元数据

修改任意字段（如年份），点击「保存」：

- 按钮进入 loading 状态
- 保存成功后弹窗关闭
- 列表中该行对应字段更新为新值
- `metadataUpdatedAt` 列显示时间戳

### 7. 验证 title 兜底

找到一条 `title` 为空的歌曲（从未手动编辑过的旧扫描数据）：

- 列表「歌曲名」列应显示文件名（不含扩展名）
- 打开编辑弹窗，`title` 输入框为空
- 保持 title 为空并保存，文件名兜底逻辑应使 `title` 字段写入文件名

### 8. 验证校验

在编辑弹窗中：

- 年份输入 1899 → 应阻止保存，提示"年份需在 1900 ~ N 之间"
- 曲目号输入 0 → 应阻止保存，提示"曲目号必须大于 0"

### 9. 验证非元数据字段不被修改

在编辑弹窗中修改元数据后，验证该歌曲的：

- 文件路径不变
- 歌词绑定状态不变
- 封面绑定状态不变

### 10. 验证后端异常时错误提示

停止后端服务后，在前端点击「保存」，应显示错误提示"保存元数据失败"，弹窗保持打开。
