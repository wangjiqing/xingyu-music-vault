<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchScanJobs, createScanJob, runScanJob, type ScanJob } from '../api/scanJobs'

const jobs = ref<ScanJob[]>([])
const loading = ref(false)
const creating = ref(false)
const newJobName = ref('')
const newJobPath = ref('')

async function loadJobs() {
  loading.value = true
  try {
    jobs.value = await fetchScanJobs()
  } catch {
    ElMessage.error('加载扫描任务失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!newJobName.value.trim() || !newJobPath.value.trim()) {
    ElMessage.warning('请填写任务名称和路径')
    return
  }
  creating.value = true
  try {
    await createScanJob({ name: newJobName.value, path: newJobPath.value })
    ElMessage.success('扫描任务已创建')
    newJobName.value = ''
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
            <el-form-item label="任务名称">
              <el-input v-model="newJobName" placeholder="如：全量扫描" />
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
    <el-table :data="jobs" v-loading="loading" empty-text="暂无扫描任务">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="path" label="路径" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'RUNNING' ? 'warning' : row.status === 'COMPLETED' ? 'success' : 'info'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'RUNNING'"
            type="primary"
            size="small"
            text
            @click="handleRun(row.id)"
          >
            运行
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
