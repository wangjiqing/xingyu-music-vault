# 春日晨光主题资源接入

本次接入是 Xingyu Music Vault / 星语音库的第四套四季主题资源：「春日晨光 / Spring Dawn」。资源来自已经工程化处理的 `themes/spring-dawn/` 包，目前放置在前端静态资源目录，作为 v1.0.3 的春季主题候选素材。

本次接入以 v1.0.3 为边界：复用既有主题目录、主题元数据、静态资源 helper 和管理后台轻量主题切换入口，不修改后端业务逻辑、OpenAPI 契约、数据库结构或音乐扫描、元数据、歌词、封面等业务能力。春季主题以春日晨光、浅色、花草、清新柔和、温暖明亮为主，不刻意保留星空元素。

## 主题信息

- Theme ID：`spring-dawn`
- 中文名：春日晨光
- English Name：Spring Dawn
- 季节定位：四季主题中的春季主题
- 视觉关键词：春日晨光、浅色、花草、清新、柔和、温暖明亮、音乐盒、唱片、音符

## 资源路径

- 前端静态资源：`frontend/public/themes/spring-dawn/`
- README 预览图：`frontend/public/themes/spring-dawn/banner/readme-banner.png`
- Favicon：`frontend/public/themes/spring-dawn/favicon/`
- 主题变量 CSS：`frontend/public/themes/spring-dawn/theme.css`
- 背景图：`frontend/public/themes/spring-dawn/background/`
- Header / footer / README banner 候选：`frontend/public/themes/spring-dawn/banner/`
- Logo：`frontend/public/themes/spring-dawn/logo/`
- 空状态图：`frontend/public/themes/spring-dawn/empty-states/`
- 色板：`frontend/public/themes/spring-dawn/palette/`

## 当前接入范围

- Spring Dawn 已作为春季主题候选加入前端主题配置列表。
- 管理后台 header 右侧主题切换入口可选择 `春日晨光 / Spring Dawn`。
- 切换主题时，页面皮肤、空状态图、favicon 和 `theme.css` 会跟随当前主题更新。
- 主题选择继续写入浏览器 `localStorage`，刷新页面后恢复上次选择。
- 当前默认主题仍为 `midsummer-starlight`，新增春季主题不改变默认主题逻辑。
- 由于源素材包未提供 `metadata-pending.png`，当前前端对该运行时引用使用同主题 `empty-home.png` 兜底，避免春季主题出现资源 404。

## 素材缺失或待确认项

- `empty-states/metadata-pending.png`：源展示板缺失，未伪造文件。
- `empty-states/syncing.png`：源展示板缺失，当前前端未直接引用，影响面为零；已在 `manifest.json` 的 `emptyStatesMissing` 中标记，后续实际使用该资源时再补充即可。

## 冗余资源处理

- 素材包中的 `background-4k.png`、`background-4k.webp`、`background-2k.webp`、`background-1080p.webp` 与语义化桌面背景为同源别名，本次未重复拷贝。
- `manifest.json` 使用 `assets.background.aliases` 记录旧命名到 `background-desktop` 的映射，保持四季主题结构一致。
- 源素材包内的 `archive/` 仅用于追溯，未放入公开静态目录。
- 背景图不是原生 4K 终稿，`isNative4K` 保持 `false`。

## 限制与后续

- 当前资源为生成素材工程化版本，还不是最终品牌资产。
- Logo 来源于展示板裁切，后续如进入正式主题系统，应重新制作或规范化输出。
- 空状态图已按统一目录入库，后续可按页面语义重绘或替换。
- 本版本仅接入基础主题切换闭环，不引入服务端主题管理系统。

## 验证

前端构建：

```bash
cd frontend
npm run build
```

本地预览：

```bash
cd frontend
npm run dev
```
