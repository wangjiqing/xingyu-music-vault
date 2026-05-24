<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, View, Clock } from '@element-plus/icons-vue'
import {
  fetchMetadataAudits,
  type MetadataAuditListItem,
} from '../api/music'
import MetadataAuditDetailDialog from '../components/music/MetadataAuditDetailDialog.vue'
import MetadataRollbackPreviewDialog from '../components/music/MetadataRollbackPreviewDialog.vue'

const list = ref<MetadataAuditListItem[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 20,
  keyword: '',
  direction: '',
  status: '',
  rollbackStatus: '',
  startTime: '',
  endTime: '',
})

const selectedRows = ref<MetadataAuditListItem[]>([])

const detailDialogRef = ref<InstanceType<typeof MetadataAuditDetailDialog>>()
const detailAuditId = ref(0)

const rollbackPreviewRef = ref<InstanceType<typeof MetadataRollbackPreviewDialog>>()
const MAX_BATCH_SIZE = 20

const DIRECTION_LABELS: Record<string, string> = {
  file_to_db: '文件→数据库',
  db_to_file: '数据库→文件',
}

const STATUS_TYPES: Record<string, string> = {
  SUCCESS: 'success',
  FAILED: 'danger',
}

const STATUS_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
}

const ROLLBACK_LABELS: Record<string, string> = {
  NOT_ROLLED_BACK: '未回滚',
  ROLLED_BACK: '已回滚',
}

const ROLLBACK_TYPES: Record<string, string> = {
  NOT_ROLLED_BACK: 'info',
  ROLLED_BACK: 'success',
}

function canRollbackItem(row: MetadataAuditListItem): boolean {
  return row.status === 'SUCCESS' && row.operationType !== 'ROLLBACK' && row.rollbackStatus === 'NOT_ROLLED_BACK'
}

async function loadList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: query.page - 1,
      pageSize: query.pageSize,
    }
    if (query.keyword) params.keyword = query.keyword
    if (query.direction) params.direction = query.direction
    if (query.status) params.status = query.status
    if (query.rollbackStatus) params.rollbackStatus = query.rollbackStatus
    if (query.startTime) params.startTime = query.startTime
    if (query.endTime) params.endTime = query.endTime
    const res = await fetchMetadataAudits(params as any)
    list.value = res.items
    total.value = res.total
  } catch {
    ElMessage.error('加载审计列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
}

function handleSelectionChange(rows: MetadataAuditListItem[]) {
  selectedRows.value = rows
}

function openDetail(row: MetadataAuditListItem) {
  detailAuditId.value = row.id
  detailDialogRef.value?.open()
}

async function handleSingleRollback(row: MetadataAuditListItem) {
  rollbackPreviewRef.value?.openSingle(row.id)
}

async function onRollbackDone() {
  await loadList()
}

function handleBatchRollback() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要回滚的审计记录')
    return
  }
  if (selectedRows.value.length > MAX_BATCH_SIZE) {
    ElMessage.warning(`一次最多处理 ${MAX_BATCH_SIZE} 条，当前已选择 ${selectedRows.value.length} 条`)
    return
  }
  const nonRollbackable = selectedRows.value.filter((r) => !canRollbackItem(r))
  if (nonRollbackable.length > 0) {
    ElMessage.warning(`${nonRollbackable.length} 条记录不可回滚，请重新选择`)
    return
  }
  const ids = selectedRows.value.map((r) => r.id)
  rollbackPreviewRef.value?.openBatch(ids)
}

function formatTime(time: string): string {
  if (!time) return '--'
  return time.replace('T', ' ').substring(0, 19)
}

function formatChangedFields(fields: string[]): string {
  if (!fields || fields.length === 0) return '--'
  return fields.join('、')
}

function emptyText(): string {
  return query.keyword || query.direction || query.status || query.rollbackStatus
    ? '没有匹配的结果'
    : '暂无审计记录'
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>元数据同步审计</span>
        <div class="header-actions">
          <el-button size="small" @click="loadList" :icon="RefreshRight">刷新</el-button>
          <el-button
            v-if="selectedRows.length > 0"
            type="primary"
            size="small"
            @click="handleBatchRollback"
          >
            批量回滚 ({{ selectedRows.length }})
          </el-button>
        </div>
      </div>
    </template>

    <div class="search-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索歌曲标题、文件路径、批次..."
        clearable
        size="small"
        style="width: 280px"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="query.direction"
        placeholder="方向"
        clearable
        size="small"
        style="width: 140px"
        @change="handleSearch"
      >
        <el-option label="全部" value="" />
        <el-option label="文件→数据库" value="file_to_db" />
        <el-option label="数据库→文件" value="db_to_file" />
      </el-select>
      <el-select
        v-model="query.status"
        placeholder="状态"
        clearable
        size="small"
        style="width: 100px"
        @change="handleSearch"
      >
        <el-option label="全部" value="" />
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
      </el-select>
      <el-select
        v-model="query.rollbackStatus"
        placeholder="回滚状态"
        clearable
        size="small"
        style="width: 120px"
        @change="handleSearch"
      >
        <el-option label="全部" value="" />
        <el-option label="未回滚" value="NOT_ROLLED_BACK" />
        <el-option label="已回滚" value="ROLLED_BACK" />
      </el-select>
      <el-date-picker
        v-model="query.startTime"
        type="datetime"
        placeholder="开始时间"
        size="small"
        style="width: 180px"
        value-format="YYYY-MM-DDTHH:mm:ss"
        @change="handleSearch"
      />
      <el-date-picker
        v-model="query.endTime"
        type="datetime"
        placeholder="结束时间"
        size="small"
        style="width: 180px"
        value-format="YYYY-MM-DDTHH:mm:ss"
        @change="handleSearch"
      />
    </div>

    <el-table
      :data="list"
      v-loading="loading"
      @selection-change="handleSelectionChange"
      :empty-text="emptyText()"
      style="width: 100%"
    >
      <el-table-column
        type="selection"
        width="40"
        :selectable="canRollbackItem"
      />
      <el-table-column label="操作时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="歌曲" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.musicTitle || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="方向" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">
            {{ DIRECTION_LABELS[row.direction] || row.direction }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="变更字段" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ formatChangedFields(row.changedFields) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="STATUS_TYPES[row.status] || 'info'" size="small">
            {{ STATUS_LABELS[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="回滚" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="ROLLBACK_TYPES[row.rollbackStatus] || 'info'" size="small">
            {{ ROLLBACK_LABELS[row.rollbackStatus] || row.rollbackStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="批次" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.batchId">
            {{ row.batchId.substring(0, 8) }}...
          </template>
          <span v-else style="color: #c0c4cc">--</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            text
            :icon="View"
            @click="openDetail(row)"
          >
            详情
          </el-button>
          <el-button
            v-if="canRollbackItem(row)"
            type="warning"
            size="small"
            text
            :icon="Clock"
            @click="handleSingleRollback(row)"
          >
            回滚
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </el-card>

  <MetadataAuditDetailDialog
    ref="detailDialogRef"
    :audit-id="detailAuditId"
  />

  <MetadataRollbackPreviewDialog
    ref="rollbackPreviewRef"
    @done="onRollbackDone"
  />
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
.search-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
