<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMetadataAuditDetail, type MetadataAuditDetailResponse, type MetadataSnapshot } from '../../api/music'

const props = defineProps<{
  auditId: number
}>()

const visible = ref(false)
const loading = ref(false)
const detail = ref<MetadataAuditDetailResponse | null>(null)

const FIELD_LABELS: Record<string, string> = {
  title: '标题',
  artist: '歌手',
  album: '专辑',
}

const DIRECTION_LABELS: Record<string, string> = {
  file_to_db: '文件→数据库',
  db_to_file: '数据库→文件',
}

function fieldLabel(field: string): string {
  return FIELD_LABELS[field] || field
}

function displayValue(val: unknown): string {
  if (val == null || val === '') return '--'
  return String(val)
}

function snapshotToRows(snapshot: MetadataSnapshot | null): { field: string; value: string }[] {
  if (!snapshot) return []
  return Object.keys(FIELD_LABELS).map((k) => ({
    field: fieldLabel(k),
    value: displayValue((snapshot as any)[k]),
  }))
}

async function open() {
  visible.value = true
  loading.value = true
  detail.value = null
  try {
    detail.value = await fetchMetadataAuditDetail(props.auditId)
  } catch {
    ElMessage.error('加载审计详情失败')
    visible.value = false
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="审计详情"
    width="900px"
    destroy-on-close
  >
    <div v-loading="loading" style="min-height: 120px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="操作时间">
            {{ detail.createdAt?.replace('T', ' ').substring(0, 19) || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="歌曲">
            {{ detail.musicTitle || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="方向">
            {{ DIRECTION_LABELS[detail.direction] || detail.direction }}
          </el-descriptions-item>
          <el-descriptions-item label="操作类型">
            <el-tag size="small" :type="detail.operationType === 'ROLLBACK' ? 'warning' : 'primary'">
              {{ detail.operationType === 'ROLLBACK' ? '回滚' : '同步' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="detail.status === 'SUCCESS' ? 'success' : 'danger'">
              {{ detail.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="回滚状态">
            <el-tag size="small" :type="detail.rollbackStatus === 'ROLLED_BACK' ? 'success' : 'info'">
              {{ detail.rollbackStatus === 'ROLLED_BACK' ? '已回滚' : '未回滚' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="批次" :span="2">
            {{ detail.batchId || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="文件路径" :span="2">
            {{ detail.filePath || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="变更字段" :span="2">
            <template v-if="detail.changedFields && detail.changedFields.length > 0">
              <el-tag
                v-for="f in detail.changedFields"
                :key="f"
                size="small"
                type="warning"
                style="margin-right: 4px"
              >
                {{ fieldLabel(f) }}
              </el-tag>
            </template>
            <span v-else style="color: #c0c4cc">--</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.errorMessage" label="错误信息" :span="2">
            <span style="color: #f56c6c">{{ detail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.direction === 'file_to_db'">
          <h4 style="margin: 16px 0 8px; font-size: 14px; color: #303133">数据库（文件覆盖前 / 覆盖后）</h4>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
            <el-table :data="snapshotToRows(detail.beforeDatabase)" size="small" border>
              <el-table-column label="字段" width="80" align="center">
                <template #default="{ row }">{{ row.field }}</template>
              </el-table-column>
              <el-table-column label="覆盖前" show-overflow-tooltip>
                <template #default="{ row }">{{ row.value }}</template>
              </el-table-column>
            </el-table>
            <el-table :data="snapshotToRows(detail.afterDatabase)" size="small" border>
              <el-table-column label="字段" width="80" align="center">
                <template #default="{ row }">{{ row.field }}</template>
              </el-table-column>
              <el-table-column label="覆盖后" show-overflow-tooltip>
                <template #default="{ row }">{{ row.value }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>

        <template v-if="detail.direction === 'db_to_file'">
          <h4 style="margin: 16px 0 8px; font-size: 14px; color: #303133">文件 Tag（写回前 / 写回后）</h4>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
            <el-table :data="snapshotToRows(detail.beforeFile)" size="small" border>
              <el-table-column label="字段" width="80" align="center">
                <template #default="{ row }">{{ row.field }}</template>
              </el-table-column>
              <el-table-column label="写回前" show-overflow-tooltip>
                <template #default="{ row }">{{ row.value }}</template>
              </el-table-column>
            </el-table>
            <el-table :data="snapshotToRows(detail.afterFile)" size="small" border>
              <el-table-column label="字段" width="80" align="center">
                <template #default="{ row }">{{ row.field }}</template>
              </el-table-column>
              <el-table-column label="写回后" show-overflow-tooltip>
                <template #default="{ row }">{{ row.value }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>
