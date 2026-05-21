<script setup lang="ts">
import type { ArtistDetailResponse } from '../../api/music'

defineProps<{
  detail: ArtistDetailResponse
}>()
</script>

<template>
  <div class="summary-header">
    <div class="summary-artist-name">{{ detail.artist }}</div>
    <div class="summary-stats">
      <div class="summary-stat-card">
        <div class="summary-stat-num">{{ detail.trackCount }}</div>
        <div class="summary-stat-label">歌曲</div>
      </div>
      <div class="summary-stat-card">
        <div class="summary-stat-num">{{ detail.albumCount }}</div>
        <div class="summary-stat-label">专辑</div>
      </div>
      <div class="summary-stat-card summary-stat-success">
        <div class="summary-stat-num">{{ detail.lyricsCount }}</div>
        <div class="summary-stat-label">歌词</div>
      </div>
      <div class="summary-stat-card summary-stat-success">
        <div class="summary-stat-num">{{ detail.artworkCount }}</div>
        <div class="summary-stat-label">封面</div>
      </div>
      <div v-if="detail.metadataIncompleteCount > 0" class="summary-stat-card summary-stat-warning">
        <div class="summary-stat-num">{{ detail.metadataIncompleteCount }}</div>
        <div class="summary-stat-label">待整理</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.summary-header {
  margin-bottom: 16px;
}
.summary-artist-name {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 12px;
}
.summary-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.summary-stat-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 16px;
  text-align: center;
  min-width: 72px;
}
.summary-stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}
.summary-stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 1px;
}
.summary-stat-success .summary-stat-num {
  color: #67c23a;
}
.summary-stat-warning .summary-stat-num {
  color: #e6a23c;
}
</style>
