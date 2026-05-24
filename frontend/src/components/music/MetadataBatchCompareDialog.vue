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
  title: '标题',
  artist: '歌手',
  album: '专辑',
  albumArtist: '专辑歌手',
  year: '年份',
  genre: '流派',
  trackNumber: '音轨号',
  duration: '时长',
}

const visible = ref(false)
const loading = ref(false)
const applying = ref(false)
const compareList = ref<MetadataCompareResponse[]>([])
const applyResult = ref<BatchMetadataSyncResponse | null>(null)

const withDifference = computed(() => compareList.value.filter((r) => r.diffs.length > 0).length)
const withoutDifference = computed(() => compareList.value.filter((r) => r.diffs.length === 0).length)

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

async function open() {
  visible.value = true
  loading.value = true
  compareList.value = []
  applyResult.value = null
  try {
    compareList.value = await batchCompareMusicMetadata(props.musicIds)
  } catch (e: any) {
    const msg = e?.response?.data?.message || '批量元数据对比失败'
    ElMessage.error(msg)
    visible.value = false
  } finally {
    loading.value = false
  }
}

async function handleBatchApplyFileToDb() {
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
  try {
    await ElMessageBox.confirm(
      `将使用数据库中的元数据写回 ${props.musicIds.length} 首音频文件内嵌 Tag。\n这会直接修改本地音频文件，请确认你已经备份重要文件。\n是否继续？`,
      '确认：批量用数据库写回文件',
      { confirmButtonText: '确认写回', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await doBatchApply('db-to-file')
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
      ElMessage.error(`全部失败：${r.failed} 首`)
    } else {
      ElMessage.warning(`成功 ${r.success} 首，失败 ${r.failed} 首`)
    }
    emit('done')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '批量操作失败'
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
      <!-- Compare summary -->
      <template v-if="compareList.length > 0 && !applyResult">
        <el-row :gutter="16" style="margin-bottom: 20px">
          <el-col :span="6">
            <div class="batch-stat-card">
              <div class="batch-stat-num">{{ compareList.length }}</div>
              <div class="batch-stat-label">总数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="batch-stat-card batch-stat-warning">
              <div class="batch-stat-num">{{ withDifference }}</div>
              <div class="batch-stat-label">有差异</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="batch-stat-card batch-stat-success">
              <div class="batch-stat-num">{{ withoutDifference }}</div>
              <div class="batch-stat-label">无差异</div>
            </div>
          </el-col>
          <el-col :span="6">
            <!-- 当前批量 compare 在任一歌曲失败时直接关闭弹窗并全局报错，
                 因此此处统计始终为 0。若后续改为逐条容错（部分成功），
                 需要从 compareList 中动态计算失败条目数。 -->
            <div class="batch-stat-card">
              <div class="batch-stat-num">0</div>
              <div class="batch-stat-label">读取失败</div>
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
                <div v-if="row.diffs.length === 0" style="color: #67c23a; padding: 4px 0">
                  数据库与文件元数据一致
                </div>
                <el-table
                  v-if="row.diffs.length > 0"
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
              {{ row.database.title || '--' }}
              <span v-if="row.database.artist" style="color: #909399; font-size: 12px">
                — {{ row.database.artist }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.diffs.length > 0" type="warning" size="small">有差异</el-tag>
              <el-tag v-else type="success" size="small">一致</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- Apply result -->
      <template v-if="applyResult">
        <el-result
          :icon="applyResult.failed === 0 ? 'success' : applyResult.success === 0 ? 'error' : 'warning'"
          :title="applyResult.failed === 0 ? '全部完成' : applyResult.success === 0 ? '全部失败' : '部分完成'"
          :sub-title="`成功 ${applyResult.success} 首，失败 ${applyResult.failed} 首`"
        >
          <template v-if="applyResult.failed > 0" #extra>
            <div style="text-align: left; max-height: 200px; overflow-y: auto">
              <div
                v-for="r in applyResult.items.filter(r => r.status !== 'SUCCESS')"
                :key="r.musicId"
                style="padding: 6px 0; border-bottom: 1px solid #ebeef5"
              >
                <span style="font-size: 12px">ID: {{ r.musicId }}</span>
                <span style="font-size: 12px; color: #f56c6c; margin-left: 8px">{{ r.errorMessage }}</span>
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
            :disabled="compareList.length === 0"
            @click="handleBatchApplyFileToDb"
          >
            批量用文件覆盖数据库
          </el-button>
          <el-button
            type="warning"
            :loading="applying"
            :disabled="compareList.length === 0"
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
</style>
