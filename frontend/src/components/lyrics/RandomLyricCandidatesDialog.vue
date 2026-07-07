<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchRandomLyricCandidates } from '../../api/lyricDashboard'
import type { MusicItem } from '../../api/music'
import { lyricStatusLabel, lyricStatusTagType } from '../../constants/musicStatus'

const router = useRouter()
const visible = ref(false)
const loading = ref(false)
const count = ref(5)
const items = ref<MusicItem[]>([])
const message = ref('')

function open() {
  visible.value = true
  items.value = []
  message.value = ''
}

async function generate() {
  if (count.value < 1 || count.value > 20) {
    ElMessage.warning('一次最多随机挑选 20 首')
    return
  }
  loading.value = true
  try {
    const res = await fetchRandomLyricCandidates(count.value)
    items.value = res.items
    message.value = res.message || ''
    if (items.value.length === 0) {
      ElMessage.info(message.value || '当前没有待制作歌曲')
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '随机挑选失败')
  } finally {
    loading.value = false
  }
}

function openWorkbench(row: MusicItem) {
  visible.value = false
  router.push({ path: '/music/workbench', query: { id: row.id } })
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="随机挑选待制作歌曲" width="720px">
    <div class="toolbar">
      <el-segmented
        v-model="count"
        :options="[
          { label: '5 首', value: 5 },
          { label: '10 首', value: 10 },
          { label: '20 首', value: 20 },
        ]"
      />
      <el-button type="primary" :loading="loading" @click="generate">生成候选</el-button>
    </div>

    <el-alert
      v-if="message && items.length === 0"
      :title="message"
      type="info"
      show-icon
      class="candidate-alert"
    />

    <el-table :data="items" v-loading="loading" max-height="420">
      <el-table-column label="歌曲" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.title || row.fileName }}</template>
      </el-table-column>
      <el-table-column label="歌手" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.artist || 'Unknown' }}</template>
      </el-table-column>
      <el-table-column label="歌词状态" width="110">
        <template #default="{ row }">
          <el-tag :type="lyricStatusTagType(row.lyricStatus)" size="small">
            {{ lyricStatusLabel(row.lyricStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="openWorkbench(row)">
            进入工作台
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.candidate-alert {
  margin-bottom: 12px;
}
</style>
