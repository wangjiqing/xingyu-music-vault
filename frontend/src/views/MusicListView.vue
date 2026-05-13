<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMusicList, triggerMusicScan, type MusicItem } from '../api/music'

const list = ref<MusicItem[]>([])
const loading = ref(false)
const scanning = ref(false)
const errorMessage = ref('')

const query = reactive({
  page: 1,
  size: 20,
})
const total = ref(0)

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

function formatDuration(duration: number | null): string {
  if (duration == null) return '--'
  let seconds = duration
  if (duration > 36000) {
    seconds = Math.round(duration / 1000)
  }
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await fetchMusicList({ page: query.page - 1, size: query.size })
    list.value = res.items
    total.value = res.total
  } catch {
    errorMessage.value = '加载音乐列表失败，请检查后端服务是否运行'
    ElMessage.error('加载音乐列表失败')
  } finally {
    loading.value = false
  }
}

async function handleScan() {
  scanning.value = true
  try {
    const res = await triggerMusicScan()
    ElMessage.success(`扫描任务已提交（任务ID: ${res.scanJobId}），请稍后刷新列表`)
  } catch {
    ElMessage.error('提交扫描任务失败')
  } finally {
    scanning.value = false
  }
}

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadList()
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>音乐库</span>
        <div class="header-actions">
          <el-button type="primary" size="small" :loading="scanning" @click="handleScan">
            扫描音乐目录
          </el-button>
          <el-button size="small" @click="loadList">刷新</el-button>
        </div>
      </div>
    </template>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      closable
      style="margin-bottom: 12px"
      @close="errorMessage = ''"
    />

    <el-table
      :data="list"
      v-loading="loading"
      empty-text="暂无音乐文件，请点击「扫描音乐目录」导入"
      style="width: 100%"
    >
      <el-table-column prop="title" label="歌曲名" min-width="180" show-overflow-tooltip />
      <el-table-column label="歌手" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.artist || 'Unknown' }}
        </template>
      </el-table-column>
      <el-table-column label="专辑" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.album || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="格式" width="80">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.fileExtension?.toUpperCase() || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="时长" width="80" align="center">
        <template #default="{ row }">
          {{ formatDuration(row.duration) }}
        </template>
      </el-table-column>
      <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column prop="filePath" label="文件路径" min-width="240" show-overflow-tooltip />
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
