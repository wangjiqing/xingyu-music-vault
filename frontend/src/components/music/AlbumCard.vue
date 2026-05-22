<script setup lang="ts">
import { computed } from 'vue'
import { Collection } from '@element-plus/icons-vue'
import type { AlbumItem } from '../../api/music'
import ArtworkImage from './ArtworkImage.vue'

const props = withDefaults(defineProps<{
  item: AlbumItem
  coverUrl?: string | null
}>(), {
  coverUrl: null,
})

const emit = defineEmits<{
  click: [item: AlbumItem]
}>()

const initial = computed(() => {
  const name = props.item.album || ''
  if (!name || name === 'Unknown') return ''
  return name.charAt(0).toUpperCase()
})

const albumColor = computed(() => {
  const colors = [
    '#409eff', '#67c23a', '#e6a23c', '#f56c6c',
    '#909399', '#337ecc', '#529b2e', '#b88230',
    '#c45656', '#73767a',
  ]
  if (!props.item.albumKey) return colors[0]
  let hash = 0
  for (let i = 0; i < props.item.albumKey.length; i++) {
    hash = props.item.albumKey.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
})

function handleClick() {
  emit('click', props.item)
}
</script>

<template>
  <div class="album-card" @click="handleClick">
    <ArtworkImage
      v-if="coverUrl"
      :src="coverUrl"
      :alt="item.album"
      :size="1000"
      :radius="0"
      class="album-card-cover"
    />
    <div v-else class="album-card-avatar" :style="{ background: albumColor }">
      <span v-if="initial" class="album-initial">{{ initial }}</span>
      <el-icon v-else :size="28"><Collection /></el-icon>
    </div>
    <div class="album-card-body">
      <div class="album-name" :title="item.album">{{ item.album || 'Unknown' }}</div>
      <div class="album-artist" :title="item.albumArtist">
        {{ item.albumArtist || 'Unknown' }}
      </div>
      <div class="album-year" v-if="item.year">{{ item.year }}</div>
      <div class="album-stats">
        <div class="album-stat-item">
          <span class="album-stat-value">{{ item.trackCount }}</span>
          <span class="album-stat-label">曲目</span>
        </div>
        <div class="album-stat-item">
          <span class="album-stat-value">{{ item.lyricsCount }}</span>
          <span class="album-stat-label">歌词</span>
        </div>
        <div class="album-stat-item">
          <span class="album-stat-value">{{ item.artworkCount }}</span>
          <span class="album-stat-label">封面</span>
        </div>
        <div v-if="item.metadataIncompleteCount > 0" class="album-stat-item album-stat-warning">
          <span class="album-stat-value">{{ item.metadataIncompleteCount }}</span>
          <span class="album-stat-label">待整理</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.album-card {
  min-width: 0;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.album-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}
.album-card-cover {
  width: 100% !important;
  height: auto !important;
  aspect-ratio: 1 / 1;
  border: 0;
  border-radius: 0 !important;
}
.album-card-avatar {
  width: 100%;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.album-initial {
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
  user-select: none;
}
.album-card-body {
  min-width: 0;
  padding: 10px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.album-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-artist {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-year {
  font-size: 12px;
  color: #909399;
}
.album-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}
.album-stat-item {
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.album-stat-value {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}
.album-stat-label {
  font-size: 11px;
  color: #909399;
}
.album-stat-warning .album-stat-value {
  color: #e6a23c;
}
</style>
