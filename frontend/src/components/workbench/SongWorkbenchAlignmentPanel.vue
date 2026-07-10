<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, RefreshRight, Tickets } from '@element-plus/icons-vue'
import type { MusicItem } from '../../api/music'
import {
  createLyricAlignmentJob,
  fetchLyricAlignmentJobs,
  type LyricAlignmentJob,
  type LyricAlignmentJobListItem,
  type ObservabilitySummary,
} from '../../api/lyricAlignment'
import { useAuth } from '../../composables/useAuth'
import { shortAlignmentJobId, workerHeartbeatHealthLabel } from '../../constants/lyricAlignmentStatus'
import LyricAlignmentStatusTags from '../alignment/LyricAlignmentStatusTags.vue'
import LyricAlignmentTaskDetail from '../alignment/LyricAlignmentTaskDetail.vue'

const props = defineProps<{
  music: MusicItem
  preselectedSourceLyricsId?: number | null
}>()

const emit = defineEmits<{
  (event: 'imported'): void
  (event: 'viewLyrics'): void
  (event: 'previewLyrics', jobId: string): void
}>()

const router = useRouter()
const { user } = useAuth()
const loading = ref(false)
const creating = ref(false)
const jobs = ref<LyricAlignmentJobListItem[]>([])
const selectedJobId = ref('')

const sortedJobs = computed(() =>
  [...jobs.value].sort((left, right) => timeValue(right.updatedAt || right.createdAt) - timeValue(left.updatedAt || left.createdAt)),
)
const latestJob = computed(() => sortedJobs.value[0] || null)
const selectedJob = computed(() => sortedJobs.value.find((item) => item.id === selectedJobId.value) || latestJob.value)

watch(
  () => props.music.id,
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

function formatDuration(seconds?: number | null): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return '-'
  const total = Math.max(0, Math.floor(seconds))
  const minutes = Math.floor(total / 60)
  const secs = total % 60
  return minutes > 0 ? `${minutes} 分 ${secs} 秒` : `${secs} 秒`
}

function observabilityLines(summary?: ObservabilitySummary | null): string[] {
  if (!summary) return []
  const lines: string[] = []
  const stage = summary.workerStageLabel || summary.workerStage
  if (stage) lines.push(`阶段：${stage}`)
  const heartbeat = summary.heartbeatHealth ? workerHeartbeatHealthLabel(summary.heartbeatHealth) : ''
  if (heartbeat) lines.push(`心跳：${heartbeat}${summary.heartbeatAt ? ` · ${formatTime(summary.heartbeatAt)}` : ''}`)
  const running = formatDuration(summary.runningDurationSeconds)
  if (running !== '-') lines.push(`已运行：${running}`)
  if (summary.errorCode || summary.errorSummary) {
    lines.push(`错误：${[summary.errorCode, summary.errorSummary].filter(Boolean).join(' · ')}`)
  }
  return lines
}

function errorText(error: any, fallback: string): string {
  const message = error?.response?.data?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

async function loadJobs() {
  loading.value = true
  try {
    const res = await fetchLyricAlignmentJobs({ page: 0, size: 100 })
    jobs.value = res.items.filter((item) => item.songId === props.music.id && item.taskType !== 'LYRIC_DRAFT_EXTRACTION')
    if (!selectedJobId.value || !jobs.value.some((item) => item.id === selectedJobId.value)) {
      selectedJobId.value = latestJob.value?.id || ''
    }
  } catch (error: any) {
    ElMessage.error(errorText(error, '加载歌词对齐任务失败'))
    jobs.value = []
    selectedJobId.value = ''
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  creating.value = true
  try {
    const job = await createLyricAlignmentJob({
      songId: props.music.id,
      sourceLyricsAssetId: props.preselectedSourceLyricsId || undefined,
      createdBy: user.value?.username || 'admin',
      workerOptions: { device: 'cpu' },
    })
    ElMessage.success('歌词对齐任务已创建')
    selectedJobId.value = job.id
    await loadJobs()
  } catch (error: any) {
    ElMessage.error(errorText(error, '创建歌词对齐任务失败'))
  } finally {
    creating.value = false
  }
}

function handleJobUpdated(updated: LyricAlignmentJob) {
  const index = jobs.value.findIndex((item) => item.id === updated.id)
  if (index >= 0) {
    jobs.value.splice(index, 1, updated)
  }
}

async function handleImported(updated: LyricAlignmentJob) {
  handleJobUpdated(updated)
  emit('imported')
  await loadJobs()
}

function openTaskList() {
  router.push({ path: '/lyric-alignment', query: { songId: String(props.music.id) } })
}
</script>

<template>
  <div v-loading="loading" class="alignment-workbench-panel">
    <div class="alignment-toolbar">
      <div>
        <h2>歌词对齐</h2>
        <p v-if="latestJob">最近任务：{{ shortAlignmentJobId(latestJob.id) }}</p>
        <p v-else>当前歌曲还没有歌词对齐任务</p>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="Tickets" @click="openTaskList">任务列表</el-button>
        <el-button :icon="RefreshRight" @click="loadJobs">刷新</el-button>
        <el-button type="primary" :icon="Plus" :loading="creating" @click="handleCreate">
          创建对齐任务
        </el-button>
      </div>
    </div>

    <template v-if="latestJob">
      <el-alert
        v-if="preselectedSourceLyricsId"
        class="source-alert"
        type="success"
        show-icon
        :closable="false"
        :title="`已预选候选歌词草稿人工确认资产 #${preselectedSourceLyricsId}`"
      />

      <div class="latest-strip">
        <LyricAlignmentStatusTags :job="latestJob" />
        <span>创建：{{ formatTime(latestJob.createdAt) }}</span>
        <span>完成：{{ formatTime(latestJob.completedAt) }}</span>
        <span
          v-for="line in observabilityLines(latestJob.observabilitySummary)"
          :key="line"
          class="observability-line"
        >
          {{ line }}
        </span>
      </div>

      <div v-if="sortedJobs.length > 1" class="job-switcher">
        <span>历史任务</span>
        <el-select v-model="selectedJobId" size="small" class="job-select">
          <el-option
            v-for="item in sortedJobs"
            :key="item.id"
            :label="`${shortAlignmentJobId(item.id)} · ${formatTime(item.updatedAt || item.createdAt)}`"
            :value="item.id"
          />
        </el-select>
      </div>

      <LyricAlignmentTaskDetail
        v-if="selectedJob"
        :job-id="selectedJob.id"
        @updated="handleJobUpdated"
        @imported="handleImported"
        @view-lyrics="emit('viewLyrics')"
        @preview-lyrics="(_songId, jobId) => emit('previewLyrics', jobId)"
      />
    </template>

    <el-empty v-else description="暂无歌词对齐任务" :image-size="140">
      <el-alert
        v-if="preselectedSourceLyricsId"
        class="source-alert"
        type="success"
        show-icon
        :closable="false"
        :title="`已预选候选歌词草稿人工确认资产 #${preselectedSourceLyricsId}`"
      />
      <el-button type="primary" :icon="Plus" :loading="creating" @click="handleCreate">
        创建对齐任务
      </el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.alignment-workbench-panel {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding-right: calc(8px * var(--workbench-scale, 1));
  scrollbar-width: thin;
}
.alignment-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.alignment-toolbar h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 18px;
  line-height: 1.35;
}
.alignment-toolbar p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.latest-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-fill-color-lighter) 70%, transparent);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.observability-line {
  color: var(--el-text-color-primary);
}
.source-alert {
  margin-bottom: 12px;
}
.job-switcher {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.job-select {
  width: min(420px, 100%);
}
@media (max-width: 900px) {
  .alignment-toolbar {
    flex-direction: column;
  }
  .toolbar-actions {
    justify-content: flex-start;
  }
}
</style>
