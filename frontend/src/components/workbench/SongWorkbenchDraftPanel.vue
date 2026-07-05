<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  Close,
  DocumentAdd,
  EditPen,
  Link,
  RefreshLeft,
  RefreshRight,
  Search,
  Tickets,
} from '@element-plus/icons-vue'
import type { MusicItem } from '../../api/music'
import {
  confirmLyricDraft,
  createManualLyricDraft,
  createLyricDraftJob,
  fetchMusicLyricDraftContext,
  addLyricDraftSource,
  rejectLyricDraft,
  updateLyricDraft,
  type CreateLyricDraftJobRequest,
  type MusicLyricDraftContext,
} from '../../api/lyricAlignment'
import {
  fetchBraveSearchStatus,
  searchBrave,
  type BraveSearchResult,
  type BraveSearchStatus,
} from '../../api/braveSearch'
import { useAuth } from '../../composables/useAuth'
import {
  alignmentExecutionStatusTagType,
  alignmentWorkerOutcomeLabel,
  alignmentWorkerOutcomeTagType,
  lyricDraftStatusLabel,
  lyricDraftStatusTagType,
  shortAlignmentJobId,
} from '../../constants/lyricAlignmentStatus'

const props = defineProps<{
  music: MusicItem
  currentLyricAvailable: boolean
}>()

const emit = defineEmits<{
  (event: 'trusted-confirmed', trustedLyricsId: number): void
}>()

const { user } = useAuth()
const loading = ref(false)
const actionLoading = ref(false)
const createDialogVisible = ref(false)
const manualDialogVisible = ref(false)
const braveDialogVisible = ref(false)
const textMode = ref<'editable' | 'original'>('editable')
const editorText = ref('')
const manualText = ref('')
const braveQuery = ref('')
const context = ref<MusicLyricDraftContext | null>(null)
const pollTimer = ref<number | null>(null)
const braveStatus = ref<BraveSearchStatus | null>(null)
const braveResults = ref<BraveSearchResult[]>([])
const braveLoading = ref(false)

const createForm = reactive<CreateLyricDraftJobRequest>({
  language: '',
  asrModel: '',
  skipSeparation: false,
  vadFilter: true,
})

const latestJob = computed(() => context.value?.latestJob || null)
const draft = computed(() => context.value?.draft || null)
const defaultOptions = computed(() => context.value?.defaultOptions || null)
const trustedAsset = computed(() => context.value?.trustedLyricsAsset || null)
const activeJob = computed(() =>
  latestJob.value && ['CREATING', 'QUEUED', 'RUNNING'].includes(latestJob.value.status),
)
const canCreate = computed(() => !activeJob.value)
const canEditDraft = computed(() =>
  latestJob.value?.status === 'COMPLETED' &&
  (draft.value?.draftStatus === 'PENDING_REVIEW' || draft.value?.draftStatus === 'EDITING'),
)
const isDirty = computed(() => draft.value != null && editorText.value !== draft.value.editableText)
const sourceStateLabel = computed(() => {
  if (draft.value?.draftStatus === 'CONFIRMED') return '草稿已确认'
  if (draft.value?.draftStatus === 'REJECTED') return '草稿已驳回'
  if (draft.value?.draftStatus === 'PENDING_REVIEW' || draft.value?.draftStatus === 'EDITING') return '存在待校对草稿'
  if (activeJob.value) return '草稿提取中'
  if (latestJob.value?.status === 'FAILED' || latestJob.value?.status === 'ABANDONED') return '草稿提取失败'
  return props.currentLyricAvailable ? '已有可信歌词' : '尚无可信歌词'
})
const reportEntries = computed(() => objectEntries(draft.value?.reportSummary))
const sourceEntries = computed(() => draft.value?.sources || [])
const sourceTypeLabel = computed(() => {
  const value = draft.value?.sourceType
  if (value === 'MANUAL_PASTE') return '手工粘贴'
  if (value === 'BRAVE_ASSISTED') return 'Brave 辅助来源'
  if (value === 'WORKER_EXTRACTION') return 'Worker 草稿提取'
  return value || '-'
})
const braveSearchDisabledReason = computed(() => {
  if (!braveStatus.value) return '正在读取 Brave 配置状态'
  return braveStatus.value.searchable ? '' : braveStatus.value.message
})
const signalItems = computed(() => {
  const signals = latestJob.value?.workerSignals
  if (!signals) return []
  return [
    ['READY', signals.ready],
    ['RUNNING', signals.running],
    ['status.json', signals.statusJsonAvailable],
    ['result', signals.resultDirectoryAvailable],
  ] as Array<[string, boolean]>
})

watch(
  () => props.music.id,
  () => loadContext(),
)

watch(
  () => draft.value?.jobId,
  () => {
    editorText.value = draft.value?.editableText || ''
    textMode.value = 'editable'
  },
)

watch(
  () => latestJob.value?.status,
  () => updatePolling(),
)

onMounted(() => {
  loadContext()
  loadBraveStatus()
})
onBeforeUnmount(() => stopPolling())

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

function draftExecutionLabel(status?: string | null): string {
  const map: Record<string, string> = {
    CREATING: '创建中',
    QUEUED: '等待 Worker',
    RUNNING: '草稿提取中',
    COMPLETED: '草稿已生成',
    FAILED: '草稿提取失败',
    ABANDONED: '已放弃',
  }
  return status ? map[status] || status : '-'
}

function objectEntries(value?: Record<string, unknown> | null): Array<[string, string]> {
  if (!value || typeof value !== 'object') return []
  return Object.entries(value).map(([key, item]) => [
    key,
    typeof item === 'object' ? JSON.stringify(item, null, 2) : String(item),
  ])
}

async function loadContext(showError = true) {
  loading.value = true
  try {
    context.value = await fetchMusicLyricDraftContext(props.music.id)
    if (draft.value && !isDirty.value) {
      editorText.value = draft.value.editableText
    }
    updatePolling()
  } catch (error: any) {
    if (showError) {
      ElMessage.error(errorText(error, '加载歌词草稿状态失败'))
    }
  } finally {
    loading.value = false
  }
}

async function loadBraveStatus() {
  try {
    braveStatus.value = await fetchBraveSearchStatus()
  } catch {
    braveStatus.value = {
      configured: false,
      enabled: false,
      searchable: false,
      mode: 'ERROR',
      message: 'Brave 搜索配置状态读取失败',
      encryptionAvailable: false,
      updatedAt: null,
      lastCheckedAt: null,
      lastError: null,
    }
  }
}

function updatePolling() {
  if (activeJob.value) {
    if (pollTimer.value == null) {
      pollTimer.value = window.setInterval(() => loadContext(false), 5000)
    }
    return
  }
  stopPolling()
}

function stopPolling() {
  if (pollTimer.value != null) {
    window.clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function openCreateDialog() {
  if (!defaultOptions.value) return
  createForm.language = defaultOptions.value.language
  createForm.asrModel = defaultOptions.value.asrModel
  createForm.skipSeparation = defaultOptions.value.skipSeparation
  createForm.vadFilter = defaultOptions.value.vadFilter
  createDialogVisible.value = true
}

function openManualDialog() {
  manualText.value = ''
  manualDialogVisible.value = true
}

async function handleCreateManualDraft() {
  if (!canCreate.value) return
  actionLoading.value = true
  try {
    const updated = await createManualLyricDraft(props.music.id, {
      text: manualText.value,
      createdBy: operator(),
    })
    manualDialogVisible.value = false
    context.value = context.value ? { ...context.value, draft: updated } : context.value
    editorText.value = updated.editableText
    ElMessage.success('手工草稿已创建')
    await loadContext()
  } catch (error: any) {
    ElMessage.error(errorText(error, '创建手工草稿失败'))
  } finally {
    actionLoading.value = false
  }
}

function defaultBraveQuery() {
  return `${props.music.artist || ''} ${props.music.title || props.music.fileName || ''} 歌词`.trim()
}

async function openBraveDialog() {
  await loadBraveStatus()
  braveQuery.value = defaultBraveQuery()
  braveResults.value = []
  braveDialogVisible.value = true
  if (braveStatus.value?.searchable) {
    await handleBraveSearch()
  }
}

async function handleBraveSearch() {
  if (!braveStatus.value?.searchable) return
  braveLoading.value = true
  try {
    const response = await searchBrave({ query: braveQuery.value, count: 8 })
    braveResults.value = response.results
  } catch (error: any) {
    ElMessage.error(errorText(error, 'Brave 搜索失败'))
  } finally {
    braveLoading.value = false
  }
}

function openExternal(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function handleLinkSource(result: BraveSearchResult) {
  if (!draft.value) return
  actionLoading.value = true
  try {
    await addLyricDraftSource(draft.value.jobId, {
      provider: 'BRAVE',
      query: braveQuery.value,
      title: result.title,
      url: result.url,
      domain: result.domain,
      selectedBy: operator(),
    })
    ElMessage.success('已关联为当前草稿来源')
    await loadContext()
  } catch (error: any) {
    ElMessage.error(errorText(error, '关联来源失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleCreateDraftJob() {
  if (!canCreate.value || !defaultOptions.value) return
  actionLoading.value = true
  try {
    await createLyricDraftJob(props.music.id, {
      language: createForm.language,
      asrModel: createForm.asrModel,
      skipSeparation: createForm.skipSeparation,
      vadFilter: createForm.vadFilter,
      conditionOnPreviousText: defaultOptions.value.conditionOnPreviousText,
      keepSuspectedMetadata: defaultOptions.value.keepSuspectedMetadata,
      retainIntermediate: defaultOptions.value.retainIntermediate,
      createdBy: operator(),
    })
    createDialogVisible.value = false
    ElMessage.success('歌词草稿任务已创建')
    await loadContext()
  } catch (error: any) {
    ElMessage.error(errorText(error, '创建歌词草稿任务失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleSaveDraft(text = editorText.value) {
  if (!draft.value || !canEditDraft.value) return
  actionLoading.value = true
  try {
    const updated = await updateLyricDraft(draft.value.jobId, {
      text,
      editedBy: operator(),
    })
    context.value = context.value ? { ...context.value, draft: updated } : context.value
    editorText.value = updated.editableText
    ElMessage.success('草稿已保存')
  } catch (error: any) {
    ElMessage.error(errorText(error, '保存草稿失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleRestoreOriginal() {
  if (!draft.value || !canEditDraft.value) return
  try {
    await ElMessageBox.confirm(
      '将把当前可编辑草稿恢复为原始提取文本。原始提取文本不会被修改。',
      '恢复为原始草稿',
      {
        confirmButtonText: '恢复并保存',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    editorText.value = draft.value.originalText
    await handleSaveDraft(draft.value.originalText)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(errorText(error, '恢复原始草稿失败'))
    }
  }
}

async function handleConfirmDraft() {
  if (!draft.value || !canEditDraft.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      '将把当前校对后的歌词文本保存为可信歌词资产。\n\n该操作不会替换当前已导入的 LRC / SWLRC，也不会自动创建逐字歌词对齐任务。\n确认后，该草稿将不可继续编辑。可填写确认说明：',
      '确认可信歌词',
      {
        confirmButtonText: '确认可信歌词',
        cancelButtonText: '取消',
        inputType: 'textarea',
      },
    )
    actionLoading.value = true
    if (isDirty.value) {
      await updateLyricDraft(draft.value.jobId, {
        text: editorText.value,
        editedBy: operator(),
      })
    }
    const result = await confirmLyricDraft(draft.value.jobId, {
      note: typeof value === 'string' ? value.trim() : '',
      confirmedBy: operator(),
    })
    ElMessage.success('已确认可信歌词')
    await loadContext()
    emit('trusted-confirmed', result.trustedLyricsId)
  } catch (error: any) {
    if (error === 'cancel') return
    ElMessage.error(errorText(error, '确认可信歌词失败'))
  } finally {
    actionLoading.value = false
  }
}

async function handleRejectDraft() {
  if (!draft.value || !canEditDraft.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回歌词草稿', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (value) => Boolean(value?.trim()) || '请填写驳回原因',
    })
    actionLoading.value = true
    const updated = await rejectLyricDraft(draft.value.jobId, {
      rejectNote: value.trim(),
      rejectedBy: operator(),
    })
    context.value = context.value ? { ...context.value, draft: updated } : context.value
    ElMessage.success('已驳回歌词草稿')
    await loadContext()
  } catch (error: any) {
    if (error === 'cancel') return
    ElMessage.error(errorText(error, '驳回歌词草稿失败'))
  } finally {
    actionLoading.value = false
  }
}

function handleCreateAlignmentFromDraft() {
  const lyricId = trustedAsset.value?.id || draft.value?.confirmedTrustedLyricsId
  if (lyricId) {
    emit('trusted-confirmed', lyricId)
  }
}
</script>

<template>
  <div v-loading="loading" class="draft-workbench-panel">
    <div class="draft-toolbar">
      <div>
        <h2>歌词草稿</h2>
        <p>{{ sourceStateLabel }}</p>
      </div>
      <div class="toolbar-actions">
        <el-button :icon="RefreshRight" @click="loadContext()">刷新</el-button>
        <el-button :icon="Search" @click="openBraveDialog">
          Brave 搜索
        </el-button>
        <el-button :icon="DocumentAdd" :disabled="!canCreate" @click="openManualDialog">
          手工创建草稿
        </el-button>
        <el-button
          type="primary"
          :icon="DocumentAdd"
          :disabled="!canCreate"
          :loading="actionLoading && createDialogVisible"
          @click="openCreateDialog"
        >
          生成歌词草稿
        </el-button>
      </div>
    </div>

    <div class="draft-status-grid">
      <section class="status-panel">
        <span class="status-label">当前歌词来源</span>
        <strong>{{ currentLyricAvailable ? '已有可信歌词' : '尚无可信歌词' }}</strong>
      </section>
      <section class="status-panel">
        <span class="status-label">任务状态</span>
        <el-tag :type="alignmentExecutionStatusTagType(latestJob?.status)" size="small">
          {{ draftExecutionLabel(latestJob?.status) }}
        </el-tag>
      </section>
      <section class="status-panel">
        <span class="status-label">草稿状态</span>
        <el-tag :type="lyricDraftStatusTagType(draft?.draftStatus)" size="small">
          {{ lyricDraftStatusLabel(draft?.draftStatus) }}
        </el-tag>
      </section>
      <section class="status-panel">
        <span class="status-label">Worker 结果</span>
        <el-tag :type="alignmentWorkerOutcomeTagType(latestJob?.workerOutcome)" size="small">
          {{ latestJob?.taskType === 'LYRIC_DRAFT_MANUAL' ? '未使用 Worker' : alignmentWorkerOutcomeLabel(latestJob?.workerOutcome) }}
        </el-tag>
      </section>
    </div>

    <el-alert
      v-if="activeJob"
      type="info"
      show-icon
      :closable="false"
      :title="latestJob?.workerSignals?.stageMessage || '草稿任务正在等待或执行，页面会自动刷新状态。'"
      class="draft-alert"
    />
    <el-alert
      v-else-if="latestJob?.status === 'FAILED' || latestJob?.status === 'ABANDONED'"
      type="error"
      show-icon
      :closable="false"
      :title="latestJob.errorMessage || '歌词草稿提取未成功完成'"
      class="draft-alert"
    />

    <template v-if="latestJob">
      <div class="task-strip">
        <span>任务：{{ shortAlignmentJobId(latestJob.id) }}</span>
        <span>类型：{{ latestJob.taskType === 'LYRIC_DRAFT_MANUAL' ? '手工草稿' : 'Worker 草稿提取' }}</span>
        <span>创建：{{ formatTime(latestJob.createdAt) }}</span>
        <span>完成：{{ formatTime(latestJob.completedAt) }}</span>
      </div>
      <div v-if="signalItems.length" class="signal-strip">
        <el-tag
          v-for="[label, available] in signalItems"
          :key="label"
          :type="available ? 'success' : 'info'"
          size="small"
        >
          {{ label }} {{ available ? '已出现' : '未出现' }}
        </el-tag>
      </div>
    </template>

    <template v-if="draft">
      <div class="draft-layout">
        <section class="draft-info">
          <div class="info-block">
            <h3>草稿记录</h3>
            <dl>
              <div>
                <dt>来源语义</dt>
                <dd>{{ sourceTypeLabel }}</dd>
              </div>
              <div>
                <dt>最后保存</dt>
                <dd>{{ formatTime(draft.editedAt || draft.updatedAt) }}</dd>
              </div>
              <div>
                <dt>保存人</dt>
                <dd>{{ draft.editedBy || '-' }}</dd>
              </div>
              <div v-if="draft.draftStatus === 'CONFIRMED'">
                <dt>确认</dt>
                <dd>{{ draft.confirmedBy || '-' }} · {{ formatTime(draft.confirmedAt) }}</dd>
              </div>
              <div v-if="draft.draftStatus === 'REJECTED'">
                <dt>驳回</dt>
                <dd>{{ draft.rejectedBy || '-' }} · {{ formatTime(draft.rejectedAt) }}</dd>
              </div>
            </dl>
            <p v-if="draft.rejectNote" class="note-text">{{ draft.rejectNote }}</p>
            <p v-if="draft.errorMessage" class="error-text">{{ draft.errorMessage }}</p>
          </div>

          <div class="info-block">
            <h3>质量摘要</h3>
            <p class="hint-text">用于提示草稿可靠性。手工粘贴草稿没有 Worker 质量报告时，此处会保持为空。</p>
            <dl v-if="reportEntries.length">
              <div v-for="[key, value] in reportEntries" :key="key">
                <dt>{{ key }}</dt>
                <dd>{{ value }}</dd>
              </div>
            </dl>
            <el-empty v-else description="暂无质量摘要" :image-size="88" />
          </div>

          <div class="info-block">
            <h3>来源记录</h3>
            <p class="hint-text">这里只保存候选网页元信息，不保存第三方网页歌词正文。</p>
            <dl v-if="sourceEntries.length">
              <div v-for="source in sourceEntries" :key="source.id">
                <dt>{{ source.provider }} · {{ source.domain }}</dt>
                <dd>
                  <a :href="source.url" target="_blank" rel="noopener noreferrer">{{ source.title }}</a>
                </dd>
              </div>
            </dl>
            <el-empty v-else description="暂无关联来源" :image-size="88" />
          </div>

          <div v-if="trustedAsset" class="info-block">
            <h3>可信歌词资产</h3>
            <dl>
              <div>
                <dt>资产 ID</dt>
                <dd>#{{ trustedAsset.id }}</dd>
              </div>
              <div>
                <dt>来源</dt>
                <dd>候选歌词草稿人工确认</dd>
              </div>
              <div>
                <dt>确认人</dt>
                <dd>{{ trustedAsset.confirmedBy || '-' }}</dd>
              </div>
            </dl>
            <el-button type="primary" :icon="Tickets" @click="handleCreateAlignmentFromDraft">
              使用此可信歌词创建逐字对齐任务
            </el-button>
          </div>
        </section>

        <section class="draft-editor-panel">
          <div class="editor-toolbar">
            <el-radio-group v-model="textMode" size="small">
              <el-radio-button value="editable">校对草稿</el-radio-button>
              <el-radio-button value="original">原始提取文本</el-radio-button>
            </el-radio-group>
            <div class="editor-state">
              <el-tag v-if="isDirty" type="warning" size="small">未保存</el-tag>
              <el-tag v-else type="info" size="small">已保存</el-tag>
            </div>
          </div>

          <div v-if="canEditDraft" class="draft-actions top-actions">
            <el-button
              :icon="RefreshLeft"
              :disabled="!canEditDraft || actionLoading"
              @click="handleRestoreOriginal"
            >
              恢复为原始草稿
            </el-button>
            <el-button
              type="primary"
              :icon="EditPen"
              :disabled="!canEditDraft || !isDirty"
              :loading="actionLoading"
              @click="handleSaveDraft()"
            >
              保存草稿
            </el-button>
            <el-button
              type="success"
              :icon="Check"
              :disabled="!canEditDraft"
              :loading="actionLoading"
              @click="handleConfirmDraft"
            >
              确认可信歌词
            </el-button>
            <el-button
              type="danger"
              :icon="Close"
              :disabled="!canEditDraft"
              :loading="actionLoading"
              @click="handleRejectDraft"
            >
              驳回草稿
            </el-button>
          </div>

          <el-input
            v-if="textMode === 'editable'"
            v-model="editorText"
            type="textarea"
            class="draft-textarea"
            :readonly="!canEditDraft"
            resize="none"
            spellcheck="false"
            placeholder="未对齐歌词草稿"
          />
          <pre v-else class="original-preview">{{ draft.originalText }}</pre>

        </section>
      </div>
    </template>

    <el-empty v-else-if="!activeJob" description="当前歌曲暂无可校对歌词草稿" :image-size="140">
      <el-button :icon="DocumentAdd" :disabled="!canCreate" @click="openManualDialog">
        手工创建草稿
      </el-button>
      <el-button type="primary" :icon="DocumentAdd" :disabled="!canCreate" @click="openCreateDialog">
        生成歌词草稿
      </el-button>
    </el-empty>

    <el-dialog v-model="createDialogVisible" title="生成歌词草稿" width="520px">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="歌词草稿由本地音频自动提取，可能存在错字、漏字、重复或段落顺序问题。生成后请边听边校对；未经确认的草稿不会作为正式歌词使用。"
        class="dialog-alert"
      />
      <el-form label-position="top" class="draft-create-form">
        <el-form-item label="语言">
          <el-input v-model="createForm.language" />
        </el-form-item>
        <el-form-item label="ASR 模型">
          <el-input v-model="createForm.asrModel" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="createForm.skipSeparation">跳过人声分离</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="createForm.vadFilter">启用 VAD</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleCreateDraftJob">
          创建任务
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manualDialogVisible" title="手工创建草稿" width="680px">
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="粘贴的文本会作为可编辑歌词草稿保存；不会创建 Worker 任务，也不会自动改写歌词内容。"
        class="dialog-alert"
      />
      <el-input
        v-model="manualText"
        type="textarea"
        class="manual-textarea"
        resize="none"
        spellcheck="false"
        placeholder="在这里粘贴歌词文本"
      />
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleCreateManualDraft">
          保存草稿
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="braveDialogVisible" title="Brave 搜索歌词来源" width="760px">
      <el-alert
        v-if="braveSearchDisabledReason"
        type="warning"
        show-icon
        :closable="false"
        :title="braveSearchDisabledReason"
        class="dialog-alert"
      />
      <el-alert
        v-else-if="!draft"
        type="info"
        show-icon
        :closable="false"
        title="当前歌曲还没有草稿。可以先搜索并打开来源网页；创建草稿后再关联来源记录。"
        class="dialog-alert"
      />
      <div class="brave-search-bar">
        <el-input v-model="braveQuery" maxlength="160" show-word-limit />
        <el-button
          type="primary"
          :icon="Search"
          :disabled="Boolean(braveSearchDisabledReason)"
          :loading="braveLoading"
          @click="handleBraveSearch"
        >
          搜索
        </el-button>
      </div>
      <div v-loading="braveLoading" class="brave-results">
        <div v-for="result in braveResults" :key="result.url" class="brave-result">
          <div class="brave-result-title">{{ result.title }}</div>
          <div class="brave-result-domain">{{ result.domain }}</div>
          <p>{{ result.description }}</p>
          <div class="brave-result-actions">
            <el-button :icon="Link" @click="openExternal(result.url)">新标签页打开</el-button>
            <el-button type="primary" :disabled="!draft" @click="handleLinkSource(result)">
              关联为当前草稿来源
            </el-button>
          </div>
        </div>
        <el-empty v-if="!braveLoading && braveResults.length === 0" description="暂无搜索结果" :image-size="88" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.draft-workbench-panel {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding-right: calc(8px * var(--workbench-scale, 1));
  scrollbar-width: thin;
}
.draft-toolbar,
.editor-toolbar,
.draft-actions,
.toolbar-actions,
.task-strip {
  display: flex;
  align-items: center;
  gap: 10px;
}
.draft-toolbar {
  justify-content: space-between;
  margin-bottom: 14px;
}
.draft-toolbar h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 18px;
  line-height: 1.35;
}
.draft-toolbar p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.toolbar-actions,
.draft-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}
.draft-status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.status-panel,
.info-block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-fill-color-lighter) 72%, transparent);
}
.status-panel {
  min-width: 0;
  padding: 10px 12px;
}
.status-label {
  display: block;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.status-panel strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}
.draft-alert,
.dialog-alert {
  margin-bottom: 12px;
}
.task-strip {
  flex-wrap: wrap;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.signal-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -4px 0 12px;
}
.draft-layout {
  display: grid;
  grid-template-columns: minmax(250px, 320px) minmax(0, 1fr);
  gap: 14px;
  min-height: 0;
}
.draft-info {
  display: grid;
  align-content: start;
  gap: 12px;
  min-width: 0;
}
.info-block {
  padding: 12px;
}
.info-block h3 {
  margin: 0 0 10px;
  color: var(--el-text-color-primary);
  font-size: 15px;
}
.info-block dl {
  display: grid;
  gap: 8px;
  margin: 0;
}
.info-block dl > div {
  display: grid;
  gap: 3px;
}
.info-block dt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.info-block dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-size: 13px;
  white-space: pre-wrap;
}
.note-text,
.error-text,
.hint-text {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.note-text {
  color: var(--el-text-color-secondary);
}
.hint-text {
  margin: 0 0 10px;
  color: var(--el-text-color-secondary);
}
.error-text {
  color: var(--el-color-danger);
}
.draft-editor-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 520px;
}
.editor-toolbar {
  justify-content: space-between;
  margin-bottom: 10px;
}
.top-actions {
  justify-content: flex-start;
  margin-bottom: 10px;
}
.draft-textarea {
  flex: 1;
  min-height: 0;
}
.draft-textarea :deep(.el-textarea__inner) {
  height: 100%;
  min-height: 420px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.65;
  white-space: pre-wrap;
}
.original-preview {
  flex: 1;
  min-height: 420px;
  margin: 0;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
}
.draft-actions {
  margin-top: 12px;
}
.draft-create-form {
  margin-top: 12px;
}
.manual-textarea :deep(.el-textarea__inner) {
  min-height: 360px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  line-height: 1.65;
}
.brave-search-bar,
.brave-result-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brave-results {
  min-height: 160px;
  margin-top: 14px;
}
.brave-result {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.brave-result-title {
  color: var(--el-text-color-primary);
  font-weight: 700;
  line-height: 1.4;
}
.brave-result-domain {
  margin-top: 4px;
  color: var(--el-color-primary);
  font-size: 12px;
}
.brave-result p {
  margin: 8px 0 10px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
@media (max-width: 1100px) {
  .draft-status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .draft-layout {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
  .draft-toolbar,
  .editor-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .draft-status-grid {
    grid-template-columns: 1fr;
  }
  .toolbar-actions,
  .draft-actions {
    justify-content: flex-start;
  }
}
</style>
