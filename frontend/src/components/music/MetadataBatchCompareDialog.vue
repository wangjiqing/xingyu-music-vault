<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchCompareMusicMetadata,
  batchApplyFileMetadataToDatabase,
  batchApplyDatabaseMetadataToFile,
  type MetadataCompareResponse,
  type BatchMetadataSyncResponse,
} from '../../api/music'

const props = defineProps<{
  musicIds: number[]
}>()

const emit = defineEmits<{
  done: []
  close: []
}>()

const FIELD_LABELS: Record<string, string> = {
  title: '歌曲名',
  artist: '歌手',
  album: '专辑',
}

const COMPARABLE_FIELDS = ['title', 'artist', 'album']
const MAX_BATCH_SIZE = 100

const visible = ref(false)
const loading = ref(false)
const applying = ref(false)
const compareList = ref<MetadataCompareResponse[]>([])
const applyResult = ref<BatchMetadataSyncResponse | null>(null)
const previewFailed = ref(false)


const successfulCompareList = computed(() => compareList.value.filter((r) => r.status !== 'FAILED'))
const failedCompareCount = computed(() => compareList.value.filter((r) => r.status === 'FAILED').length)
const withDifference = computed(() => successfulCompareList.value.filter((r) => r.diffs.length > 0).length)
const withoutDifference = computed(() => successfulCompareList.value.filter((r) => r.diffs.length === 0).length)
const allNoDifference = computed(() => successfulCompareList.value.length > 0 && withDifference.value === 0)
const previewCompleted = computed(() => !loading.value && compareList.value.length > 0 && !previewFailed.value)

const fieldDiffStats = computed(() => {
  const stats: Record<string, number> = {}
  for (const item of compareList.value) {
    for (const d of item.diffs) {
      stats[d.field] = (stats[d.field] || 0) + 1
    }
  }
  return Object.entries(stats)
    .filter(([, count]) => count > 0)
    .sort(([, a], [, b]) => b - a)
})

function fieldLabel(field: string): string {
  return FIELD_LABELS[field] || field
}

function displayValue(val: unknown): string {
  if (val == null || val === '') return '--'
  return String(val)
}

function validateBatchSelection(): boolean {
  if (!props.musicIds || props.musicIds.length === 0) {
    ElMessage.warning('请先选择要同步的歌曲')
    return false
  }
  if (props.musicIds.length > MAX_BATCH_SIZE) {
    ElMessage.warning(`一次最多处理 ${MAX_BATCH_SIZE} 首，当前已选择 ${props.musicIds.length} 首`)
    return false
  }
  return true
}

async function open() {
  if (!validateBatchSelection()) return
  visible.value = true
  loading.value = true
  compareList.value = []
  applyResult.value = null
  previewFailed.value = false
  try {
    const raw = await batchCompareMusicMetadata(props.musicIds)
    compareList.value = raw.map((item) => ({
      ...item,
      diffs: item.diffs?.filter((diff) => COMPARABLE_FIELDS.includes(diff.field)) || [],
    }))
  } catch (e: any) {
    const msg = e?.response?.data?.message || '批量元数据对比失败，请确认所选歌曲文件仍存在且后端服务正常'
    ElMessage.error(msg)
    previewFailed.value = true
  } finally {
    loading.value = false
  }
}

async function handleBatchApplyFileToDb() {
  if (!previewCompleted.value) return
  try {
    await ElMessageBox.confirm(
      `将使用音频文件内嵌 Tag 覆盖 ${props.musicIds.length} 首歌曲的数据库元数据。\n这会修改系统中展示的标题、歌手、专辑、年份等信息，但不会修改音频文件本身。\n是否继续？`,
      '确认：批量用文件覆盖数据库',
      { confirmButtonText: '确认覆盖', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await doBatchApply('file-to-db')
}

async function handleBatchApplyDbToFile() {
  if (!previewCompleted.value) return
  try {
    await ElMessageBox.confirm(
      `将使用数据库中的歌曲元数据写回 ${props.musicIds.length} 首音频文件内嵌 Tag。\n这会直接修改本地音频文件，请确认你已经备份重要文件。\n是否继续？`,
      '确认：批量用数据库写回文件',
      { confirmButtonText: '确认写回', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await doBatchApply('db-to-file')
}

function getFailHints(dir: 'file-to-db' | 'db-to-file'): string {
  return dir === 'file-to-db'
    ? '请确认音频文件仍存在且当前程序有读写权限'
    : '请确认音频文件存在且当前程序有写入权限，建议提前备份重要文件'
}

async function doBatchApply(direction: 'file-to-db' | 'db-to-file') {
  applying.value = true
  try {
    if (direction === 'file-to-db') {
      applyResult.value = await batchApplyFileMetadataToDatabase(props.musicIds)
    } else {
      applyResult.value = await batchApplyDatabaseMetadataToFile(props.musicIds)
    }
    const r = applyResult.value
    if (r.failed === 0) {
      ElMessage.success(`全部完成：成功 ${r.success} 首`)
    } else if (r.success === 0) {
      ElMessage.error(`全部失败：${r.failed} 首。${getFailHints(direction)}`)
    } else {
      ElMessage.warning(`成功 ${r.success} 首，失败 ${r.failed} 首。${getFailHints(direction)}`)
    }
    emit('done')
  } catch (e: any) {
    const msg = e?.response?.data?.message || `批量操作失败。${getFailHints(direction)}`
    ElMessage.error(msg)
  } finally {
    applying.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="批量元数据同步"
    width="900px"
    destroy-on-close
    @closed="emit('close')"
  >
    <div v-loading="loading" style="min-height: 120px">
      <template v-if="compareList.length > 0 && !applyResult">
        <div class="metadata-scope-note">
          当前版本仅比对歌曲名、歌手、专辑三个字段。年份、流派、音轨号、专辑歌手等字段暂不参与同步。
        </div>

        <div v-if="allNoDifference" style="text-align: center; padding: 16px 0">
          <el-alert
            title="所有选中歌曲的数据库与文件元数据均一致，无可变更字段，不建议继续覆盖操作。"
            type="warning"
            show-icon
            :closable="false"
          />
        </div>

        <el-row :gutter="16" style="margin-bottom: 20px">
          <el-col :span="8">
            <div class="batch-stat-card">
              <div class="batch-stat-num">{{ compareList.length }}</div>
              <div class="batch-stat-label">总数</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="batch-stat-card batch-stat-warning">
              <div class="batch-stat-num">{{ withDifference }}</div>
              <div class="batch-stat-label">有差异</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="batch-stat-card batch-stat-success">
              <div class="batch-stat-num">{{ withoutDifference }}</div>
              <div class="batch-stat-label">无差异</div>
            </div>
          </el-col>

        </el-row>

        <div v-if="fieldDiffStats.length > 0" style="margin-bottom: 16px">
          <span style="font-size: 13px; color: #606266; margin-right: 8px">字段差异统计：</span>
          <el-tag
            v-for="[key, count] in fieldDiffStats"
            :key="key"
            size="small"
            type="warning"
            style="margin-right: 6px"
          >
            {{ fieldLabel(key) }}: {{ count }}
          </el-tag>
        </div>

        <el-alert
          v-if="failedCompareCount > 0"
          :title="`有 ${failedCompareCount} 首歌曲对比失败，失败原因已在列表中展示。`"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        />

        <el-table
          :data="compareList"
          border
          size="small"
          max-height="400"
          style="width: 100%"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div style="padding: 8px 16px">
                <div v-if="row.status !== 'FAILED' && row.diffs.length === 0" style="color: #67c23a; padding: 4px 0">
                  数据库与文件元数据一致
                </div>
                <div v-if="row.status === 'FAILED'" style="color: #f56c6c; padding: 4px 0">
                  {{ row.errorMessage || '对比失败' }}
                </div>
                <el-table
                  v-if="row.status !== 'FAILED' && row.diffs.length > 0"
                  :data="row.diffs"
                  size="small"
                  style="width: 100%"
                >
                  <el-table-column label="字段" width="90" align="center">
                    <template #default="{ row: d }">
                      <span style="font-weight: 600">
                        {{ fieldLabel(d.field) }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="数据库值" min-width="160">
                    <template #default="{ row: d }">
                      {{ displayValue(d.databaseValue) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="文件内嵌 Tag" min-width="160">
                    <template #default="{ row: d }">
                      {{ displayValue(d.embeddedValue) }}
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="歌曲" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.database?.title || `ID: ${row.musicId}` }}
              <span v-if="row.database?.artist" style="color: #909399; font-size: 12px">
                — {{ row.database.artist }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'FAILED'" type="danger" size="small">失败</el-tag>
              <el-tag v-else-if="row.diffs.length > 0" type="warning" size="small">有差异</el-tag>
              <el-tag v-else type="success" size="small">一致</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template v-if="previewFailed">
        <el-empty description="无法加载批量对比预览，请确认所选歌曲存在且后端服务正常" />
      </template>

      <template v-if="applyResult">
        <el-result
          :icon="applyResult.failed === 0 ? 'success' : applyResult.success === 0 ? 'error' : 'warning'"
          :title="applyResult.failed === 0 ? '全部完成' : applyResult.success === 0 ? '全部失败' : '部分完成'"
          :sub-title="`成功 ${applyResult.success} 首，失败 ${applyResult.failed} 首`"
        >
          <template v-if="applyResult.failed > 0" #extra>
            <div style="text-align: left; max-height: 200px; overflow-y: auto">
              <div v-for="r in applyResult.items.filter(r => r.status !== 'SUCCESS')" :key="r.musicId" style="padding: 8px 0; border-bottom: 1px solid #ebeef5">
                <div style="font-size: 13px"><strong>ID: {{ r.musicId }}</strong></div>
                <div style="font-size: 12px; color: #f56c6c; margin-top: 2px">错误：{{ r.errorMessage || '未知错误' }}</div>
                <div v-if="r.direction" style="font-size: 12px; color: #909399">操作方向：{{ r.direction === 'file_to_db' ? '文件→数据库' : '数据库→文件' }}</div>
              </div>
            </div>
          </template>
        </el-result>
      </template>
    </div>

    <template #footer>
      <div v-if="!applyResult" style="display: flex; justify-content: space-between">
        <div>
          <el-button
            type="primary"
            :loading="applying"
            :disabled="!previewCompleted || allNoDifference"
            @click="handleBatchApplyFileToDb"
          >
            批量用文件覆盖数据库
          </el-button>
          <el-button
            type="warning"
            :loading="applying"
            :disabled="!previewCompleted || allNoDifference"
            @click="handleBatchApplyDbToFile"
          >
            批量用数据库写回文件
          </el-button>
        </div>
        <el-button @click="visible = false">取消</el-button>
      </div>
      <div v-else>
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.batch-stat-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 14px;
  text-align: center;
}
.batch-stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.batch-stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.batch-stat-warning .batch-stat-num { color: #e6a23c; }
.batch-stat-success .batch-stat-num { color: #67c23a; }
.metadata-scope-note {
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
</style>
