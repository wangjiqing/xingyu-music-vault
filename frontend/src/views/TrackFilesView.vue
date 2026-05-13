<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchTrackFiles, type TrackFile, type TrackFileQuery } from '../api/trackFiles'

const files = ref<TrackFile[]>([])
const loading = ref(false)

const query = reactive<TrackFileQuery>({
  page: 1,
  size: 10,
  ext: '',
  keyword: '',
})
const total = ref(0)

const extOptions = ['', '.mp3', '.flac', '.wav', '.aac', '.ogg', '.m4a']

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

async function loadFiles() {
  loading.value = true
  try {
    const res = await fetchTrackFiles({ ...query, page: query.page - 1 })
    files.value = res.items
    total.value = res.total
  } catch {
    ElMessage.error('加载音乐文件失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadFiles()
}

function handleExtChange() {
  query.page = 1
  loadFiles()
}

function handlePageChange(page: number) {
  query.page = page
  loadFiles()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadFiles()
}

onMounted(() => {
  loadFiles()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>音乐文件</span>
        <el-button size="small" @click="loadFiles">刷新</el-button>
      </div>
    </template>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索文件名"
        clearable
        style="width: 240px"
        size="small"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><span>&#128269;</span></el-icon>
        </template>
      </el-input>
      <el-select
        v-model="query.ext"
        placeholder="文件类型"
        clearable
        size="small"
        style="width: 140px; margin-left: 12px"
        @change="handleExtChange"
      >
        <el-option
          v-for="ext in extOptions"
          :key="ext"
          :label="ext || '全部类型'"
          :value="ext"
        />
      </el-select>
    </div>

    <el-table :data="files" v-loading="loading" empty-text="暂无音乐文件" style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column prop="fileExt" label="类型" width="80" />
      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column prop="filePath" label="路径" min-width="240" show-overflow-tooltip />
      <el-table-column prop="lastModifiedAt" label="修改时间" width="170" />
      <el-table-column prop="scanJobId" label="扫描任务ID" width="110" align="center" />
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
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
.filter-bar {
  display: flex;
  align-items: center;
}
</style>
