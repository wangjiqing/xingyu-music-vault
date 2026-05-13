<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchScanJobs,
  createScanJob,
  runScanJob,
  type ScanJob,
  type ScanJobQuery,
} from '../api/scanJobs'

const jobs = ref<ScanJob[]>([])
const loading = ref(false)
const creating = ref(false)
const newJobType = ref('')
const newJobPath = ref('')

const query = reactive<ScanJobQuery>({
  page: 1,
  size: 10,
  status: '',
})
const total = ref(0)

const statusLabel = (status: string) => {
  const map: Record<string, string> = {
    pending: '待执行',
    running: '运行中',
    completed: '已完成',
    failed: '失败',
  }
  return map[status] || status
}

const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    failed: 'danger',
  }
  return map[status] || 'info'
}

const canRun = (row: ScanJob) => row.status === 'pending' || row.status === 'failed'

async function loadJobs() {
  loading.value = true
  try {
    const res = await fetchScanJobs({ ...query, page: query.page - 1 })
    jobs.value = res.items
    total.value = res.total
  } catch {
    ElMessage.error('加载扫描任务失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!newJobType.value.trim() || !newJobPath.value.trim()) {
    ElMessage.warning('请填写任务类型和扫描路径')
    return
  }
  creating.value = true
  try {
    await createScanJob({
      jobType: newJobType.value,
      musicDirs: [newJobPath.value],
    })
    ElMessage.success('扫描任务已创建')
    newJobType.value = ''
    newJobPath.value = ''
    await loadJobs()
  } catch {
    ElMessage.error('创建任务失败')
  } finally {
    creating.value = false
  }
}

async function handleRun(id: number) {
  try {
    await runScanJob(id)
    ElMessage.success('任务已启动')
    await loadJobs()
  } catch {
    ElMessage.error('启动任务失败')
  }
}

function handleStatusFilter(status: string) {
  query.status = status
  query.page = 1
  loadJobs()
}

function handlePageChange(page: number) {
  query.page = page
  loadJobs()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadJobs()
}

onMounted(() => {
  loadJobs()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>扫描任务</span>
        <el-popover trigger="click" placement="bottom" :width="360">
          <template #reference>
            <el-button type="primary" size="small">创建任务</el-button>
          </template>
          <el-form label-width="80px">
            <el-form-item label="任务类型">
              <el-input v-model="newJobType" placeholder="如：library_scan" />
            </el-form-item>
            <el-form-item label="扫描路径">
              <el-input v-model="newJobPath" placeholder="如：/music/library" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" :loading="creating" @click="handleCreate">
                确认创建
              </el-button>
            </el-form-item>
          </el-form>
        </el-popover>
      </div>
    </template>

    <div class="filter-bar">
      <el-radio-group v-model="query.status" size="small" @change="handleStatusFilter">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="pending">待执行</el-radio-button>
        <el-radio-button value="running">运行中</el-radio-button>
        <el-radio-button value="completed">已完成</el-radio-button>
        <el-radio-button value="failed">失败</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="jobs" v-loading="loading" empty-text="暂无扫描任务" style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="jobType" label="任务类型" min-width="140" />
      <el-table-column label="扫描路径" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.musicDirs?.join(', ') || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalFiles" label="总数" width="70" align="center" />
      <el-table-column prop="scannedFiles" label="已扫描" width="70" align="center" />
      <el-table-column prop="newFiles" label="新增" width="70" align="center" />
      <el-table-column prop="updatedFiles" label="更新" width="70" align="center" />
      <el-table-column prop="skippedFiles" label="跳过" width="70" align="center" />
      <el-table-column prop="errorFiles" label="错误" width="70" align="center" />
      <el-table-column label="错误信息" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.errorMessage" style="color: #f56c6c; font-size: 12px">
            {{ row.errorMessage }}
          </span>
          <span v-else style="color: #c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            text
            :disabled="!canRun(row)"
            @click="handleRun(row.id)"
          >
            运行
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-bar {
  margin-bottom: 4px;
}
</style>
