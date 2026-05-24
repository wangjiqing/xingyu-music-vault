<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  compareMusicMetadata,
  applyFileMetadataToDatabase,
  applyDatabaseMetadataToFile,
  type MetadataCompareResponse,
} from '../../api/music'

const props = defineProps<{
  musicId: number
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
const result = ref<MetadataCompareResponse | null>(null)

const hasDifference = computed(() => (result.value?.diffs?.length ?? 0) > 0)

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
  result.value = null
  try {
    result.value = await compareMusicMetadata(props.musicId)
  } catch (e: any) {
    const msg = e?.response?.data?.message || '加载元数据对比失败'
    ElMessage.error(msg)
    visible.value = false
  } finally {
    loading.value = false
  }
}

async function handleApplyFileToDb() {
  try {
    await ElMessageBox.confirm(
      '将使用音频文件内嵌 Tag 覆盖数据库中的歌曲元数据。\n这会修改系统中展示的标题、歌手、专辑、年份等信息，但不会修改音频文件本身。\n是否继续？',
      '确认：用文件覆盖数据库',
      { confirmButtonText: '确认覆盖', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  applying.value = true
  try {
    const res = await applyFileMetadataToDatabase(props.musicId)
    if (res.status === 'SUCCESS') {
      ElMessage.success('元数据已用文件 Tag 覆盖数据库')
    } else {
      ElMessage.error(res.errorMessage || '覆盖失败')
    }
    visible.value = false
    emit('done')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '覆盖失败'
    ElMessage.error(msg)
  } finally {
    applying.value = false
  }
}

async function handleApplyDbToFile() {
  try {
    await ElMessageBox.confirm(
      '将使用数据库中的歌曲元数据写回音频文件内嵌 Tag。\n这会直接修改本地音频文件，请确认你已经备份重要文件。\n是否继续？',
      '确认：用数据库写回文件',
      { confirmButtonText: '确认写回', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  applying.value = true
  try {
    const res = await applyDatabaseMetadataToFile(props.musicId)
    if (res.status === 'SUCCESS') {
      ElMessage.success('元数据已写回音频文件')
    } else {
      ElMessage.error(res.errorMessage || '写回失败')
    }
    visible.value = false
    emit('done')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '写回失败'
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
    title="元数据同步"
    width="680px"
    destroy-on-close
    @closed="emit('close')"
  >
    <div v-loading="loading" style="min-height: 120px">
      <template v-if="result">
        <div v-if="!hasDifference" style="text-align: center; padding: 24px 0; color: #909399">
          <el-icon :size="40" style="color: #67c23a; margin-bottom: 12px">
            <svg viewBox="0 0 1024 1024" width="1em" height="1em" fill="currentColor"><path d="M512 64a448 448 0 1 1 0 896 448 448 0 0 1 0-896z m-55.808 536.128l-99.52-99.584a38.4 38.4 0 1 0-54.336 54.336l126.72 126.72a38.4 38.4 0 0 0 54.336 0l262.144-262.144a38.4 38.4 0 1 0-54.336-54.336L456.192 600.128z" /></svg>
          </el-icon>
          <div style="font-size: 15px">数据库与文件元数据一致</div>
        </div>

        <el-table
          v-if="hasDifference"
          :data="result.diffs"
          border
          size="small"
          style="width: 100%"
        >
          <el-table-column label="字段" width="100" align="center">
            <template #default="{ row }">
              <span style="font-weight: 600">
                {{ fieldLabel(row.field) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="数据库值" min-width="200">
            <template #default="{ row }">
              {{ displayValue(row.databaseValue) }}
            </template>
          </el-table-column>
          <el-table-column label="文件内嵌 Tag" min-width="200">
            <template #default="{ row }">
              {{ displayValue(row.embeddedValue) }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <template #footer>
      <div style="display: flex; justify-content: space-between">
        <div>
          <el-button
            type="primary"
            :loading="applying"
            :disabled="!result"
            @click="handleApplyFileToDb"
          >
            用文件覆盖数据库
          </el-button>
          <el-button
            type="warning"
            :loading="applying"
            :disabled="!result"
            @click="handleApplyDbToFile"
          >
            用数据库写回文件
          </el-button>
        </div>
        <el-button @click="visible = false">取消</el-button>
      </div>
    </template>
  </el-dialog>
</template>
