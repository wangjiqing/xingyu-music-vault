<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchMetadataRollbackPreview,
  rollbackMetadataAudit,
  batchFetchMetadataRollbackPreview,
  batchRollbackMetadataAudits,
  type MetadataRollbackPreviewResponse,
  type BatchMetadataRollbackPreviewResponse,
  type BatchMetadataRollbackResponse,
} from '../../api/music'

const emit = defineEmits<{
  done: []
  close: []
}>()

const FIELD_LABELS: Record<string, string> = {
  title: '标题',
  artist: '歌手',
  album: '专辑',
}

const COMPARABLE_FIELDS = ['title', 'artist', 'album']

const visible = ref(false)
const loading = ref(false)
const executing = ref(false)
const mode = ref<'single' | 'batch'>('single')
const singlePreview = ref<MetadataRollbackPreviewResponse | null>(null)
const batchPreview = ref<BatchMetadataRollbackPreviewResponse | null>(null)
const batchResult = ref<BatchMetadataRollbackResponse | null>(null)
const singleAuditId = ref(0)

function fieldLabel(field: string): string {
  return FIELD_LABELS[field] || field
}

function displayValue(val: unknown): string {
  if (val == null || val === '') return '--'
  return String(val)
}

function rollbackTargetLabel(target: string): string {
  return target === 'embedded_tag' ? '文件 Tag' : target === 'database' ? '数据库' : target
}

async function openSingle(auditId: number) {
  mode.value = 'single'
  singleAuditId.value = auditId
  visible.value = true
  loading.value = true
  singlePreview.value = null
  batchPreview.value = null
  batchResult.value = null
  try {
    singlePreview.value = await fetchMetadataRollbackPreview(auditId)
    if (singlePreview.value) {
      singlePreview.value = {
        ...singlePreview.value,
        diffs: singlePreview.value.diffs.filter((d) => COMPARABLE_FIELDS.includes(d.field)),
      }
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || '加载回滚预览失败'
    ElMessage.error(msg)
    visible.value = false
  } finally {
    loading.value = false
  }
}

async function openBatch(auditIds: number[]) {
  mode.value = 'batch'
  visible.value = true
  loading.value = true
  singlePreview.value = null
  batchPreview.value = null
  batchResult.value = null
  try {
    batchPreview.value = await batchFetchMetadataRollbackPreview(auditIds)
    if (batchPreview.value) {
      batchPreview.value = {
        ...batchPreview.value,
        items: batchPreview.value.items.map((item) => ({
          ...item,
          diffs: item.diffs.filter((d) => COMPARABLE_FIELDS.includes(d.field)),
        })),
      }
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || '加载批量回滚预览失败'
    ElMessage.error(msg)
    visible.value = false
  } finally {
    loading.value = false
  }
}

async function handleSingleExecute() {
  if (!singlePreview.value) return
  const isFileTarget = singlePreview.value.rollbackTarget === 'embedded_tag'
  const confirmText = isFileTarget
    ? '将使用历史审计记录中的文件 Tag 快照写回音频文件。\n这会直接修改本地音频文件，请确认你已经备份重要文件。\n是否继续？'
    : '将使用历史审计记录中的数据库快照恢复当前数据库元数据。\n这会修改系统中展示的标题、歌手、专辑等信息，但不会修改音频文件本身。\n是否继续？'
  const confirmTitle = isFileTarget ? '确认：回滚文件 Tag' : '确认：回滚数据库'

  try {
    await ElMessageBox.confirm(confirmText, confirmTitle, {
      confirmButtonText: '确认回滚',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  executing.value = true
  try {
    const res = await rollbackMetadataAudit(singleAuditId.value)
    if (res.success) {
      ElMessage.success(res.message || '回滚完成')
    } else {
      ElMessage.error(res.errorMessage || '回滚失败')
    }
    visible.value = false
    emit('done')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '回滚失败'
    ElMessage.error(msg)
  } finally {
    executing.value = false
  }
}

async function handleBatchExecute() {
  if (!batchPreview.value || batchPreview.value.canRollbackCount === 0) return

  const anyFileTarget = batchPreview.value.items.some((i) => i.rollbackTarget === 'embedded_tag')
  const confirmText = anyFileTarget
    ? `将回滚 ${batchPreview.value.canRollbackCount} 条审计记录。\n其中包含对音频文件内嵌 Tag 的写回操作，会直接修改本地音频文件。\n请确认你已经备份重要文件。是否继续？`
    : `将回滚 ${batchPreview.value.canRollbackCount} 条审计记录的数据库元数据。\n这会修改系统中展示的信息，但不会修改音频文件本身。是否继续？`

  try {
    await ElMessageBox.confirm(confirmText, '确认：批量回滚', {
      confirmButtonText: '确认回滚',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  executing.value = true
  try {
    const ids = batchPreview.value.items.map((i) => i.auditId)
    batchResult.value = await batchRollbackMetadataAudits(ids)
    if (batchResult.value.failed === 0) {
      ElMessage.success(`全部完成：成功 ${batchResult.value.success} 条`)
    } else if (batchResult.value.success === 0) {
      ElMessage.error(`全部失败：${batchResult.value.failed} 条`)
    } else {
      ElMessage.warning(`成功 ${batchResult.value.success} 条，失败 ${batchResult.value.failed} 条`)
    }
    emit('done')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '批量回滚失败'
    ElMessage.error(msg)
  } finally {
    executing.value = false
  }
}

defineExpose({ openSingle, openBatch })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'batch' ? '批量回滚' : '回滚预览'"
    width="800px"
    destroy-on-close
    @closed="emit('close')"
  >
    <div v-loading="loading" style="min-height: 120px">
      <!-- Single preview -->
      <template v-if="mode === 'single' && singlePreview">
        <el-alert
          v-if="!singlePreview.canRollback"
          :title="singlePreview.errorMessage || '该记录不可回滚'"
          type="error"
          show-icon
          style="margin-bottom: 16px"
        />

        <template v-if="singlePreview.canRollback">
          <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
            <el-descriptions-item label="回滚目标">
              <el-tag size="small" :type="singlePreview.rollbackTarget === 'embedded_tag' ? 'warning' : 'primary'">
                {{ rollbackTargetLabel(singlePreview.rollbackTarget) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="音乐 ID">
              {{ singlePreview.musicId }}
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="singlePreview.warnings && singlePreview.warnings.length > 0"
            v-for="w in singlePreview.warnings"
            :key="w"
            :title="w"
            type="warning"
            show-icon
            style="margin-bottom: 12px"
          />

          <div v-if="singlePreview.diffs.length > 0">
            <h4 style="margin-bottom: 8px; font-size: 14px; color: #303133">将发生以下变更：</h4>
            <el-table
              :data="singlePreview.diffs"
              border
              size="small"
              style="width: 100%"
            >
              <el-table-column label="字段" width="110" align="center">
                <template #default="{ row }">
                  <span style="font-weight: 600">{{ fieldLabel(row.field) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="当前值">
                <template #default="{ row }">
                  <span style="color: #e6a23c">{{ displayValue(row.databaseValue) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="将恢复为">
                <template #default="{ row }">
                  <span style="color: #409eff">{{ displayValue(row.embeddedValue) }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-else style="text-align: center; padding: 24px 0; color: #909399">
            当前值与目标值一致，无需回滚
          </div>
        </template>
      </template>

      <!-- Batch preview -->
      <template v-if="mode === 'batch' && batchPreview">
        <el-row :gutter="16" style="margin-bottom: 20px">
          <el-col :span="8">
            <div class="rollback-stat-card">
              <div class="rollback-stat-num">{{ batchPreview.total }}</div>
              <div class="rollback-stat-label">总数</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="rollback-stat-card rollback-stat-success">
              <div class="rollback-stat-num">{{ batchPreview.canRollbackCount }}</div>
              <div class="rollback-stat-label">可回滚</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="rollback-stat-card rollback-stat-error">
              <div class="rollback-stat-num">{{ batchPreview.cannotRollbackCount }}</div>
              <div class="rollback-stat-label">不可回滚</div>
            </div>
          </el-col>
        </el-row>

        <el-table
          :data="batchPreview.items"
          border
          size="small"
          max-height="400"
          style="width: 100%"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div style="padding: 8px 16px">
                <template v-if="row.canRollback">
                  <el-alert
                    v-for="w in row.warnings"
                    :key="w"
                    :title="w"
                    type="warning"
                    show-icon
                    :closable="false"
                    style="margin-bottom: 8px"
                  />
                  <div v-if="row.diffs.length > 0">
                    <el-table :data="row.diffs" size="small" style="width: 100%">
                      <el-table-column label="字段" width="90" align="center">
                        <template #default="{ row: d }">
                          <span style="font-weight: 600">{{ fieldLabel(d.field) }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="当前值">
                        <template #default="{ row: d }">
                          <span style="color: #e6a23c">{{ displayValue(d.databaseValue) }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="将恢复为">
                        <template #default="{ row: d }">
                          <span style="color: #409eff">{{ displayValue(d.embeddedValue) }}</span>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                  <div v-else style="color: #67c23a; padding: 4px 0">当前值与目标值一致</div>
                </template>
                <el-alert
                  v-else
                  :title="row.errorMessage || '不可回滚'"
                  type="error"
                  show-icon
                  :closable="false"
                />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="审计 ID" width="80" align="center">
            <template #default="{ row }">{{ row.auditId }}</template>
          </el-table-column>
          <el-table-column label="音乐 ID" width="80" align="center">
            <template #default="{ row }">{{ row.musicId }}</template>
          </el-table-column>
          <el-table-column label="目标" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.rollbackTarget === 'embedded_tag' ? 'warning' : 'primary'">
                {{ rollbackTargetLabel(row.rollbackTarget) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="可回滚" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.canRollback ? 'success' : 'danger'" size="small">
                {{ row.canRollback ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- Batch result -->
      <template v-if="mode === 'batch' && batchResult">
        <el-result
          :icon="batchResult.failed === 0 ? 'success' : batchResult.success === 0 ? 'error' : 'warning'"
          :title="batchResult.failed === 0 ? '全部完成' : batchResult.success === 0 ? '全部失败' : '部分完成'"
          :sub-title="`成功 ${batchResult.success} 条，失败 ${batchResult.failed} 条`"
        >
          <template v-if="batchResult.failed > 0" #extra>
            <div style="text-align: left; max-height: 200px; overflow-y: auto">
              <div
                v-for="r in batchResult.items.filter(r => !r.success)"
                :key="r.auditId"
                style="padding: 6px 0; border-bottom: 1px solid #ebeef5"
              >
                <span style="font-size: 12px">审计 ID: {{ r.auditId }}</span>
                <span style="font-size: 12px; color: #f56c6c; margin-left: 8px">{{ r.errorMessage }}</span>
              </div>
            </div>
          </template>
        </el-result>
      </template>
    </div>

    <template #footer>
      <!-- Single: show rollback button -->
      <div v-if="mode === 'single' && singlePreview">
        <el-button
          v-if="singlePreview.canRollback && singlePreview.diffs.length > 0"
          :type="singlePreview.rollbackTarget === 'embedded_tag' ? 'danger' : 'warning'"
          :loading="executing"
          @click="handleSingleExecute"
        >
          确认回滚
        </el-button>
        <el-button @click="visible = false">取消</el-button>
      </div>

      <!-- Batch preview: show execute button -->
      <div v-if="mode === 'batch' && batchPreview && !batchResult" style="display: flex; justify-content: space-between">
        <div>
          <el-button
            type="warning"
            :loading="executing"
            :disabled="batchPreview.canRollbackCount === 0"
            @click="handleBatchExecute"
          >
            确认批量回滚 ({{ batchPreview.canRollbackCount }} 条)
          </el-button>
        </div>
        <el-button @click="visible = false">取消</el-button>
      </div>

      <!-- Batch result: close -->
      <div v-if="mode === 'batch' && batchResult">
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.rollback-stat-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 14px;
  text-align: center;
}
.rollback-stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.rollback-stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.rollback-stat-success .rollback-stat-num { color: #67c23a; }
.rollback-stat-error .rollback-stat-num { color: #f56c6c; }
</style>
