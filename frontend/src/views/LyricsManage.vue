<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import {
  fetchLyricList,
  fetchLyricDetail,
  triggerLyricScan,
  type LyricListItem,
  type LyricDetail,
} from '../api/lyrics'

const list = ref<LyricListItem[]>([])
const loading = ref(false)
const scanningLyrics = ref(false)
const errorMessage = ref('')

const filter = reactive({
  page: 1,
  size: 20,
  keyword: '',
  bindStatus: '',
  parseStatus: '',
  sourceType: '',
})
const total = ref(0)

const dialogVisible = ref(false)
const dialogLoading = ref(false)
const currentLyric = ref<LyricDetail | null>(null)

const sourceTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    LOCAL_FILE: '本地文件',
    MANUAL: '手动',
    ONLINE: '在线',
  }
  return map[type] || type
}

const bindStatusLabel = (status: string) => {
  return status === 'BOUND' ? '已绑定' : '未绑定'
}

const bindStatusTagType = (status: string) => {
  return status === 'BOUND' ? 'success' : 'info'
}

const parseStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    UNKNOWN: '未知',
  }
  return map[status] || status
}

const parseStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: 'success',
    FAILED: 'danger',
    UNKNOWN: 'warning',
  }
  return map[status] || 'info'
}

const matchTypeLabel = (type: string | null) => {
  const map: Record<string, string> = {
    TITLE_ARTIST: '标题+歌手',
    TITLE: '标题匹配',
  }
  if (!type) return '--'
  return map[type] || type
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').substring(0, 19)
}

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const params: Record<string, unknown> = {
      page: filter.page - 1,
      size: filter.size,
    }
    if (filter.keyword) params.keyword = filter.keyword
    if (filter.bindStatus) params.bindStatus = filter.bindStatus
    if (filter.parseStatus) params.parseStatus = filter.parseStatus
    if (filter.sourceType) params.sourceType = filter.sourceType
    const res = await fetchLyricList(params as any)
    list.value = res.items
    total.value = res.total
  } catch {
    errorMessage.value = '加载歌词列表失败，请检查后端服务是否运行'
    ElMessage.error('加载歌词列表失败')
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  filter.page = 1
  loadList()
}

function handleReset() {
  filter.keyword = ''
  filter.bindStatus = ''
  filter.parseStatus = ''
  filter.sourceType = ''
  filter.page = 1
  loadList()
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
    ElMessage.success(`歌词扫描完成${detail}`)
    await loadList()
  } catch {
    ElMessage.error('歌词扫描失败')
  } finally {
    scanningLyrics.value = false
  }
}

async function handleViewLyric(row: LyricListItem) {
  dialogLoading.value = true
  dialogVisible.value = true
  currentLyric.value = null
  try {
    currentLyric.value = await fetchLyricDetail(row.id)
  } catch {
    ElMessage.error('加载歌词详情失败')
    dialogVisible.value = false
  } finally {
    dialogLoading.value = false
  }
}

function handlePageChange(page: number) {
  filter.page = page
  loadList()
}

function handleSizeChange(size: number) {
  filter.size = size
  filter.page = 1
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
        <span>歌词管理</span>
        <div class="header-actions">
          <el-button type="primary" size="small" :loading="scanningLyrics" @click="handleLyricScan">
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

    <div class="filter-bar">
      <el-input
        v-model="filter.keyword"
        placeholder="搜索标题、歌手、专辑、文件名..."
        clearable
        size="small"
        style="width: 280px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="filter.bindStatus"
        placeholder="绑定状态"
        clearable
        size="small"
        style="width: 120px; margin-left: 12px"
        @change="handleSearch"
      >
        <el-option label="全部" value="" />
        <el-option label="已绑定" value="BOUND" />
        <el-option label="未绑定" value="UNBOUND" />
      </el-select>
      <el-select
        v-model="filter.parseStatus"
        placeholder="解析状态"
        clearable
        size="small"
        style="width: 120px; margin-left: 12px"
        @change="handleSearch"
      >
        <el-option label="全部" value="" />
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="未知" value="UNKNOWN" />
      </el-select>
      <el-select
        v-model="filter.sourceType"
        placeholder="来源类型"
        clearable
        size="small"
        style="width: 120px; margin-left: 12px"
        @change="handleSearch"
      >
        <el-option label="全部" value="" />
        <el-option label="本地文件" value="LOCAL_FILE" />
        <el-option label="手动（即将推出）" value="MANUAL" />
        <el-option label="在线（即将推出）" value="ONLINE" />
      </el-select>
      <el-button size="small" type="primary" style="margin-left: 12px" @click="handleSearch">
        查询
      </el-button>
      <el-button size="small" style="margin-left: 8px" @click="handleReset">
        重置
      </el-button>
    </div>

    <el-table
      :data="list"
      v-loading="loading"
      empty-text="暂无歌词数据，可先扫描本地 LRC 文件"
      style="margin-top: 12px; width: 100%"
    >
      <el-table-column prop="title" label="歌词标题" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.title || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="歌手" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.artist || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="专辑" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.album || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ sourceTypeLabel(row.sourceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="bindStatusTagType(row.bindStatus)" size="small">
            {{ bindStatusLabel(row.bindStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="解析状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="parseStatusTagType(row.parseStatus)" size="small">
            {{ parseStatusLabel(row.parseStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联歌曲" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.bindStatus === 'BOUND' && row.boundSongTitle">
            {{ row.boundSongTitle }}
            <span v-if="row.boundSongArtist" style="color: #909399">
              — {{ row.boundSongArtist }}
            </span>
          </template>
          <span v-else style="color: #c0c4cc">--</span>
        </template>
      </el-table-column>
      <el-table-column label="匹配方式" width="110" align="center">
        <template #default="{ row }">
          {{ matchTypeLabel(row.matchType) }}
        </template>
      </el-table-column>
      <el-table-column label="文件路径" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.sourcePath || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
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
        v-model:current-page="filter.page"
        v-model:page-size="filter.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </el-card>

  <el-dialog
    v-model="dialogVisible"
    title="歌词详情"
    width="680px"
    destroy-on-close
  >
    <div v-loading="dialogLoading" style="min-height: 120px">
      <template v-if="currentLyric">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="歌词标题" :span="2">
            {{ currentLyric.title || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="歌手" :span="2">
            {{ currentLyric.artist || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="专辑" v-if="currentLyric.album">
            {{ currentLyric.album }}
          </el-descriptions-item>
          <el-descriptions-item label="语言" v-if="currentLyric.language">
            {{ currentLyric.language }}
          </el-descriptions-item>
          <el-descriptions-item label="来源类型">
            {{ sourceTypeLabel(currentLyric.sourceType) }}
          </el-descriptions-item>
          <el-descriptions-item label="格式">
            {{ currentLyric.format || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="解析状态">
            <el-tag :type="parseStatusTagType(currentLyric.parseStatus)" size="small">
              {{ parseStatusLabel(currentLyric.parseStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="绑定状态">
            <el-tag :type="bindStatusTagType(currentLyric.bindStatus)" size="small">
              {{ bindStatusLabel(currentLyric.bindStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文件路径" :span="2" v-if="currentLyric.sourcePath">
            {{ currentLyric.sourcePath }}
          </el-descriptions-item>
          <template v-if="currentLyric.boundSong">
            <el-descriptions-item label="绑定歌曲" :span="2">
              {{ currentLyric.boundSong.title || currentLyric.boundSong.fileName || '--' }}
              <span v-if="currentLyric.boundSong.artist" style="color: #909399">
                — {{ currentLyric.boundSong.artist }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="匹配方式">
              {{ matchTypeLabel(currentLyric.boundSong.matchType) }}
            </el-descriptions-item>
            <el-descriptions-item label="匹配分数">
              {{ currentLyric.boundSong.matchScore ?? '--' }}
            </el-descriptions-item>
          </template>
          <template v-if="currentLyric.parseMessage">
            <el-descriptions-item label="解析消息" :span="2">
              <span style="color: #f56c6c">{{ currentLyric.parseMessage }}</span>
            </el-descriptions-item>
          </template>
        </el-descriptions>
        <div
          v-if="currentLyric.boundSongs && currentLyric.boundSongs.length > 0"
          style="margin-top: 16px"
        >
          <div style="font-size: 13px; color: #909399; margin-bottom: 8px">
            全部绑定 ({{ currentLyric.boundSongs.length }})
          </div>
          <el-table :data="currentLyric.boundSongs" border size="small" max-height="200">
            <el-table-column label="歌曲" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.title || row.fileName || '--' }}
                <span v-if="row.artist" style="color: #909399"> — {{ row.artist }}</span>
              </template>
            </el-table-column>
            <el-table-column label="匹配方式" width="110">
              <template #default="{ row }">
                {{ matchTypeLabel(row.matchType) }}
              </template>
            </el-table-column>
            <el-table-column label="匹配分数" width="90" align="center">
              <template #default="{ row }">
                {{ row.matchScore ?? '--' }}
              </template>
            </el-table-column>
            <el-table-column label="主绑定" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isPrimary" type="success" size="small">是</el-tag>
                <span v-else style="color: #c0c4cc">--</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="currentLyric.content" style="margin-top: 16px">
          <div style="font-size: 13px; color: #909399; margin-bottom: 8px">LRC 原文</div>
          <pre class="lyric-content">{{ currentLyric.content }}</pre>
        </div>
        <el-empty v-else description="歌词内容为空" />
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
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 0;
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
