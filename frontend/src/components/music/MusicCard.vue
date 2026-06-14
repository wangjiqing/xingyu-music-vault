<script setup lang="ts">
import { Edit, MoreFilled, PictureFilled, View, Connection } from '@element-plus/icons-vue'
import type { MusicItem } from '../../api/music'
import { ARTWORK_STATUS, LYRIC_STATUS } from '../../constants/musicStatus'
import ArtworkImage from './ArtworkImage.vue'
import StatusBadges from './StatusBadges.vue'

defineProps<{
  item: MusicItem
}>()

const emit = defineEmits<{
  open: [item: MusicItem]
  edit: [item: MusicItem]
  lyric: [item: MusicItem]
  bind: [item: MusicItem]
  unbind: [item: MusicItem]
  delete: [item: MusicItem]
  sync: [item: MusicItem]
}>()

function displayTitle(item: MusicItem) {
  return item.title || item.fileName || '--'
}
</script>

<template>
  <div class="music-card">
    <div class="music-card-open" role="button" tabindex="0" @click="emit('open', item)" @keyup.enter="emit('open', item)">
      <ArtworkImage
        :src="item.artworkPreviewUrl"
        :file-exists="item.artworkFileExists"
        :alt="displayTitle(item)"
        :size="148"
        :radius="6"
        class="music-card-cover"
      />
      <div class="music-card-body">
        <div class="music-title" :title="displayTitle(item)">{{ displayTitle(item) }}</div>
        <div class="music-meta" :title="item.artist || '--'">{{ item.artist || '--' }}</div>
        <div class="music-meta" :title="item.album || '--'">{{ item.album || '--' }}</div>
        <div class="music-year">{{ item.year ?? '--' }}</div>
        <StatusBadges :item="item" compact />
      </div>
    </div>
    <div class="music-card-actions" @click.stop>
      <el-button type="primary" size="small" text :icon="Edit" @click="emit('edit', item)">
        编辑
      </el-button>
      <el-button
        v-if="item.lyricStatus === LYRIC_STATUS.BOUND"
        type="primary"
        size="small"
        text
        :icon="View"
        @click="emit('lyric', item)"
      >
        歌词
      </el-button>
      <el-dropdown
        trigger="click"
        @command="(cmd: string) => {
          if (cmd === 'sync') emit('sync', item)
          if (cmd === 'bind') emit('bind', item)
          if (cmd === 'unbind') emit('unbind', item)
          if (cmd === 'delete') emit('delete', item)
        }"
      >
        <el-button size="small" text :icon="MoreFilled" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="sync" :icon="Connection">
              元数据同步
            </el-dropdown-item>
            <el-dropdown-item command="bind" :icon="PictureFilled" divided>
              {{ item.artworkStatus === ARTWORK_STATUS.BOUND ? '更换封面' : '选择/导入封面' }}
            </el-dropdown-item>
            <el-dropdown-item v-if="item.artworkStatus === ARTWORK_STATUS.BOUND" command="unbind" divided>
              取消封面
            </el-dropdown-item>
            <el-dropdown-item command="delete" divided style="color: #f56c6c">
              移入回收站
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.music-card {
  min-width: 0;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.music-card-open {
  min-width: 0;
  cursor: pointer;
  outline: none;
}
.music-card-open:focus-visible {
  box-shadow: inset 0 0 0 2px var(--el-color-primary);
}
.music-card-cover {
  width: 100% !important;
  height: auto !important;
  aspect-ratio: 1 / 1;
  border: 0;
  border-radius: 0 !important;
}
.music-card-body {
  min-width: 0;
  padding: 10px 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.music-title,
.music-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.music-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.music-meta,
.music-year {
  font-size: 12px;
  color: #606266;
}
.music-year {
  color: #909399;
}
.music-card-actions {
  min-height: 38px;
  padding: 6px 8px 8px;
  display: flex;
  align-items: center;
  gap: 2px;
  border-top: 1px solid #f0f2f5;
}
.music-card-actions :deep(.el-button) {
  padding-left: 6px;
  padding-right: 6px;
}
</style>
