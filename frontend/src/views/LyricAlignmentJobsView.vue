<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { RefreshRight, View } from '@element-plus/icons-vue'
import {
  fetchLyricAlignmentJobs,
  type LyricAlignmentJob,
  type LyricAlignmentJobListItem,
} from '../api/lyricAlignment'
import {
  alignmentExecutionStatusLabel,
  alignmentExecutionStatusTagType,
  alignmentImportStatusLabel,
  alignmentImportStatusTagType,
  alignmentReviewStatusLabel,
  alignmentReviewStatusTagType,
  alignmentWorkerOutcomeLabel,
  alignmentWorkerOutcomeTagType,
  shortAlignmentJobId,
} from '../constants/lyricAlignmentStatus'
import LyricAlignmentTaskDetail from '../components/alignment/LyricAlignmentTaskDetail.vue'

type FilterKey = 'all' | 'running' | 'pendingReview' | 'approvedPendingImport' | 'imported' | 'failed'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const jobs = ref<LyricAlignmentJobListItem[]>([])
const filter = ref<FilterKey>('all')
const drawerVisible = ref(false)
const drawerJobId = ref('')

const routeSongId = computed(() => {
  const value = Number(route.query.songId)
  return Number.isFinite(value) && value > 0 ? value : null
})

const sortedJobs = computed(() =>
  [...jobs.value].sort((left, right) => timeValue(right.updatedAt || right.createdAt) - timeValue(left.updatedAt || left.createdAt)),
)

const filteredJobs = computed(() => {
  const bySong = routeSongId.value
    ? sortedJobs.value.filter((item) => item.songId === routeSongId.value)
    : sortedJobs.value
  return bySong.filter((item) => matchesFilter(item, filter.value))
})

watch(
  () => route.query.songId,
  () => loadJobs(),
)

onMounted(() => loadJobs())

function timeValue(value?: string | null): number {
  if (!value) return 0
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : 0
}

function formatTime(value?: string | null): string {
  if (!value) return '-'
  return value.replace('T', ' ').substring(0, 19)
}

function errorText(error: any, fallback: string): string {
  const message = error?.response?.data?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

function matchesFilter(job: LyricAlignmentJobListItem, value: FilterKey): boolean {
  if (value === 'all') return true
  if (value === 'running') return ['CREATING', 'QUEUED', 'RUNNING'].includes(job.status)
  if (value === 'pendingReview') return job.status === 'COMPLETED' && job.reviewStatus === 'PENDING'
  if (value === 'approvedPendingImport') {
    return job.status === 'COMPLETED' && job.reviewStatus === 'APPROVED' && job.importStatus === 'NOT_IMPORTED'
  }
  if (value === 'imported') return job.importStatus === 'IMPORTED'
  return job.status === 'FAILED' || job.status === 'ABANDONED' || job.importStatus === 'IMPORT_FAILED'
}

async function loadJobs() {
  loading.value = true
  try {
    const res = await fetchLyricAlignmentJobs({ page: 0, size: 100 })
    jobs.value = res.items
  } catch (error: any) {
    ElMessage.error(errorText(error, '加载歌词对齐任务失败'))
  } finally {
    loading.value = false
  }
}

function openDetail(jobId: string) {
  drawerJobId.value = jobId
  drawerVisible.value = true
}

function handleJobUpdated(updated: LyricAlignmentJob) {
  const index = jobs.value.findIndex((item) => item.id === updated.id)
  if (index >= 0) {
    jobs.value.splice(index, 1, updated)
  }
}

function openWorkbench(songId: number) {
  router.push({ path: '/music/workbench', query: { id: String(songId) } })
}

function clearSongFilter() {
  router.replace({ path: '/lyric-alignment' })
}
</script>

<template>
  <div class="alignment-jobs-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span>歌词对齐任务</span>
            <el-tag v-if="routeSongId" size="small" type="info">歌曲 #{{ routeSongId }}</el-tag>
          </div>
          <div class="header-actions">
            <el-button v-if="routeSongId" size="small" text @click="clearSongFilter">清除歌曲筛选</el-button>
            <el-button :icon="RefreshRight" size="small" @click="loadJobs">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="filter-bar">
        <el-radio-group v-model="filter" size="small">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="running">运行中</el-radio-button>
          <el-radio-button value="pendingReview">待审核</el-radio-button>
          <el-radio-button value="approvedPendingImport">已通过待导入</el-radio-button>
          <el-radio-button value="imported">已导入</el-radio-button>
          <el-radio-button value="failed">失败</el-radio-button>
        </el-radio-group>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredJobs"
        empty-text="暂无歌词对齐任务"
        class="jobs-table"
      >
        <el-table-column label="任务 ID" width="150">
          <template #default="{ row }">
            <el-tooltip :content="row.id" placement="top">
              <span class="mono">{{ shortAlignmentJobId(row.id) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="歌曲" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <button class="link-button" type="button" @click="openWorkbench(row.songId)">
              歌曲 #{{ row.songId }}
            </button>
            <span v-if="row.audioRelativePath" class="song-path">{{ row.audioRelativePath }}</span>
          </template>
        </el-table-column>
        <el-table-column label="执行状态" width="118">
          <template #default="{ row }">
            <el-tag :type="alignmentExecutionStatusTagType(row.status)" size="small">
              {{ alignmentExecutionStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Worker 结果" width="126">
          <template #default="{ row }">
            <el-tag :type="alignmentWorkerOutcomeTagType(row.workerOutcome)" size="small">
              {{ alignmentWorkerOutcomeLabel(row.workerOutcome) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="118">
          <template #default="{ row }">
            <el-tag :type="alignmentReviewStatusTagType(row.reviewStatus)" size="small">
              {{ alignmentReviewStatusLabel(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="导入状态" width="108">
          <template #default="{ row }">
            <el-tag :type="alignmentImportStatusTagType(row.importStatus)" size="small">
              {{ alignmentImportStatusLabel(row.importStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" text :icon="View" @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      title="歌词对齐任务详情"
      size="72%"
      destroy-on-close
      class="alignment-detail-drawer"
    >
      <LyricAlignmentTaskDetail
        v-if="drawerJobId"
        :job-id="drawerJobId"
        @updated="handleJobUpdated"
        @imported="handleJobUpdated"
        @view-lyrics="openWorkbench"
      />
    </el-drawer>
  </div>
</template>

<style scoped>
.alignment-jobs-view {
  display: grid;
  gap: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.card-header > div:first-child,
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.filter-bar {
  margin-bottom: 12px;
}
.jobs-table {
  width: 100%;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.link-button {
  display: inline;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  font: inherit;
}
.song-path {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 900px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
