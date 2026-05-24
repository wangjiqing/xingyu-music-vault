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
  title: '歌曲名',
  artist: '歌手',
  album: '专辑',
}

const COMPARABLE_FIELDS = ['title', 'artist', 'album']

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
    const response = await compareMusicMetadata(props.musicId)
    result.value = {
      ...response,
      diffs: response.diffs?.filter((diff) => COMPARABLE_FIELDS.includes(diff.field)) || [],
    }
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
      '本次操作将使用音频文件内嵌 Tag 覆盖数据库中的歌曲名、歌手、专辑三个字段，请确认差异后再继续。\n这不会修改音频文件本身。\n是否继续？',
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
      '本次操作将使用数据库中的歌曲名、歌手、专辑三个字段写回音频文件内嵌 Tag，请确认差异后再继续。\n这会直接修改本地音频文件，请确认你已经备份重要文件。\n是否继续？',
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
        <div class="metadata-scope-note">
          当前版本仅比对歌曲名、歌手、专辑三个字段。年份、流派、音轨号、专辑歌手等字段暂不参与同步。
        </div>

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

<style scoped>
.metadata-scope-note {
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
</style>
