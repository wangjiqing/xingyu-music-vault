<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import { fetchMusicList, triggerMusicScan, type MusicItem } from '../api/music'
import { fetchSongLyric, triggerLyricScan, type SongLyric } from '../api/lyrics'

const list = ref<MusicItem[]>([])
const loading = ref(false)
const scanning = ref(false)
const scanningLyrics = ref(false)
const errorMessage = ref('')

const query = reactive({
  page: 1,
  size: 20,
})
const total = ref(0)

const lyricDialogVisible = ref(false)
const lyricLoading = ref(false)
const currentLyric = ref<SongLyric | null>(null)
const currentSongTitle = ref('')
const currentSongArtist = ref('')

const lyricStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    BOUND: '有歌词',
    NO_LYRIC: '无歌词',
    UNMATCHED: '待匹配',
    PARSE_FAILED: '解析失败',
    MISSING_FILE: '文件缺失',
  }
  return map[status] || status
}

const lyricStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    BOUND: 'success',
    NO_LYRIC: 'info',
    UNMATCHED: 'warning',
    PARSE_FAILED: 'danger',
    MISSING_FILE: 'danger',
  }
  return map[status] || 'info'
}

const sourceTypeLabel = (sourceType: string) => {
  const map: Record<string, string> = {
    LOCAL_FILE: '本地文件',
  }
  return map[sourceType] || sourceType
}

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

async function handleLyricScan() {
  scanningLyrics.value = true
  try {
    const res = await triggerLyricScan()
    const parts: string[] = []
    if (res.matched > 0) parts.push(`匹配 ${res.matched} 首`)
    if (res.unmatched > 0) parts.push(`未匹配 ${res.unmatched} 首`)
    if (res.failed > 0) parts.push(`失败 ${res.failed} 首`)
    const detail = parts.length > 0 ? `（${parts.join('，')}）` : ''
    ElMessage.success(`歌词扫描完成${detail}，请刷新列表查看`)
  } catch {
    ElMessage.error('歌词扫描失败')
  } finally {
    scanningLyrics.value = false
  }
}

async function handleViewLyric(row: MusicItem) {
  if (!row.lyricId) return
  lyricLoading.value = true
  currentSongTitle.value = row.title
  currentSongArtist.value = row.artist
  lyricDialogVisible.value = true
  currentLyric.value = null
  try {
    currentLyric.value = await fetchSongLyric(row.id)
  } catch {
    ElMessage.error('加载歌词失败')
    lyricDialogVisible.value = false
  } finally {
    lyricLoading.value = false
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
          <el-button size="small" :loading="scanningLyrics" @click="handleLyricScan">
            扫描歌词
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
      <el-table-column label="歌词" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="lyricStatusTagType(row.lyricStatus)" size="small">
            {{ lyricStatusLabel(row.lyricStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.lyricStatus === 'BOUND'"
            type="primary"
            size="small"
            text
            :icon="View"
            @click="handleViewLyric(row)"
          >
            查看歌词
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
    v-model="lyricDialogVisible"
    title="歌词详情"
    width="640px"
    destroy-on-close
  >
    <div v-loading="lyricLoading" style="min-height: 120px">
      <template v-if="currentLyric">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="歌曲标题" :span="2">
            {{ currentSongTitle }}
          </el-descriptions-item>
          <el-descriptions-item label="歌手" :span="2">
            {{ currentSongArtist || 'Unknown' }}
          </el-descriptions-item>
          <el-descriptions-item label="歌词来源">
            {{ sourceTypeLabel(currentLyric.sourceType) }}
          </el-descriptions-item>
          <el-descriptions-item label="格式">
            {{ currentLyric.format }}
          </el-descriptions-item>
          <el-descriptions-item label="歌词标题" v-if="currentLyric.title">
            {{ currentLyric.title }}
          </el-descriptions-item>
          <el-descriptions-item label="歌词艺术家" v-if="currentLyric.artist">
            {{ currentLyric.artist }}
          </el-descriptions-item>
          <el-descriptions-item label="专辑" v-if="currentLyric.album">
            {{ currentLyric.album }}
          </el-descriptions-item>
          <el-descriptions-item label="语言" v-if="currentLyric.language">
            {{ currentLyric.language }}
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px">
          <div style="font-size: 13px; color: #909399; margin-bottom: 8px">LRC 原文</div>
          <pre class="lyric-content">{{ currentLyric.content }}</pre>
        </div>
      </template>
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
.lyric-content {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
}
</style>
