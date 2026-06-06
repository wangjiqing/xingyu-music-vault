# 仲夏星河主题资源试接

本次接入是 Xingyu Music Vault / 星语音库的「仲夏星河 / Midsummer Starlight」主题资源试点。资源来自已经工程化处理的 `themes/midsummer-starlight/` 包，目前放置在前端静态资源目录，供 README、浏览器图标、管理后台皮肤、首页和少量低风险页面引用。

本次试接以 v1.0.1 为边界：不重构主题系统，不引入复杂主题切换，不修改后端业务逻辑，不接入星语音乐盒项目。

## 资源路径

- 前端静态资源：`frontend/public/themes/midsummer-starlight/`
- README 预览图：`frontend/public/themes/midsummer-starlight/banner/readme-banner.png`
- Favicon：`frontend/public/themes/midsummer-starlight/favicon/`
- 主题变量 CSS：`frontend/public/themes/midsummer-starlight/theme.css`
- 首页与管理后台背景试接：`frontend/public/themes/midsummer-starlight/background/background-desktop.webp`
- Header / footer / README banner：`frontend/public/themes/midsummer-starlight/banner/`
- Logo：`frontend/public/themes/midsummer-starlight/logo/`
- 空状态图：`frontend/public/themes/midsummer-starlight/empty-states/`
- 旧背景命名 alias：在 `manifest.json` 的 `assets.background.aliases` 中记录，指向 `background-desktop` 语义化资源。
- 源素材备份：`frontend/public/themes/midsummer-starlight/archive/` 仅用于源码追溯，生产构建会排除该目录。

## 当前引用范围

- `frontend/index.html` 引用主题 favicon 与 `theme.css`。
- 管理后台顶部 header 使用 `banner/readme-banner.webp` 作为轻量皮肤。
- 管理后台底部 footer 使用 `banner/readme-banner.webp` 作为轻量皮肤。
- 管理后台主内容底板使用低对比度虚化的 `background/background-desktop.webp`。
- 管理后台首页使用 `banner/readme-banner.webp`、`logo/logo-horizontal.png` 与 `empty-states/empty-home.png` 作为欢迎区和入口卡片素材。
- 管理后台 footer 结构化展示项目名、后端 `serviceVersion`、当前主题名与 Apache License 2.0；版本来自 `/api/open/v1/server/info`，主题名来自当前主题配置。
- 全部歌曲页空状态使用 `empty-songs.png`。
- 歌手页空状态使用 `empty-artists.png`。
- 专辑页空状态使用 `empty-albums.png`。
- 封面管理页空状态使用 `empty-cover.png`。
- 歌词管理页空状态使用 `empty-lyrics.png`。
- 项目 README 新增主题预览，不覆盖现有品牌标题。

## 页面体验调整

- 首页迁入原歌曲列表统计卡片，展示音乐总数、待整理、有歌词、有封面、回收站。
- 首页入口卡片改为纵向排列，并引用空状态素材作为入口图标。
- 全部歌曲、歌手、专辑、歌词、封面列表采用内部滚动区域，避免浏览器纵向滚动条破坏背景覆盖。
- 列表页默认预取两页数据；滚动接近底部时继续请求下一页并追加展示。
- 全部歌曲列表收起封面缩略图，改为有封面 / 无封面状态和统一预览入口。
- 封面管理列表收起缩略图，改为可预览 / 缺失 / 无预览状态和统一预览入口。
- 歌手详情与专辑详情中的歌曲列表同步采用内部滚动和追加加载，并加宽操作列。
- OpenAPI 页面改为左右两栏，安全与访问控制、客户端接入建议放到右侧，请求示例暂时隐藏。

## 冗余资源处理

- 已移除与 `background/background-desktop.webp` 完全一致的 `background-4k.webp`、`background-2k.webp`、`background-1080p.webp`。
- 已移除与 `background/background-desktop.png` 完全一致的 `background-4k.png`。
- `manifest.json` 保留旧 4K/2K/1080p 名称的 alias 映射，避免后续维护者误以为存在原生 4K/2K/1080p 终稿。
- `palette/theme-palette.css` 与 `palette/theme-palette.json` 暂作为色板快照保留；运行功能以根目录 `theme.css` 与 `theme.json` 为准。
- `archive/original-previews/` 保留在源码资源包中，但通过 Vite 构建插件从 `dist/` 中排除。

## 限制与后续

- 当前资源为生成素材工程化试点版，还不是最终品牌资产。
- Logo 来源于展示板裁切，后续如进入正式主题系统，应重新制作或规范化输出。
- 空状态图已经入库并用于多个低风险页面，后续可按页面语义重绘或替换。
- 背景图不是原生 4K 终稿，请不要按 4K 品牌背景交付物标注。
- 当前主题名读取保留轻量配置结构，后续可以扩展为主题切换；本次不实现主题切换系统。
- 本次不接入星语音乐盒项目，不修改后端业务逻辑。

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
