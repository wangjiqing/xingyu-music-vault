# 本地开发验证

## 1. 启动后端

```bash
cd backend
mvn quarkus:dev
```

服务监听 `http://localhost:8080`。开发环境默认音乐目录为 `/Users/wangjiqing/Project/Musics/Music`，默认歌词目录为 `/Users/wangjiqing/Project/Musics/Lyrics`。

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
  -d '{"path": "/Users/wangjiqing/Project/Musics/Lyrics"}'
```

歌词扫描会返回 `matched`、`unmatched`、`duplicateFiles` 等统计。只有生成了 `song_lyrics` 主绑定的歌曲，`GET /api/music` 才会显示 `lyricStatus = BOUND` 和 `lyricId`。

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
- [ ] 已绑定歌曲在 `GET /api/music` 中返回 `lyricStatus = BOUND`

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
