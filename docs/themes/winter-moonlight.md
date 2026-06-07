# 冬夜雪境主题资源接入

本次接入是 Xingyu Music Vault / 星语音库的第三套四季主题资源：「冬夜雪境 / Winter Moonlight」。资源来自已经工程化处理的 `themes/winter-moonlight/` 包，目前放置在前端静态资源目录，作为 v1.0.3 的冬季主题候选素材。

本次接入以 v1.0.3 为边界：复用既有主题目录、主题元数据、静态资源 helper 和管理后台轻量主题切换入口，不修改后端业务逻辑、OpenAPI 契约、数据库结构或音乐扫描、元数据、歌词、封面等业务能力。

## 主题信息

- Theme ID：`winter-moonlight`
- 中文名：冬夜雪境
- English Name：Winter Moonlight
- 季节定位：四季主题中的冬季主题
- 视觉关键词：冬夜、雪境、月光、静谧蓝白、雪花、唱片、音乐盒、温暖灯光点缀

## 资源路径

- 前端静态资源：`frontend/public/themes/winter-moonlight/`
- README 预览图：`frontend/public/themes/winter-moonlight/banner/readme-banner.png`
- Favicon：`frontend/public/themes/winter-moonlight/favicon/`
- 主题变量 CSS：`frontend/public/themes/winter-moonlight/theme.css`
- 背景图：`frontend/public/themes/winter-moonlight/background/`
- Header / footer / README banner 候选：`frontend/public/themes/winter-moonlight/banner/`
- Logo：`frontend/public/themes/winter-moonlight/logo/`
- 空状态图：`frontend/public/themes/winter-moonlight/empty-states/`
- 色板：`frontend/public/themes/winter-moonlight/palette/`

## 当前接入范围

- Winter Moonlight 已作为冬季主题候选加入前端主题配置列表。
- 管理后台 header 右侧主题切换入口可选择 `冬夜雪境 / Winter Moonlight`。
- 切换主题时，页面皮肤、空状态图、favicon 和 `theme.css` 会跟随当前主题更新。
- 主题选择继续写入浏览器 `localStorage`，刷新页面后恢复上次选择。
- 当前默认主题仍为 `midsummer-starlight`，新增冬季主题不改变默认主题逻辑。

## 冗余资源处理

- 素材包中的 `background-4k.png`、`background-4k.webp`、`background-2k.webp`、`background-1080p.webp` 与语义化桌面背景为同源别名，本次未重复拷贝。
- `manifest.json` 使用 `assets.background.aliases` 记录旧命名到 `background-desktop` 的映射，保持四季主题结构一致。
- 源素材包内的 `archive/` 仅用于追溯，未放入公开静态目录。
- 背景图不是原生 4K 终稿，`isNative4K` 保持 `false`。

## 限制与后续

- 当前资源为生成素材工程化版本，还不是最终品牌资产。
- Logo 来源于展示板裁切，后续如进入正式主题系统，应重新制作或规范化输出。
- 空状态图已按统一目录入库，后续可按页面语义重绘或替换。
- `background/background-desktop.png` 体积约 3.1MB；当前运行时使用 `background/background-desktop.webp`，PNG 可在后续版本压缩或评估是否继续保留。
- 冬季深色主题与当前部分浅色硬编码组件仍需后续做深度视觉适配。
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
