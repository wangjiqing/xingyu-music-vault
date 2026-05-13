<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTrackFiles, type TrackFile } from '../api/trackFiles'

const files = ref<TrackFile[]>([])
const loading = ref(false)

async function loadFiles() {
  loading.value = true
  try {
    files.value = await fetchTrackFiles()
  } catch {
    ElMessage.error('加载音乐文件失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadFiles()
})

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)} ${units[i]}`
}
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>音乐文件</span>
        <el-button size="small" @click="loadFiles">刷新</el-button>
      </div>
    </template>
    <el-table :data="files" v-loading="loading" empty-text="暂无音乐文件">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="filename" label="文件名" />
      <el-table-column prop="path" label="路径" />
      <el-table-column label="大小" width="120">
        <template #default="{ row }">
          {{ formatSize(row.size) }}
        </template>
      </el-table-column>
      <el-table-column prop="modifiedAt" label="修改时间" width="180" />
    </el-table>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
