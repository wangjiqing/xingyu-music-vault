# 封面能力测试清单（v0.6 / v0.6.1 / v0.6.2）

## 扫描封面目录

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | 正常扫描 Artworks 目录 | 返回 `imported`、`duplicateFiles`、`autoBound`、`unmatched` |
| 2 | 扫描时 Artworks 目录不存在 | 目录自动创建，扫描正常完成 |
| 3 | 扫描时目录无权限写入 | 返回 `400`，错误信息提示目录不可写 |
| 4 | 扫描目录配置为文件而非目录 | 返回 `400` |
| 5 | 扫描空目录 | `totalFiles: 0`，所有计数为 0 |
| 6 | 扫描包含重复 hash 图片 | 相同 hash 只入库一次，`duplicateFiles` 累加 |
| 7 | 扫描文件名匹配音乐的封面 | 自动写入 `music_artwork_bindings` |
| 8 | 扫描已有主封面的歌曲 | 不覆盖已有主绑定 |

## 导入本地封面

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | 正常导入 jpg 图片 | 保存到 Artworks 目录，`artworkStatus: BOUND` |
| 2 | 正常导入 png/webp 图片 | 同上 |
| 3 | 导入 10MB 以内图片 | 成功 |
| 4 | 导入超过 10MB 图片 | 返回 `400`，提示文件过大 |
| 5 | 导入非图片文件（如 txt） | 返回 `400`，提示图片类型不支持 |
| 6 | 导入内容损坏的图片 | 返回 `400`，提示图片损坏或无法解析 |
| 7 | 导入时 Artworks 目录不存在 | 目录自动创建 |
| 8 | 导入时 Artworks 目录不可写 | 返回 `400`，提示目录不可写 |
| 9 | 重复导入同一张图（相同 hash） | 复用已有 artwork 记录，`artworkId` 复用，`artworkStatus` 切换到该图 |
| 10 | 导入时文件名冲突 | 追加序号（`-1`、`-2`）保存 |

## 封面绑定与解绑

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | 选择已有封面绑定到歌曲 | `artworkStatus: BOUND`，`artworkFileExists: true` |
| 2 | 更换封面（再次绑定） | 旧绑定降级，新绑定设为主封面 |
| 3 | 绑定文件已缺失的封面 | 返回 `400`，提示文件缺失 |
| 4 | 绑定不存在的 artwork | 返回 `404` |
| 5 | 取消绑定 | `artworkStatus: MISSING`，`artworkId: null`，`artworkFileExists: null` |
| 6 | 取消绑定后再次绑定 | 正常绑定 |
| 7 | `/music` 列表返回正确 `artworkStatus` | 有绑定且文件存在 → `BOUND`；无绑定 → `MISSING` |

## 文件缺失处理

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | 封面文件被手动删除后查 `/music` | `artworkStatus: BOUND`，`artworkFileExists: false` |
| 2 | 封面文件被删除后访问 `/api/artworks/{id}/file` | 返回 `404` |
| 3 | 封面文件被删除后查 `/api/artworks/{id}` | `fileExists: false` |
| 4 | 封面文件被删除后查 `/api/artworks` 列表 | 列表项 `fileExists: false` |

## /artworks 列表筛选

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | `boundStatus=all` | 返回全部封面 |
| 2 | `boundStatus=bound` | 只返回 `boundCount > 0` 的封面 |
| 3 | `boundStatus=unbound` | 只返回 `boundCount = 0` 的封面 |
| 4 | `keyword` 搜索 | 按文件名/标题模糊匹配 |
| 5 | `keyword` + `boundStatus` 组合筛选 | 同时生效 |

## 前端展示

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | `/music` 封面图片加载成功 | 正常显示 |
| 2 | `/music` 封面文件缺失 | 显示兜底占位图 |
| 3 | `/artworks` 列表展示绑定数量 | 显示 `boundCount` |
| 4 | `/artworks` 筛选切换（全部/已绑定/未绑定） | 列表正确过滤 |
| 5 | 导入失败 | 显示具体错误提示（文件过大/类型不支持/损坏） |

## /music 列表稳定性

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | 分页翻页正常 | 分页数据正确 |
| 2 | `/music` 歌词状态字段不受封面操作影响 | `lyricStatus` 不变 |
| 3 | `/music` 封面状态不受歌词操作影响 | `artworkStatus` 不变 |
| 4 | 大量数据翻页性能 | 无明显卡顿 |
