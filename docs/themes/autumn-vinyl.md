# 秋日唱片主题资源接入

本次接入是 Xingyu Music Vault / 星语音库的第二套四季主题资源：「秋日唱片 / Autumn Vinyl」。资源来自已经工程化处理的 `themes/autumn-vinyl/` 包，目前放置在前端静态资源目录，作为 v1.0.2 的主题候选素材。

本次接入以 v1.0.2 为边界：复用 v1.0.1 建立的主题目录、主题元数据和静态资源引用方式，补充管理后台 header 轻量主题切换入口，但不重构主题系统，不引入服务端主题管理，不修改后端业务逻辑、OpenAPI 契约或数据库结构。

## 主题信息

- Theme ID：`autumn-vinyl`
- 中文名：秋日唱片
- English Name：Autumn Vinyl
- 季节定位：四季主题中的秋季主题
- 视觉关键词：秋季、黑胶唱片、复古留声机、枫叶、暖色落叶、温暖怀旧、复古暖色、音乐氛围

## 资源路径

- 前端静态资源：`frontend/public/themes/autumn-vinyl/`
- README 预览图：`frontend/public/themes/autumn-vinyl/banner/readme-banner.png`
- Favicon：`frontend/public/themes/autumn-vinyl/favicon/`
- 主题变量 CSS：`frontend/public/themes/autumn-vinyl/theme.css`
- 背景图：`frontend/public/themes/autumn-vinyl/background/`
- Header / footer / README banner 候选：`frontend/public/themes/autumn-vinyl/banner/`
- Logo：`frontend/public/themes/autumn-vinyl/logo/`
- 空状态图：`frontend/public/themes/autumn-vinyl/empty-states/`
- 色板：`frontend/public/themes/autumn-vinyl/palette/`
- 源素材备份：`frontend/public/themes/autumn-vinyl/archive/` 仅用于源码追溯，生产构建会排除该目录。

## 当前接入范围

- Autumn Vinyl 已作为第二套主题候选加入前端主题配置列表。
- 管理后台 header 右侧提供轻量主题切换入口，选择结果写入浏览器 `localStorage`。
- 切换主题时，页面皮肤、空状态图、favicon 和 `theme.css` 会跟随当前主题更新。
- 当前默认展示主题仍为 `midsummer-starlight`，避免在 v1.0.2 扩大为服务端主题管理系统。
- 秋季主题保留 `theme.json`、`theme.css`、`manifest.json` 与主题 README，后续 Winter Moonlight / Spring Dawn 可沿用同样结构接入。
- 前端主题资源路径集中到当前主题配置 helper，便于后续接入更多候选主题。
- Vite 构建会排除所有主题目录下的 `archive/` 备份目录，避免预览源图进入生产产物。

## 冗余资源处理

- 素材包中的 `background-4k.png`、`background-4k.webp`、`background-2k.webp`、`background-1080p.webp` 与语义化桌面背景为同源别名，本次未重复拷贝。
- `manifest.json` 使用 `assets.background.aliases` 记录旧命名到 `background-desktop` 的映射，保持与仲夏主题一致。
- 背景图不是原生 4K 终稿，`isNative4K` 保持 `false`。

## 限制与后续

- 当前资源为生成素材工程化版本，还不是最终品牌资产。
- Logo 来源于展示板裁切，后续如进入正式主题系统，应重新制作或规范化输出。
- 空状态图已按统一目录入库，后续可按页面语义重绘或替换。
- 本版本仅实现本地轻量切换入口；后续如增加完整主题管理，应优先复用当前主题候选配置。
- Winter Moonlight / Spring Dawn 后续应继续沿用 `frontend/public/themes/<theme-id>/`、`theme.json`、`manifest.json`、`theme.css` 和文档说明结构。

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
