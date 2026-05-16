<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import {
  fetchArtworkList,
  triggerArtworkScan,
  type ArtworkItem,
  type ArtworkScanResponse,
} from '../api/artwork'

const items = ref<ArtworkItem[]>([])
const loading = ref(false)
const scanning = ref(false)
const scanResult = ref<ArtworkScanResponse | null>(null)
const errorMessage = ref('')
const brokenThumbs = reactive(new Set<number>())

const query = reactive({
  page: 1,
  size: 20,
  boundStatus: '',
})
const total = ref(0)

const previewVisible = ref(false)
const previewUrl = ref('')
const previewTitle = ref('')

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

function formatDimensions(item: ArtworkItem): string {
  if (item.width && item.height) return `${item.width} × ${item.height}`
  return '--'
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').substring(0, 19)
}

function mimeLabel(mime: string): string {
  const map: Record<string, string> = {
    'image/jpeg': 'JPEG',
    'image/png': 'PNG',
    'image/webp': 'WebP',
    'image/gif': 'GIF',
    'image/bmp': 'BMP',
  }
  return map[mime] || mime
}

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const params: Record<string, unknown> = {
      page: query.page - 1,
      size: query.size,
    }
    if (query.boundStatus) params.boundStatus = query.boundStatus
    const res = await fetchArtworkList(params as any)
    items.value = res.items
    total.value = res.total
  } catch {
    errorMessage.value = '加载封面列表失败，请检查后端服务是否运行'
    ElMessage.error('加载封面列表失败')
  } finally {
    loading.value = false
  }
}

async function handleScan() {
  scanning.value = true
  try {
    const res = await triggerArtworkScan()
    scanResult.value = res
    const parts: string[] = []
    if (res.imported > 0) parts.push(`新增 ${res.imported} 张`)
    if (res.autoBound > 0) parts.push(`绑定 ${res.autoBound} 首`)
    if (res.duplicateFiles > 0) parts.push(`重复 ${res.duplicateFiles} 张`)
    if (res.failed > 0) parts.push(`失败 ${res.failed} 张`)
    const detail = parts.length > 0 ? `（${parts.join('，')}）` : ''
    ElMessage.success(`封面扫描完成${detail}`)
    await loadList()
  } catch {
    ElMessage.error('封面扫描失败')
  } finally {
    scanning.value = false
  }
}

function handlePreview(row: ArtworkItem) {
  previewUrl.value = row.previewUrl
  previewTitle.value = row.fileName
  previewVisible.value = true
}

function handleBoundStatusChange() {
  query.page = 1
  loadList()
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
        <span>封面管理</span>
        <div class="header-actions">
          <el-button type="primary" size="small" :loading="scanning" @click="handleScan">
            扫描封面
          </el-button>
          <el-button size="small" @click="loadList">刷新</el-button>
        </div>
      </div>
    </template>

    <el-alert
      v-if="scanResult"
      :title="`上次扫描：${formatTime(scanResult.path)} — 共 ${scanResult.totalFiles} 个文件，新增 ${scanResult.imported}，绑定 ${scanResult.autoBound}，重复 ${scanResult.duplicateFiles}，未匹配 ${scanResult.unmatched}，失败 ${scanResult.failed}`"
      type="success"
      show-icon
      closable
      style="margin-bottom: 12px"
      @close="scanResult = null"
    />

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      closable
      style="margin-bottom: 12px"
      @close="errorMessage = ''"
    />

    <div style="font-size: 13px; color: #909399; margin-bottom: 12px">
      当前扫描本地 Artworks 目录中的封面图片，扫描完成后会自动刷新列表。
    </div>

    <div class="filter-bar">
      <el-select
        v-model="query.boundStatus"
        placeholder="绑定状态"
        clearable
        size="small"
        style="width: 130px"
        @change="handleBoundStatusChange"
      >
        <el-option label="全部" value="" />
        <el-option label="已绑定" value="bound" />
        <el-option label="未绑定" value="unbound" />
      </el-select>
    </div>

    <el-table
      :data="items"
      v-loading="loading"
      empty-text="暂无封面，请点击「扫描封面」导入"
      style="width: 100%"
    >
      <el-table-column label="缩略图" width="80" align="center">
        <template #default="{ row }">
          <img
            v-if="row.previewUrl && row.fileExists && !brokenThumbs.has(row.id)"
            :src="row.previewUrl"
            class="artwork-thumb"
            @error="brokenThumbs.add(row.id)"
          />
          <el-tag v-else-if="!row.fileExists" size="small" type="danger">缺失</el-tag>
          <span v-else style="color: #c0c4cc; font-size: 12px">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ mimeLabel(row.mimeType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="尺寸" width="110" align="center">
        <template #default="{ row }">
          {{ formatDimensions(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.sourceType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已绑定" width="80" align="center">
        <template #default="{ row }">
          {{ row.boundCount }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            text
            :icon="View"
            @click="handlePreview(row)"
          >
            预览
          </el-button>
        </template>
      </el-table-column>
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

  <el-dialog
    v-model="previewVisible"
    :title="previewTitle"
    width="600px"
    destroy-on-close
  >
    <div class="preview-container">
      <el-image
        :src="previewUrl"
        fit="contain"
        style="max-height: 480px"
        :preview-src-list="[previewUrl]"
        :preview-teleported="true"
      />
    </div>
  </el-dialog>
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
.filter-bar {
  margin-bottom: 4px;
}
.artwork-thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
}
.preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 120px;
}
</style>
