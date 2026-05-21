<script setup lang="ts">
import { computed } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import type { ArtistItem } from '../../api/music'

const props = defineProps<{
  item: ArtistItem
}>()

const emit = defineEmits<{
  click: [item: ArtistItem]
}>()

const initial = computed(() => {
  const name = props.item.artist || ''
  if (!name || name === 'Unknown') return ''
  return name.charAt(0).toUpperCase()
})

const avatarColor = computed(() => {
  const colors = [
    '#409eff', '#67c23a', '#e6a23c', '#f56c6c',
    '#909399', '#337ecc', '#529b2e', '#b88230',
    '#c45656', '#73767a',
  ]
  if (!props.item.artistKey) return colors[0]
  let hash = 0
  for (let i = 0; i < props.item.artistKey.length; i++) {
    hash = props.item.artistKey.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
})

function handleClick() {
  emit('click', props.item)
}
</script>

<template>
  <div class="artist-card" @click="handleClick">
    <div class="artist-card-avatar" :style="{ background: avatarColor }">
      <span v-if="initial" class="artist-initial">{{ initial }}</span>
      <el-icon v-else :size="28"><UserFilled /></el-icon>
    </div>
    <div class="artist-card-body">
      <div class="artist-name" :title="item.artist || 'Unknown'">
        {{ item.artist || 'Unknown' }}
      </div>
      <div class="artist-stats">
        <div class="stat-item">
          <span class="stat-value">{{ item.trackCount }}</span>
          <span class="stat-label">歌曲</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ item.albumCount }}</span>
          <span class="stat-label">专辑</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ item.lyricsCount }}</span>
          <span class="stat-label">歌词</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ item.artworkCount }}</span>
          <span class="stat-label">封面</span>
        </div>
        <div v-if="item.metadataIncompleteCount > 0" class="stat-item stat-warning">
          <span class="stat-value">{{ item.metadataIncompleteCount }}</span>
          <span class="stat-label">待整理</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.artist-card {
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
.artist-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}
.artist-card-avatar {
  width: 100%;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.artist-initial {
  font-size: 48px;
  font-weight: 700;
  line-height: 1;
  user-select: none;
}
.artist-card-body {
  min-width: 0;
  padding: 10px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.artist-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.artist-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}
.stat-item {
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.stat-value {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}
.stat-label {
  font-size: 11px;
  color: #909399;
}
.stat-warning .stat-value {
  color: #e6a23c;
}
</style>
