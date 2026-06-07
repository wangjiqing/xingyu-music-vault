# 秋日唱片 / Autumn Vinyl

工程化主题资产目录，themeId: `autumn-vinyl`。

## 目录

- `banner/`: README 与网页横幅。
- `logo/`: 从 Logo 展示图裁切的横向、方形与 mark 版本。
- `favicon/`: favicon、Apple Touch Icon 与 PWA 图标。
- `background/`: 桌面与移动端背景。
- `empty-states/`: 页面空状态与流程插画。
- `palette/`: 色板图、CSS 变量与 JSON。
- `archive/original-previews/`: 原始预览图与源素材包附带配置的备份。

## 分辨率说明

- README banner 源裁切尺寸为 `1983x620`。`readme-banner@2x.png` 保留最佳可用尺寸，并非原生 3200x1000 2x。
- 背景源图为 `1672x941`，`isNative4K` 为 `false`。当前未做超分，`background-4k.*` 是兼容旧路径的文件名，不是原生 4K。
- 推荐接入优先使用语义化别名：`background/background-desktop.png`、`background/background-desktop.webp`、`background/background-mobile.webp`。
- 移动端背景已生成，尺寸为 `529x941`，为竖向裁切。

## 质量标记

- 当前 Logo 来源为展示图裁切，建议后续使用原始 SVG / 分层文件重做正式 Logo，已标记 `needs-original-vector`。
- 空状态插画来源为展示板卡片裁切，已标记 `needs-redraw`，建议后续补充原始独立单图。
- 背景资源低于原生 4K，已标记 `source-resolution-limited`。
- favicon 使用展示板中最大图标区域作为母版派生，已生成 `.ico`、16/32/48/64、Apple Touch Icon、192 与 512 图标。

## 使用

```css
@import "./theme.css";
```

```json
{
  "theme": "./manifest.json"
}
```
