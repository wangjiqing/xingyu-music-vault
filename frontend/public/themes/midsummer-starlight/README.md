# 仲夏星河 / Midsummer Starlight

工程化主题资产目录，themeId: `midsummer-starlight`。

## 目录

- `banner/`: README 与网页横幅。
- `logo/`: 从 Logo 展示板裁切的横向、方形与 mark 版本。
- `favicon/`: favicon、Apple Touch Icon 与 PWA 图标。
- `background/`: 桌面与移动端背景。
- `empty-states/`: 页面空状态与流程插画。
- `palette/`: 色板图、CSS 变量与 JSON。
- `archive/original-previews/`: 原始预览图与源素材包附带配置的备份。

## 分辨率说明

- README banner 源裁切尺寸为 `1586x496`。`readme-banner.png` 输出为 1600x500；`readme-banner@2x.png` 保留最佳可用尺寸，并非真正 3200x1000 原生 2x。
- 背景源图为 `1672x941`，低于 4K/2K/1080p 建议尺寸，当前未做超分。
- 推荐接入优先使用语义化路径：`background/background-desktop.png`、`background/background-desktop.webp`、`background/background-mobile.webp`。`manifest.json` 中的旧 4K/2K/1080p 名称仅作为 alias 映射记录，不再保留重复二进制文件。
- 移动端背景已生成，尺寸为 `529x941`，为竖向居中偏右裁切，优先保留唱机主体。

## 质量标记

- 当前 Logo 来源为展示板裁切，建议后续使用原始 SVG / 分层文件重做正式 Logo。
- 空状态插画来源为展示板卡片裁切，`manifest.json` 中已标记为 `needs-redraw`，建议后续补充原始独立单图。
- favicon 使用展示板中 512x512 图标区域作为母版派生，已生成 `.ico`、16/32/48/64、Apple Touch Icon、192 与 512 图标。

## 使用

```css
@import "./theme.css";
```

```json
{
  "theme": "./manifest.json"
}
```
