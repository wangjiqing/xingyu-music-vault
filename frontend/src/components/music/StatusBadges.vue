<script setup lang="ts">
import type { MusicItem } from '../../api/music'
import {
  artworkStatusLabel,
  artworkStatusTagType,
  hasCompleteMusicMetadata,
  lyricStatusLabel,
  lyricStatusTagType,
} from '../../constants/musicStatus'

const props = defineProps<{
  item: MusicItem
  compact?: boolean
}>()
</script>

<template>
  <div class="status-badges" :class="{ compact }">
    <el-tag :type="lyricStatusTagType(props.item.lyricStatus)" size="small">
      {{ lyricStatusLabel(props.item.lyricStatus) }}
    </el-tag>
    <el-tag :type="artworkStatusTagType(props.item)" size="small">
      {{ artworkStatusLabel(props.item) }}
    </el-tag>
    <el-tag :type="hasCompleteMusicMetadata(props.item) ? 'success' : 'warning'" size="small">
      {{ hasCompleteMusicMetadata(props.item) ? '完整' : '待完善' }}
    </el-tag>
  </div>
</template>

<style scoped>
.status-badges {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.status-badges.compact {
  gap: 5px;
}
</style>
