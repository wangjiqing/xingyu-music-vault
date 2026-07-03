<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight, View } from '@element-plus/icons-vue'
import {
  approveLyricAlignmentJob,
  fetchLyricAlignmentArtifact,
  fetchLyricAlignmentJob,
  importLyricAlignmentJob,
  rejectLyricAlignmentJob,
  type AlignmentArtifactType,
  type LyricAlignmentJob,
} from '../../api/lyricAlignment'
import { useAuth } from '../../composables/useAuth'
import {
  alignmentExecutionStatusLabel,
  alignmentImportStatusLabel,
  alignmentReviewStatusLabel,
  alignmentWorkerOutcomeLabel,
  shortAlignmentJobId,
} from '../../constants/lyricAlignmentStatus'
import LyricAlignmentArtifactPreview from './LyricAlignmentArtifactPreview.vue'
import LyricAlignmentStatusTags from './LyricAlignmentStatusTags.vue'

interface ArtifactState {
  loading: boolean
  loaded: boolean
  content: string
  error: string
}

const props = defineProps<{
  jobId: string
}>()

const emit = defineEmits<{
  (event: 'updated', job: LyricAlignmentJob): void
  (event: 'imported', job: LyricAlignmentJob): void
  (event: 'viewLyrics', songId: number): void
}>()

const { user } = useAuth()
const loading = ref(false)
const actionLoading = ref(false)
const activeTab = ref('overview')
const job = ref<LyricAlignmentJob | null>(null)

const artifactStates = reactive<Record<AlignmentArtifactType, ArtifactState>>({
  lrc: createArtifactState(),
  swlrc: createArtifactState(),
  report: createArtifactState(),
  alignment: createArtifactState(),
})

const canReview = computed(() => job.value?.status === 'COMPLETED' && job.value?.reviewStatus === 'PENDING')
const canImport = computed(
  () =>
    job.value?.status === 'COMPLETED' &&
    job.value?.reviewStatus === 'APPROVED' &&
    job.value?.importStatus === 'NOT_IMPORTED',
)
const resultSummaryEntries = computed(() => objectEntries(job.value?.resultSummary))
const workerStatusEntries = computed(() => objectEntries(job.value?.workerStatus))
const hasResultSummary = computed(() => resultSummaryEntries.value.length > 0)

watch(
  () => props.jobId,
  () => {
    resetArtifacts()
    loadJob()
  },
  { immediate: true },
)

watch(activeTab, (value) => {
  const artifact = tabArtifact(value)
  if (artifact) {
    loadArtifact(artifact)
  }
})

function createArtifactState(): ArtifactState {
  return {
    loading: false,
    loaded: false,
    content: '',
    error: '',
  }
}

function resetArtifacts() {
  ;(Object.keys(artifactStates) as AlignmentArtifactType[]).forEach((key) => {
    artifactStates[key].loading = false
    artifactStates[key].loaded = false
    artifactStates[key].content = ''
    artifactStates[key].error = ''
  })
}

function operator(): string {
  return user.value?.username || 'admin'
}

function errorText(error: any, fallback: string): string {
  const message = error?.response?.data?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

function formatTime(value?: string | null): string {
  if (!value) return '-'
  return value.replace('T', ' ').substring(0, 19)
}

function jsonPreview(value: unknown): string {
  if (value == null) return ''
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function artifactPreview(artifact: AlignmentArtifactType): string {
  const content = artifactStates[artifact].content
  if (!content) return ''
  if (artifact === 'report' || artifact === 'alignment') {
    try {
      return JSON.stringify(JSON.parse(content), null, 2)
    } catch {
      return content
    }
  }
  return content
}

function objectEntries(value?: Record<string, unknown> | null): Array<[string, string]> {
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).map(([key, item]) => [key, typeof item === 'object' ? jsonPreview(item) : String(item)])
}

function tabArtifact(tab: string): AlignmentArtifactType | null {
  if (tab === 'lrc' || tab === 'swlrc' || tab === 'report' || tab === 'alignment') {
    return tab
  }
  return null
}

async function loadJob() {
  if (!props.jobId) return
  loading.value = true
  try {
    job.value = await fetchLyricAlignmentJob(props.jobId)
    emit('updated', job.value)
    const currentArtifact = tabArtifact(activeTab.value)
    if (currentArtifact) {
      await loadArtifact(currentArtifact, true)
    }
  } catch (error: any) {
    ElMessage.error(errorText(error, '加载歌词对齐任务失败'))
    job.value = null
  } finally {
    loading.value = false
  }
}

async function loadArtifact(artifact: AlignmentArtifactType, force = false) {
  if (!job.value || (!force && artifactStates[artifact].loaded)) return
  artifactStates[artifact].loading = true
  artifactStates[artifact].error = ''
  try {
    artifactStates[artifact].content = await fetchLyricAlignmentArtifact(job.value.id, artifact)
    artifactStates[artifact].loaded = true
  } catch (error: any) {
    artifactStates[artifact].content = ''
    artifactStates[artifact].loaded = true
    artifactStates[artifact].error = errorText(error, '结果文件不存在或暂不可读取')
  } finally {
    artifactStates[artifact].loading = false
  }
}

async function handleApprove() {
  if (!job.value || !canReview.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      '通过审核不会立即替换现有歌词，仍需下一步确认导入。可填写审核说明：',
      '审核通过',
      {
        confirmButtonText: '审核通过',
        cancelButtonText: '取消',
        inputType: 'textarea',
      },
    )
    actionLoading.value = true
    job.value = await approveLyricAlignmentJob(job.value.id, {
      reviewedBy: operator(),
      reviewNote: typeof value === 'string' ? value.trim() : '',
    })
    ElMessage.success('已审核通过')
    emit('updated', job.value)
  } catch (error: any) {
    if (error === 'cancel') return
    ElMessage.error(errorText(error, '审核通过失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleReject() {
  if (!job.value || !canReview.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回对齐结果', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value) => Boolean(value?.trim()) || '请填写驳回原因',
    })
    actionLoading.value = true
    job.value = await rejectLyricAlignmentJob(job.value.id, {
      reviewedBy: operator(),
      reviewNote: value.trim(),
    })
    ElMessage.success('已驳回')
    emit('updated', job.value)
  } catch (error: any) {
    if (error === 'cancel') return
    ElMessage.error(errorText(error, '驳回失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleImport() {
  if (!job.value || !canImport.value) return
  try {
    await ElMessageBox.confirm(
      '将导入本次审核通过的 LRC 与 SWLRC 对齐结果。\n\n原始可信歌词将被保留，不会删除。\n导入后，新的已确认对齐歌词会成为该歌曲当前可用歌词版本。\n该操作不会修改音频文件。',
      '确认导入逐字歌词',
      {
        confirmButtonText: '确认导入',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    actionLoading.value = true
    await importLyricAlignmentJob(job.value.id, { importedBy: operator() })
    ElMessage.success('导入完成')
    await loadJob()
    if (job.value) {
      emit('imported', job.value)
    }
  } catch (error: any) {
    if (error === 'cancel') return
    ElMessage.error(errorText(error, '导入失败'))
    await loadJob()
  } finally {
    actionLoading.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="alignment-task-detail">
    <template v-if="job">
      <div class="detail-header">
        <div>
          <div class="detail-title">
            任务 {{ shortAlignmentJobId(job.id) }}
            <el-tooltip :content="job.id" placement="top">
              <el-tag size="small" type="info">UUID</el-tag>
            </el-tooltip>
          </div>
          <LyricAlignmentStatusTags :job="job" />
        </div>
        <div class="detail-actions">
          <el-button :icon="RefreshRight" :loading="loading" @click="loadJob">刷新</el-button>
          <el-button v-if="canReview" type="success" :loading="actionLoading" @click="handleApprove">
            审核通过
          </el-button>
          <el-button v-if="canReview" type="danger" plain :loading="actionLoading" @click="handleReject">
            驳回
          </el-button>
          <el-button v-if="canImport" type="primary" :loading="actionLoading" @click="handleImport">
            确认导入逐字歌词
          </el-button>
          <el-button
            v-if="job.importStatus === 'IMPORTED'"
            :icon="View"
            @click="emit('viewLyrics', job.songId)"
          >
            查看当前歌词
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="job.errorMessage"
        class="detail-alert"
        type="error"
        show-icon
        :title="job.errorMessage"
      />
      <el-alert
        v-if="job.importErrorMessage"
        class="detail-alert"
        type="error"
        show-icon
        :title="job.importErrorMessage"
      />

      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="概览" name="overview">
          <div class="overview-grid">
            <el-descriptions title="任务基本信息" :column="2" border>
              <el-descriptions-item label="歌曲 ID">{{ job.songId }}</el-descriptions-item>
              <el-descriptions-item label="歌词 ID">{{ job.lyricId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="执行状态">
                {{ alignmentExecutionStatusLabel(job.status) }}
              </el-descriptions-item>
              <el-descriptions-item label="Worker 结论">
                {{ alignmentWorkerOutcomeLabel(job.workerOutcome) }}
              </el-descriptions-item>
              <el-descriptions-item label="审核状态">
                {{ alignmentReviewStatusLabel(job.reviewStatus) }}
              </el-descriptions-item>
              <el-descriptions-item label="导入状态">
                {{ alignmentImportStatusLabel(job.importStatus) }}
              </el-descriptions-item>
              <el-descriptions-item label="创建人">{{ job.createdBy || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatTime(job.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ formatTime(job.startedAt) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ formatTime(job.completedAt) }}</el-descriptions-item>
              <el-descriptions-item label="音频相对路径" :span="2">
                {{ job.audioRelativePath || '-' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-descriptions title="审核与导入" :column="2" border>
              <el-descriptions-item label="审核人">{{ job.reviewedBy || '-' }}</el-descriptions-item>
              <el-descriptions-item label="审核时间">{{ formatTime(job.reviewedAt) }}</el-descriptions-item>
              <el-descriptions-item label="审核说明" :span="2">
                {{ job.reviewNote || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="导入人">{{ job.importedBy || '-' }}</el-descriptions-item>
              <el-descriptions-item label="导入时间">{{ formatTime(job.importedAt) }}</el-descriptions-item>
              <el-descriptions-item label="导入歌词 ID">{{ job.importedLyricId || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-descriptions title="结果文件" :column="2" border>
              <el-descriptions-item label="结果可用">{{ job.resultAvailable ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="可信歌词 Hash">{{ job.trustedLyricsHash || '-' }}</el-descriptions-item>
              <el-descriptions-item label="LRC Hash">{{ job.lrcHash || '-' }}</el-descriptions-item>
              <el-descriptions-item label="SWLRC Hash">{{ job.swlrcHash || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Report Hash">{{ job.reportHash || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Alignment Hash">{{ job.alignmentJsonHash || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-card shadow="never">
              <template #header>质量报告摘要</template>
              <el-descriptions v-if="hasResultSummary" :column="1" border>
                <el-descriptions-item
                  v-for="[key, value] in resultSummaryEntries"
                  :key="key"
                  :label="key"
                >
                  <pre class="inline-json">{{ value }}</pre>
                </el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="暂无质量摘要" :image-size="90" />
            </el-card>

            <el-card v-if="workerStatusEntries.length > 0" shadow="never">
              <template #header>Worker 状态摘要</template>
              <el-descriptions :column="1" border>
                <el-descriptions-item
                  v-for="[key, value] in workerStatusEntries"
                  :key="key"
                  :label="key"
                >
                  <pre class="inline-json">{{ value }}</pre>
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="LRC" name="lrc">
          <LyricAlignmentArtifactPreview
            title="LRC 结果"
            :state="artifactStates.lrc"
            :content="artifactPreview('lrc')"
            @refresh="loadArtifact('lrc', true)"
          />
        </el-tab-pane>
        <el-tab-pane label="逐字歌词 SWLRC" name="swlrc">
          <LyricAlignmentArtifactPreview
            title="SWLRC 结果"
            :state="artifactStates.swlrc"
            :content="artifactPreview('swlrc')"
            @refresh="loadArtifact('swlrc', true)"
          />
        </el-tab-pane>
        <el-tab-pane label="质量报告" name="report">
          <LyricAlignmentArtifactPreview
            title="report.json"
            :state="artifactStates.report"
            :content="artifactPreview('report')"
            json
            @refresh="loadArtifact('report', true)"
          />
        </el-tab-pane>
        <el-tab-pane label="alignment.json" name="alignment">
          <LyricAlignmentArtifactPreview
            title="alignment.json"
            :state="artifactStates.alignment"
            :content="artifactPreview('alignment')"
            json
            @refresh="loadArtifact('alignment', true)"
          />
        </el-tab-pane>
        <el-tab-pane label="原始可信歌词" name="trusted">
          <div class="artifact-panel">
            <div class="artifact-header">
              <span>创建任务时的可信歌词快照</span>
            </div>
            <pre v-if="job.trustedLyricsSnapshot" class="text-preview">{{ job.trustedLyricsSnapshot }}</pre>
            <el-empty v-else description="没有可信歌词快照" :image-size="90" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-empty v-else-if="!loading" description="任务不存在或暂不可读取" :image-size="120" />
  </div>
</template>

<style scoped>
.alignment-task-detail {
  min-height: 220px;
}
.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 700;
}
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.detail-alert {
  margin-bottom: 12px;
}
.detail-tabs {
  min-height: 0;
}
.overview-grid {
  display: grid;
  gap: 14px;
}
.artifact-panel {
  display: flex;
  flex-direction: column;
  min-height: 360px;
  gap: 10px;
}
.artifact-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 700;
}
.inline-json {
  max-height: 180px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-regular);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}
.text-preview {
  flex: 1;
  min-height: 320px;
  max-height: min(62vh, 680px);
  margin: 0;
  overflow: auto;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-fill-color-lighter) 72%, transparent);
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.text-preview.json {
  white-space: pre;
}
@media (max-width: 900px) {
  .detail-header {
    flex-direction: column;
  }
  .detail-actions {
    justify-content: flex-start;
  }
}
</style>
