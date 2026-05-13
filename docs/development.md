# 本地开发验证

## 1. 启动后端

```bash
cd backend
mvn quarkus:dev
```

服务监听 `http://localhost:8080`，默认扫描目录 `/Users/wangjiqing/Project/Musics`。

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

## 验证检查清单

- [ ] 服务启动正常，`/api/health` 返回 200
- [ ] `POST /api/music/scan` 返回 202，获取 `scanJobId`
- [ ] 扫描完成后 `GET /api/scan-jobs/{id}` status 为 `completed`
- [ ] `GET /api/music` 返回非空列表
- [ ] `GET /api/music/{id}` 返回音乐详情
- [ ] 重复扫描 `skippedFiles` > 0
