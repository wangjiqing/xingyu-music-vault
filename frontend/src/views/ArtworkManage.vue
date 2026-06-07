<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import {
  fetchArtworkList,
  triggerArtworkScan,
  type ArtworkItem,
  type ArtworkScanResponse,
} from '../api/artwork'
import { currentThemeAssets } from '../theme/currentTheme'

const items = ref<ArtworkItem[]>([])
const loading = ref(false)
const scanning = ref(false)
const scanResult = ref<ArtworkScanResponse | null>(null)
const errorMessage = ref('')

const query = reactive({
  page: 1,
  size: 20,
  boundStatus: '',
})
const total = ref(0)
const loadingMore = ref(false)
const loadedCount = computed(() => items.value.length)
const hasMoreRows = computed(() => loadedCount.value < total.value)
const loadedRangeText = computed(() => {
  if (total.value === 0) return '当前 0 - 0'
  return `当前 1 - ${Math.min(loadedCount.value, total.value)}`
})

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

function listParams(page: number) {
  const params: Record<string, unknown> = {
    page: page - 1,
    size: query.size,
  }
  if (query.boundStatus) params.boundStatus = query.boundStatus
  return params
}

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    query.page = 1
    const [currentRes, nextRes] = await Promise.all([
      fetchArtworkList(listParams(1) as any),
      fetchArtworkList(listParams(2) as any),
    ])
    items.value = [...currentRes.items, ...nextRes.items]
    total.value = nextRes.total || currentRes.total
    query.page = nextRes.items.length > 0 ? 2 : 1
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

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadList()
}

async function appendNextPage() {
  if (loadingMore.value || !hasMoreRows.value) return
  loadingMore.value = true
  try {
    const nextPage = query.page + 1
    const res = await fetchArtworkList(listParams(nextPage) as any)
    items.value = [...items.value, ...res.items]
    total.value = res.total
    if (res.items.length > 0) {
      query.page = nextPage
    }
  } finally {
    loadingMore.value = false
  }
}

function handleTableScroll(event: { scrollTop?: number; scrollHeight?: number; clientHeight?: number }) {
  const distanceToBottom = (event.scrollHeight ?? 0) - (event.scrollTop ?? 0) - (event.clientHeight ?? 0)
  if (distanceToBottom <= 520) {
    appendNextPage()
  }
}

function emptyImage(): string {
  return currentThemeAssets.value.emptyStates.cover
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
      height="calc(100vh - 356px)"
      style="width: 100%"
      @scroll="handleTableScroll"
    >
      <template #empty>
        <el-empty
          description="暂无封面，请点击「扫描封面」导入"
          :image="emptyImage()"
          :image-size="180"
        />
      </template>
      <el-table-column label="预览" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.previewUrl && row.fileExists" size="small" type="success">可预览</el-tag>
          <el-tag v-else-if="!row.fileExists" size="small" type="danger">缺失</el-tag>
          <el-tag v-else size="small" type="info">无预览</el-tag>
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
            :disabled="!row.previewUrl || !row.fileExists"
            @click="handlePreview(row)"
          >
            预览
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="cursor-pager">
      <div class="cursor-pager-meta">
        <span>共 {{ total }} 张封面</span>
        <span>{{ loadedRangeText }}</span>
        <span v-if="loadingMore">加载下一页...</span>
        <span v-else-if="!hasMoreRows">已加载全部</span>
      </div>
      <el-select
        v-model="query.size"
        size="small"
        style="width: 96px"
        @change="handleSizeChange"
      >
        <el-option label="10 条" :value="10" />
        <el-option label="20 条" :value="20" />
        <el-option label="50 条" :value="50" />
        <el-option label="100 条" :value="100" />
      </el-select>
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
.cursor-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid rgba(220, 223, 230, 0.72);
}
.cursor-pager-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: #606266;
  font-size: 12px;
}
.preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 120px;
}
</style>
