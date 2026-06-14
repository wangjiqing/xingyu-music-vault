<script setup lang="ts">
import type { MusicItem } from '../../api/music'

defineProps<{ music: MusicItem }>()

function formatDuration(duration: number | null): string {
  if (duration == null) return '--'
  const seconds = duration > 36000 ? Math.round(duration / 1000) : duration
  const minutes = Math.floor(seconds / 60)
  return `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
</script>

<template>
  <el-descriptions :column="2" border>
    <el-descriptions-item label="ID">{{ music.id }}</el-descriptions-item>
    <el-descriptions-item label="标题">{{ music.title || '--' }}</el-descriptions-item>
    <el-descriptions-item label="歌手">{{ music.artist || '--' }}</el-descriptions-item>
    <el-descriptions-item label="专辑">{{ music.album || '--' }}</el-descriptions-item>
    <el-descriptions-item label="专辑歌手">{{ music.albumArtist || '--' }}</el-descriptions-item>
    <el-descriptions-item label="年份">{{ music.year ?? '--' }}</el-descriptions-item>
    <el-descriptions-item label="流派">{{ music.genre || '--' }}</el-descriptions-item>
    <el-descriptions-item label="音轨号">{{ music.trackNo ?? '--' }}</el-descriptions-item>
    <el-descriptions-item label="时长">{{ formatDuration(music.duration) }}</el-descriptions-item>
    <el-descriptions-item label="歌词状态">{{ music.lyricStatus || '--' }}</el-descriptions-item>
    <el-descriptions-item label="封面状态">{{ music.artworkStatus || '--' }}</el-descriptions-item>
    <el-descriptions-item label="更新时间">{{ music.updatedAt || '--' }}</el-descriptions-item>
    <el-descriptions-item label="文件路径" :span="2">{{ music.filePath || '--' }}</el-descriptions-item>
  </el-descriptions>
</template>
