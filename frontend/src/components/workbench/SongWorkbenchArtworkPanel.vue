<script setup lang="ts">
import { ref } from 'vue'
import type { WorkbenchArtwork } from '../../api/music'
import { currentThemeAssets } from '../../theme/currentTheme'

defineProps<{ artwork: WorkbenchArtwork }>()
const failed = ref(false)
</script>

<template>
  <div v-if="artwork.available && artwork.previewUrl && !failed" class="artwork-panel">
    <img :src="artwork.previewUrl" alt="歌曲封面" @error="failed = true" />
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="文件名">{{ artwork.fileName || '--' }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ artwork.mimeType || '--' }}</el-descriptions-item>
      <el-descriptions-item label="尺寸">
        {{ artwork.width && artwork.height ? `${artwork.width} x ${artwork.height}` : '--' }}
      </el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ artwork.updatedAt || '--' }}</el-descriptions-item>
    </el-descriptions>
  </div>
  <el-alert v-else-if="failed" type="error" show-icon title="封面加载失败" />
  <el-empty
    v-else
    description="暂无封面"
    :image="currentThemeAssets.emptyStates.cover"
    :image-size="160"
  />
</template>

<style scoped>
.artwork-panel {
  display: grid;
  grid-template-columns: minmax(180px, 280px) minmax(260px, 1fr);
  gap: 20px;
  align-items: start;
}
.artwork-panel img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}
@media (max-width: 760px) {
  .artwork-panel {
    grid-template-columns: 1fr;
  }
}
</style>
