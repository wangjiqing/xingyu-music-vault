<script setup lang="ts">
import { computed } from 'vue'
import type {
  AlignmentArtifactType,
  LyricTaskObservabilityResponse,
  WorkerOutput,
} from '../../api/lyricAlignment'
import {
  lyricDraftPresetLabel,
  workerHeartbeatHealthLabel,
  workerHeartbeatHealthTagType,
} from '../../constants/lyricAlignmentStatus'

const props = defineProps<{
  observability: LyricTaskObservabilityResponse | null
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  (event: 'refresh'): void
  (event: 'viewArtifact', artifact: AlignmentArtifactType): void
}>()

const compatibilityMessages = computed(() => {
  const messages = [...(props.observability?.compatibilityMessages || [])]
  if (props.observability?.legacy) {
    messages.unshift('该任务由旧版 Worker 创建，仅支持基础状态展示。')
  }
  if (props.observability && !props.observability.directoryAvailable) {
    messages.unshift('任务目录不存在或已清理，仅显示数据库中的任务信息。')
  }
  if (props.observability?.statusParseError) {
    messages.unshift('status.json 暂时无法读取，已保留任务基础状态。')
  }
  return Array.from(new Set(messages.filter(Boolean)))
})

const configEntries = computed(() => objectEntries(props.observability?.configSummary))
const requestedConfigPreview = computed(() => jsonPreview(props.observability?.requestedConfig))
const resolvedConfigPreview = computed(() => jsonPreview(props.observability?.resolvedConfig))
const warningsPreview = computed(() => jsonPreview(props.observability?.warnings))
const resultPreview = computed(() => jsonPreview(props.observability?.result))
const progressPreview = computed(() => jsonPreview(props.observability?.progress))

function valueText(value?: string | number | boolean | null): string {
  if (value === null || value === undefined || value === '') return '--'
  return String(value)
}

function formatTime(value?: string | null): string {
  if (!value) return '--'
  return value.replace('T', ' ').substring(0, 19)
}

function formatClock(value?: string | null): string {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return formatTime(value)
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

function formatDuration(seconds?: number | null): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return '--'
  const total = Math.max(0, Math.floor(seconds))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const secs = total % 60
  if (hours > 0) return `${hours} 小时 ${minutes} 分`
  if (minutes > 0) return `${minutes} 分 ${secs} 秒`
  return `${secs} 秒`
}

function configLabel(key: string): string {
  const map: Record<string, string> = {
    preset: '识别模式',
    language: '语言',
    asrModel: 'ASR 模型',
    vadFilter: '启用 VAD',
    skipSeparation: '跳过人声分离',
    skipVocalSeparation: '跳过人声分离',
    retainIntermediate: '保留中间文件',
    keepIntermediate: '保留中间文件',
    conditionOnPreviousText: '关联前文',
    keepSuspectedMetadata: '保留疑似元信息',
  }
  return map[key] || key
}

function configValue(key: string, value: unknown): string {
  if (key === 'preset') return typeof value === 'string' ? lyricDraftPresetLabel(value) : '--'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (value === null || value === undefined || value === '') return '--'
  if (typeof value === 'object') return jsonPreview(value)
  return String(value)
}

function objectEntries(value?: Record<string, unknown> | null): Array<[string, string]> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return []
  return Object.entries(value).map(([key, item]) => [configLabel(key), configValue(key, item)])
}

function jsonPreview(value: unknown): string {
  if (value == null) return ''
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function outputLabel(type?: string | null): string {
  const map: Record<string, string> = {
    report: '质量报告',
    lrc: 'LRC',
    swlrc: '逐字歌词 SWLRC',
    alignment: '对齐 JSON',
    cleanedTranscript: '清洗后识别文本',
    rawTranscript: '原始识别文本',
    segments: '识别片段',
    stderrLog: '排障日志',
  }
  return type ? map[type] || type : '--'
}

function artifactForOutput(output: WorkerOutput): AlignmentArtifactType | null {
  if (output.type === 'report' || output.type === 'lrc' || output.type === 'swlrc' || output.type === 'alignment') {
    return output.type
  }
  return null
}

function viewOutput(output: WorkerOutput) {
  const artifact = artifactForOutput(output)
  if (artifact) {
    emit('viewArtifact', artifact)
  }
}

function levelTagType(level?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN' || level === 'WARNING') return 'warning'
  if (level === 'INFO') return 'success'
  return 'info'
}
</script>

<template>
  <div v-loading="loading" class="worker-observability-panel">
    <div class="panel-header">
      <div>
        <h3>Worker 可观测性</h3>
        <p>展示 Worker 写入的状态、阶段、配置、输出和最近事件。</p>
      </div>
      <el-button size="small" @click="emit('refresh')">刷新</el-button>
    </div>

    <el-alert v-if="error" type="warning" show-icon :closable="false" :title="error" class="panel-alert" />

    <template v-if="observability">
      <el-alert
        v-for="message in compatibilityMessages"
        :key="message"
        class="panel-alert"
        type="info"
        show-icon
        :closable="false"
        :title="message"
      />

      <section v-if="observability.error" class="observability-section error-section">
        <h4>错误与排障</h4>
        <dl class="kv-grid">
          <div>
            <dt>失败阶段</dt>
            <dd>{{ valueText(observability.workerStageLabel || observability.workerStage) }}</dd>
          </div>
          <div>
            <dt>错误码</dt>
            <dd>{{ valueText(observability.error.code) }}</dd>
          </div>
          <div>
            <dt>错误说明</dt>
            <dd>{{ valueText(observability.error.label) }}</dd>
          </div>
          <div>
            <dt>原始消息</dt>
            <dd>{{ valueText(observability.error.message) }}</dd>
          </div>
          <div>
            <dt>排障日志</dt>
            <dd>
              <el-tooltip content="来自 Worker attempt 记录的 stderr 相对路径；这里只显示路径，不读取日志内容。" placement="top">
                <span>{{ valueText(observability.attempt?.stderrPath) }}</span>
              </el-tooltip>
            </dd>
          </div>
        </dl>
      </section>

      <section class="observability-section">
        <h4>当前状态</h4>
        <dl class="kv-grid">
          <div>
            <dt>Worker 状态</dt>
            <dd>{{ valueText(observability.workerStateLabel || observability.workerState) }}</dd>
          </div>
          <div>
            <dt>当前阶段</dt>
            <dd>{{ valueText(observability.workerStageLabel || observability.workerStage) }}</dd>
          </div>
          <div>
            <dt>阶段说明</dt>
            <dd>{{ valueText(observability.workerStageDescription) }}</dd>
          </div>
          <div>
            <dt>协议版本</dt>
            <dd>
              status {{ valueText(observability.statusSchemaVersion) }} · request
              {{ valueText(observability.requestSchemaVersion) }}
            </dd>
          </div>
          <div>
            <dt>任务目录</dt>
            <dd>{{ observability.directoryAvailable ? '可用' : '不可用' }}</dd>
          </div>
          <div>
            <dt>status.json</dt>
            <dd>{{ observability.statusAvailable ? '可用' : '暂无' }}</dd>
          </div>
        </dl>
      </section>

      <section class="observability-section">
        <h4>心跳与耗时</h4>
        <dl class="kv-grid">
          <div>
            <dt>心跳健康</dt>
            <dd>
              <el-tag :type="workerHeartbeatHealthTagType(observability.heartbeatHealth)" size="small">
                {{ workerHeartbeatHealthLabel(observability.heartbeatHealth) }}
              </el-tag>
            </dd>
          </div>
          <div>
            <dt>最近心跳</dt>
            <dd>{{ formatTime(observability.heartbeatAt) }}</dd>
          </div>
          <div>
            <dt>已运行</dt>
            <dd>{{ formatDuration(observability.runningDurationSeconds) }}</dd>
          </div>
          <div>
            <dt>当前阶段</dt>
            <dd>{{ formatDuration(observability.stageDurationSeconds) }}</dd>
          </div>
          <div>
            <dt>开始时间</dt>
            <dd>{{ formatTime(observability.startedAt) }}</dd>
          </div>
          <div>
            <dt>阶段开始</dt>
            <dd>{{ formatTime(observability.stageStartedAt) }}</dd>
          </div>
          <div>
            <dt>更新时间</dt>
            <dd>{{ formatTime(observability.updatedAt) }}</dd>
          </div>
        </dl>
      </section>

      <section class="observability-section">
        <h4>执行配置</h4>
        <dl v-if="configEntries.length" class="kv-grid">
          <div v-for="[key, value] in configEntries" :key="key">
            <dt>{{ key }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
        <el-empty v-else description="暂无配置摘要" :image-size="72" />

        <el-collapse class="json-collapse">
          <el-collapse-item v-if="requestedConfigPreview" title="requestedConfig" name="requested">
            <pre class="json-preview">{{ requestedConfigPreview }}</pre>
          </el-collapse-item>
          <el-collapse-item v-if="resolvedConfigPreview" title="resolvedConfig" name="resolved">
            <pre class="json-preview">{{ resolvedConfigPreview }}</pre>
          </el-collapse-item>
          <el-collapse-item v-if="progressPreview" title="progress" name="progress">
            <pre class="json-preview">{{ progressPreview }}</pre>
          </el-collapse-item>
          <el-collapse-item v-if="warningsPreview" title="warnings" name="warnings">
            <pre class="json-preview">{{ warningsPreview }}</pre>
          </el-collapse-item>
          <el-collapse-item v-if="resultPreview" title="result" name="result">
            <pre class="json-preview">{{ resultPreview }}</pre>
          </el-collapse-item>
        </el-collapse>
        <p v-if="!requestedConfigPreview && !resolvedConfigPreview" class="hint-text">暂无配置详情</p>
      </section>

      <section class="observability-section">
        <h4>输出文件</h4>
        <div v-if="observability.outputs?.length" class="output-list">
          <div
            v-for="(output, index) in observability.outputs"
            :key="output.type || output.relativePath || index"
            class="output-item"
          >
            <div>
              <strong>
                {{ outputLabel(output.type) }}
                <el-tooltip
                  v-if="output.type === 'stderrLog'"
                  content="来自输出目录扫描，仅表示排障日志文件是否存在；不会读取日志内容。"
                  placement="top"
                >
                  <span class="help-dot">?</span>
                </el-tooltip>
              </strong>
              <span>{{ output.relativePath || '--' }}</span>
            </div>
            <div class="output-actions">
              <el-tag :type="output.available ? 'success' : 'info'" size="small">
                {{ output.available ? '已生成' : '未生成' }}
              </el-tag>
              <el-button
                v-if="output.available && artifactForOutput(output)"
                size="small"
                text
                @click="viewOutput(output)"
              >
                查看
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无输出文件信息" :image-size="72" />
      </section>

      <section class="observability-section">
        <h4>最近事件</h4>
        <el-alert
          v-if="observability.eventsReadError"
          class="panel-alert"
          type="warning"
          show-icon
          :closable="false"
          :title="observability.eventsReadError"
        />
        <el-alert
          v-if="observability.eventsTruncated"
          class="panel-alert"
          type="info"
          show-icon
          :closable="false"
          title="事件文件较大，当前仅展示最近事件。"
        />
        <div v-if="observability.events?.length" class="event-list">
          <div v-for="(event, index) in observability.events" :key="event.eventId || index" class="event-item">
            <div class="event-meta">
              <span>{{ formatClock(event.timestamp) }}</span>
              <el-tag :type="levelTagType(event.level)" size="small">{{ event.level || 'EVENT' }}</el-tag>
              <span>{{ event.stage || event.type || '--' }}</span>
            </div>
            <div class="event-message">{{ event.message || event.type || '--' }}</div>
            <el-collapse v-if="event.details" class="json-collapse event-details">
              <el-collapse-item title="details" :name="String(index)">
                <pre class="json-preview">{{ jsonPreview(event.details) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
        <el-empty v-else :description="observability.eventsAvailable ? '暂无事件' : '暂无事件流'" :image-size="72" />
      </section>
    </template>

    <el-empty v-else-if="!loading" description="暂无 Worker 可观测信息" :image-size="90" />
  </div>
</template>

<style scoped>
.worker-observability-panel {
  display: grid;
  gap: 12px;
}
.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.panel-header h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
}
.panel-header p,
.hint-text {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.panel-alert {
  margin-bottom: 0;
}
.observability-section {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-fill-color-lighter) 70%, transparent);
}
.observability-section h4 {
  margin: 0 0 10px;
  color: var(--el-text-color-primary);
  font-size: 15px;
}
.error-section {
  border-color: color-mix(in srgb, var(--el-color-danger) 36%, var(--el-border-color-lighter));
  background: color-mix(in srgb, var(--el-color-danger-light-9) 70%, transparent);
}
.kv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
  margin: 0;
}
.kv-grid > div {
  min-width: 0;
}
.kv-grid dt {
  margin-bottom: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.kv-grid dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.5;
}
.json-collapse {
  margin-top: 10px;
}
.json-preview {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-regular);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}
.output-list,
.event-list {
  display: grid;
  gap: 8px;
}
.output-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.output-item:last-child {
  border-bottom: 0;
}
.output-item strong,
.output-item span {
  display: block;
}
.output-item strong {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--el-text-color-primary);
  font-size: 13px;
}
.output-item .help-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 999px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1;
}
.output-item span {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.output-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.event-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.event-item:last-child {
  border-bottom: 0;
}
.event-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.event-message {
  margin-top: 6px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.6;
}
.event-details {
  margin-top: 6px;
}
@media (max-width: 720px) {
  .panel-header,
  .output-item {
    flex-direction: column;
    align-items: flex-start;
  }
  .kv-grid {
    grid-template-columns: 1fr;
  }
}
</style>
