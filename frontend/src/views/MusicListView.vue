<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View, PictureFilled, UploadFilled, Edit, Delete, DeleteFilled, RefreshRight } from '@element-plus/icons-vue'
import {
  fetchMusicList,
  triggerMusicScan,
  updateMusicMetadata,
  deleteMusic,
  fetchTrashList,
  restoreMusic,
  permanentlyDeleteMusic,
  type MusicItem,
  type MusicMetadataUpdate,
  type MusicTrashItem,
} from '../api/music'
import { fetchSongLyric, triggerLyricScan, type SongLyric } from '../api/lyrics'
import {
  fetchArtworkList,
  bindArtworkToMusic,
  unbindArtworkFromMusic,
  importArtworkFile,
  type ArtworkItem,
} from '../api/artwork'

const brokenImages = reactive(new Set<number>())

const list = ref<MusicItem[]>([])
const loading = ref(false)
const scanning = ref(false)
const scanningLyrics = ref(false)
const errorMessage = ref('')

const query = reactive({
  page: 1,
  size: 20,
})
const total = ref(0)

const lyricDialogVisible = ref(false)
const lyricLoading = ref(false)
const currentLyric = ref<SongLyric | null>(null)
const currentSongTitle = ref('')
const currentSongArtist = ref('')

const bindDialogVisible = ref(false)
const bindDialogLoading = ref(false)
const bindDialogKeyword = ref('')
const bindDialogPage = ref(1)
const bindDialogTotal = ref(0)
const bindArtworkList = ref<ArtworkItem[]>([])
const bindTargetMusic = ref<MusicItem | null>(null)
const bindSubmitting = ref(false)

const brokenBindThumbs = reactive(new Set<number>())

const bindActiveTab = ref('select')
const uploadFile = ref<File | null>(null)
const uploadFileName = ref('')
const uploading = ref(false)

const editDialogVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive<MusicMetadataUpdate & { id: number }>({
  id: 0,
  title: '',
  artist: '',
  album: '',
  year: null,
  trackNo: null,
  genre: '',
})

const deleteDialogVisible = ref(false)
const deleteLoading = ref(false)
const deleteTarget = ref<MusicItem | null>(null)

const trashVisible = ref(false)
const trashLoading = ref(false)
const trashList = ref<MusicTrashItem[]>([])
const trashOperationLoading = ref(false)

const restoreConfirmVisible = ref(false)
const restoreTarget = ref<MusicTrashItem | null>(null)

const permDeleteConfirmVisible = ref(false)
const permDeleteTarget = ref<MusicTrashItem | null>(null)

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_FILE_SIZE = 10 * 1024 * 1024

const lyricStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    BOUND: '有歌词',
    NO_LYRIC: '无歌词',
    UNMATCHED: '待匹配',
    PARSE_FAILED: '解析失败',
    MISSING_FILE: '文件缺失',
  }
  return map[status] || status
}

const lyricStatusTagType = (status: string) => {
  const map: Record<string, string> = {
    BOUND: 'success',
    NO_LYRIC: 'info',
    UNMATCHED: 'warning',
    PARSE_FAILED: 'danger',
    MISSING_FILE: 'danger',
  }
  return map[status] || 'info'
}

const sourceTypeLabel = (sourceType: string) => {
  const map: Record<string, string> = {
    LOCAL_FILE: '本地文件',
  }
  return map[sourceType] || sourceType
}

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return `${size.toFixed(1)} ${units[i]}`
}

function formatDuration(duration: number | null): string {
  if (duration == null) return '--'
  let seconds = duration
  if (duration > 36000) {
    seconds = Math.round(duration / 1000)
  }
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await fetchMusicList({ page: query.page - 1, size: query.size })
    list.value = res.items
    total.value = res.total
  } catch {
    errorMessage.value = '加载音乐列表失败，请检查后端服务是否运行'
    ElMessage.error('加载音乐列表失败')
  } finally {
    loading.value = false
  }
}

async function handleScan() {
  scanning.value = true
  try {
    const res = await triggerMusicScan()
    ElMessage.success(`扫描任务已提交（任务ID: ${res.scanJobId}），请稍后刷新列表`)
  } catch {
    ElMessage.error('提交扫描任务失败')
  } finally {
    scanning.value = false
  }
}

async function handleLyricScan() {
  scanningLyrics.value = true
  try {
    const res = await triggerLyricScan()
    const parts: string[] = []
    if (res.matched > 0) parts.push(`匹配 ${res.matched} 首`)
    if (res.unmatched > 0) parts.push(`未匹配 ${res.unmatched} 首`)
    if (res.failed > 0) parts.push(`失败 ${res.failed} 首`)
    const detail = parts.length > 0 ? `（${parts.join('，')}）` : ''
    ElMessage.success(`歌词扫描完成${detail}，请刷新列表查看`)
  } catch {
    ElMessage.error('歌词扫描失败')
  } finally {
    scanningLyrics.value = false
  }
}

async function handleViewLyric(row: MusicItem) {
  if (!row.lyricId) return
  lyricLoading.value = true
  currentSongTitle.value = row.title
  currentSongArtist.value = row.artist
  lyricDialogVisible.value = true
  currentLyric.value = null
  try {
    currentLyric.value = await fetchSongLyric(row.id)
  } catch {
    ElMessage.error('加载歌词失败')
    lyricDialogVisible.value = false
  } finally {
    lyricLoading.value = false
  }
}

async function handleBindOpen(row: MusicItem) {
  bindTargetMusic.value = row
  bindDialogKeyword.value = ''
  bindDialogPage.value = 1
  bindActiveTab.value = 'select'
  resetUpload()
  bindDialogVisible.value = true
  await loadBindArtworks()
}

async function loadBindArtworks() {
  bindDialogLoading.value = true
  try {
    const res = await fetchArtworkList({
      page: bindDialogPage.value - 1,
      size: 10,
      keyword: bindDialogKeyword.value || undefined,
    })
    bindArtworkList.value = res.items
    bindDialogTotal.value = res.total
  } catch {
    ElMessage.error('加载封面列表失败')
  } finally {
    bindDialogLoading.value = false
  }
}

function handleBindSearch() {
  bindDialogPage.value = 1
  loadBindArtworks()
}

function handleBindPageChange(page: number) {
  bindDialogPage.value = page
  loadBindArtworks()
}

async function handleBindSelect(artwork: ArtworkItem) {
  if (!bindTargetMusic.value) return
  bindSubmitting.value = true
  try {
    await bindArtworkToMusic(bindTargetMusic.value.id, artwork.id)
    ElMessage.success('封面已绑定')
    bindDialogVisible.value = false
    await loadList()
  } catch {
    ElMessage.error('绑定封面失败')
  } finally {
    bindSubmitting.value = false
  }
}

function handleFileChange(upload: { raw?: File }) {
  const file = upload.raw
  if (!file) return
  if (!ALLOWED_TYPES.includes(file.type)) {
    ElMessage.warning('仅支持 JPG、PNG、WebP 格式')
    return
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.warning('文件大小不能超过 10MB')
    return
  }
  uploadFile.value = file
  uploadFileName.value = file.name
}

async function handleUpload() {
  if (!uploadFile.value || !bindTargetMusic.value) return
  uploading.value = true
  try {
    await importArtworkFile(bindTargetMusic.value.id, uploadFile.value)
    ElMessage.success('封面已导入并绑定')
    bindDialogVisible.value = false
    brokenImages.delete(bindTargetMusic.value.id)
    await loadList()
  } catch {
    ElMessage.error('导入封面失败')
  } finally {
    uploading.value = false
  }
}

function resetUpload() {
  uploadFile.value = null
  uploadFileName.value = ''
}

async function handleUnbind(row: MusicItem) {
  try {
    await unbindArtworkFromMusic(row.id)
    ElMessage.success('封面已取消绑定')
    brokenImages.delete(row.id)
    await loadList()
  } catch {
    ElMessage.error('取消绑定失败')
  }
}

function displayTitle(row: MusicItem): string {
  return row.title || row.fileName || '--'
}

function openEditDialog(row: MusicItem) {
  editForm.id = row.id
  editForm.title = row.title || ''
  editForm.artist = row.artist || ''
  editForm.album = row.album || ''
  editForm.year = row.year ?? null
  editForm.trackNo = row.trackNo ?? null
  editForm.genre = row.genre || ''
  editDialogVisible.value = true
}

async function handleEditSave() {
  if (editForm.trackNo != null && editForm.trackNo <= 0) {
    ElMessage.warning('曲目号必须大于 0')
    return
  }
  if (editForm.year != null) {
    const maxYear = new Date().getFullYear() + 1
    if (editForm.year < 1900 || editForm.year > maxYear) {
      ElMessage.warning(`年份需在 1900 ~ ${maxYear} 之间`)
      return
    }
  }
  editSaving.value = true
  try {
    const res = await updateMusicMetadata(editForm.id, {
      title: editForm.title || undefined,
      artist: editForm.artist || undefined,
      album: editForm.album || undefined,
      year: editForm.year ?? undefined,
      trackNo: editForm.trackNo ?? undefined,
      genre: editForm.genre || undefined,
    })
    const idx = list.value.findIndex((item) => item.id === res.id)
    if (idx !== -1) {
      list.value[idx] = res
    }
    ElMessage.success('元数据已保存')
    editDialogVisible.value = false
  } catch {
    ElMessage.error('保存元数据失败')
  } finally {
    editSaving.value = false
  }
}

function openDeleteDialog(row: MusicItem) {
  deleteTarget.value = row
  deleteDialogVisible.value = true
}

async function handleDeleteConfirm() {
  if (!deleteTarget.value) return
  deleteLoading.value = true
  try {
    await deleteMusic(deleteTarget.value.id)
    ElMessage.success('已移入音乐库回收目录')
    deleteDialogVisible.value = false
    await loadList()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '删除失败'
    ElMessage.error(msg)
  } finally {
    deleteLoading.value = false
  }
}

async function openTrash() {
  trashVisible.value = true
  await loadTrashList()
}

async function loadTrashList() {
  trashLoading.value = true
  try {
    trashList.value = await fetchTrashList()
  } catch {
    ElMessage.error('加载回收站失败')
  } finally {
    trashLoading.value = false
  }
}

function openRestoreConfirm(row: MusicTrashItem) {
  restoreTarget.value = row
  restoreConfirmVisible.value = true
}

async function handleRestore() {
  if (!restoreTarget.value) return
  trashOperationLoading.value = true
  try {
    await restoreMusic(restoreTarget.value.id)
    ElMessage.success('已恢复到音乐列表')
    restoreConfirmVisible.value = false
    await loadTrashList()
    await loadList()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '恢复失败'
    ElMessage.error(msg)
  } finally {
    trashOperationLoading.value = false
  }
}

function openPermDeleteConfirm(row: MusicTrashItem) {
  permDeleteTarget.value = row
  permDeleteConfirmVisible.value = true
}

async function handlePermanentlyDelete() {
  if (!permDeleteTarget.value) return
  trashOperationLoading.value = true
  try {
    await permanentlyDeleteMusic(permDeleteTarget.value.id)
    ElMessage.success('已彻底删除')
    permDeleteConfirmVisible.value = false
    await loadTrashList()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '彻底删除失败'
    ElMessage.error(msg)
  } finally {
    trashOperationLoading.value = false
  }
}

function trashDisplayTitle(row: MusicTrashItem): string {
  return row.title || row.fileName || '--'
}

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadList()
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>音乐库</span>
        <div class="header-actions">
          <el-button type="primary" size="small" :loading="scanning" @click="handleScan">
            扫描音乐目录
          </el-button>
          <el-button size="small" :loading="scanningLyrics" @click="handleLyricScan">
            扫描歌词
          </el-button>
          <el-button size="small" @click="loadList">刷新</el-button>
          <el-button size="small" @click="openTrash">回收站</el-button>
        </div>
      </div>
    </template>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      closable
      style="margin-bottom: 12px"
      @close="errorMessage = ''"
    />

    <el-table
      :data="list"
      v-loading="loading"
      empty-text="暂无音乐文件，请点击「扫描音乐目录」导入"
      style="width: 100%"
    >
      <el-table-column label="歌曲名" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ displayTitle(row) }}
        </template>
      </el-table-column>
      <el-table-column label="歌手" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.artist || 'Unknown' }}
        </template>
      </el-table-column>
      <el-table-column label="专辑" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.album || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="年份" width="70" align="center">
        <template #default="{ row }">
          {{ row.year ?? '--' }}
        </template>
      </el-table-column>
      <el-table-column label="流派" width="100" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.genre || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="格式" width="80">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.fileExtension?.toUpperCase() || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="时长" width="80" align="center">
        <template #default="{ row }">
          {{ formatDuration(row.duration) }}
        </template>
      </el-table-column>
      <el-table-column label="歌词" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="lyricStatusTagType(row.lyricStatus)" size="small">
            {{ lyricStatusLabel(row.lyricStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="封面" width="80" align="center">
        <template #default="{ row }">
          <img
            v-if="row.artworkStatus === 'BOUND' && row.artworkFileExists !== false && row.artworkPreviewUrl && !brokenImages.has(row.id)"
            :src="row.artworkPreviewUrl"
            class="cover-thumb"
            @error="brokenImages.add(row.id)"
          />
          <el-tag v-else-if="row.artworkStatus === 'BOUND' && row.artworkFileExists === false" size="small" type="danger">
            文件缺失
          </el-tag>
          <el-tag v-else-if="row.artworkStatus === 'BOUND' && brokenImages.has(row.id)" size="small" type="warning">
            加载失败
          </el-tag>
          <el-tag v-else size="small" type="info">无封面</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            text
            :icon="Edit"
            @click="openEditDialog(row)"
          >
            编辑
          </el-button>
          <el-button
            type="danger"
            size="small"
            text
            :icon="Delete"
            @click="openDeleteDialog(row)"
          >
            删除
          </el-button>
          <el-button
            v-if="row.lyricStatus === 'BOUND'"
            type="primary"
            size="small"
            text
            :icon="View"
            @click="handleViewLyric(row)"
          >
            歌词
          </el-button>
          <el-button
            v-if="row.artworkStatus === 'BOUND'"
            type="primary"
            size="small"
            text
            :icon="PictureFilled"
            @click="handleBindOpen(row)"
          >
            更换封面
          </el-button>
          <el-button
            v-else
            type="primary"
            size="small"
            text
            :icon="PictureFilled"
            @click="handleBindOpen(row)"
          >
            选择/导入封面
          </el-button>
          <el-button
            v-if="row.artworkStatus === 'BOUND'"
            type="danger"
            size="small"
            text
            @click="handleUnbind(row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </el-card>

  <el-dialog
    v-model="lyricDialogVisible"
    title="歌词详情"
    width="640px"
    destroy-on-close
  >
    <div v-loading="lyricLoading" style="min-height: 120px">
      <template v-if="currentLyric">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="歌曲标题" :span="2">
            {{ currentSongTitle }}
          </el-descriptions-item>
          <el-descriptions-item label="歌手" :span="2">
            {{ currentSongArtist || 'Unknown' }}
          </el-descriptions-item>
          <el-descriptions-item label="歌词来源">
            {{ sourceTypeLabel(currentLyric.sourceType) }}
          </el-descriptions-item>
          <el-descriptions-item label="格式">
            {{ currentLyric.format }}
          </el-descriptions-item>
          <el-descriptions-item label="歌词标题" v-if="currentLyric.title">
            {{ currentLyric.title }}
          </el-descriptions-item>
          <el-descriptions-item label="歌词艺术家" v-if="currentLyric.artist">
            {{ currentLyric.artist }}
          </el-descriptions-item>
          <el-descriptions-item label="专辑" v-if="currentLyric.album">
            {{ currentLyric.album }}
          </el-descriptions-item>
          <el-descriptions-item label="语言" v-if="currentLyric.language">
            {{ currentLyric.language }}
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 16px">
          <div style="font-size: 13px; color: #909399; margin-bottom: 8px">LRC 原文</div>
          <pre class="lyric-content">{{ currentLyric.content }}</pre>
        </div>
      </template>
    </div>
  </el-dialog>

  <el-dialog
    v-model="bindDialogVisible"
    title="选择 / 导入封面"
    width="720px"
    destroy-on-close
  >
    <el-tabs v-model="bindActiveTab" @tab-change="resetUpload">
      <el-tab-pane label="选择已有封面" name="select">
        <div class="bind-filter-bar">
          <el-input
            v-model="bindDialogKeyword"
            placeholder="搜索封面文件名..."
            clearable
            size="small"
            style="width: 260px"
            @keyup.enter="handleBindSearch"
            @clear="handleBindSearch"
          />
          <el-button size="small" type="primary" style="margin-left: 12px" @click="handleBindSearch">
            搜索
          </el-button>
        </div>
        <el-table
          :data="bindArtworkList"
          v-loading="bindDialogLoading"
          empty-text="暂无封面"
          max-height="360"
          style="margin-top: 12px"
        >
          <el-table-column label="" width="60" align="center">
            <template #default="{ row }">
              <img
                v-if="row.previewUrl && row.fileExists && !brokenBindThumbs.has(row.id)"
                :src="row.previewUrl"
                class="bind-thumb"
                @error="brokenBindThumbs.add(row.id)"
              />
              <el-tag v-else-if="!row.fileExists" size="small" type="danger">缺失</el-tag>
              <span v-else style="color: #c0c4cc; font-size: 12px">无</span>
            </template>
          </el-table-column>
          <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
          <el-table-column label="尺寸" width="110" align="center">
            <template #default="{ row }">
              {{ row.width && row.height ? `${row.width}×${row.height}` : '--' }}
            </template>
          </el-table-column>
          <el-table-column label="已绑定" width="80" align="center">
            <template #default="{ row }">
              {{ row.boundCount }}
            </template>
          </el-table-column>
          <el-table-column label="" width="80" align="center">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                :loading="bindSubmitting"
                @click="handleBindSelect(row)"
              >
                选择
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 12px; display: flex; justify-content: flex-end">
          <el-pagination
            v-model:current-page="bindDialogPage"
            :page-size="10"
            :total="bindDialogTotal"
            layout="total, prev, pager, next"
            small
            @current-change="handleBindPageChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="导入本地图片" name="import">
        <div class="upload-area">
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept=".jpg,.jpeg,.png,.webp"
            drag
            :on-change="handleFileChange"
            :file-list="[]"
          >
            <el-icon :size="40" style="color: #909399"><UploadFilled /></el-icon>
            <div style="margin-top: 12px; color: #606266">
              将图片拖到此处，或 <em>点击上传</em>
            </div>
            <template #tip>
              <div style="margin-top: 8px; font-size: 12px; color: #909399">
                支持 JPG / PNG / WebP，单文件不超过 10MB
              </div>
            </template>
          </el-upload>
          <div v-if="uploadFileName" class="upload-file-info">
            <el-tag size="small" closable @close="resetUpload">{{ uploadFileName }}</el-tag>
          </div>
          <el-button
            v-if="uploadFile"
            type="primary"
            :loading="uploading"
            style="margin-top: 16px"
            @click="handleUpload"
          >
            确认导入并绑定
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>

  <el-dialog
    v-model="editDialogVisible"
    title="编辑元数据"
    width="480px"
    destroy-on-close
  >
    <el-form label-width="72px" :disabled="editSaving">
      <el-form-item label="标题">
        <el-input v-model="editForm.title" placeholder="留空则使用文件名" />
      </el-form-item>
      <el-form-item label="歌手">
        <el-input v-model="editForm.artist" placeholder="留空则显示 Unknown" />
      </el-form-item>
      <el-form-item label="专辑">
        <el-input v-model="editForm.album" />
      </el-form-item>
      <el-form-item label="年份">
        <el-input-number
          v-model="editForm.year"
          :min="1900"
          :max="new Date().getFullYear() + 1"
          placeholder="如：2007"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="曲目号">
        <el-input-number
          v-model="editForm.trackNo"
          :min="1"
          placeholder="如：3"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="流派">
        <el-input v-model="editForm.genre" placeholder="如：Pop" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="editSaving" @click="handleEditSave">
        保存
      </el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="deleteDialogVisible"
    title="确认移入音乐库回收目录？"
    width="480px"
    destroy-on-close
  >
    <template v-if="deleteTarget">
      <div style="margin-bottom: 12px; color: #e6a23c">
        <el-icon style="vertical-align: middle; margin-right: 4px"><Delete /></el-icon>
        该操作会将音乐文件移动到当前音乐扫描目录下的 .music-vault-trash，并从列表中隐藏。当前不会彻底删除文件。
      </div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="歌曲标题">
          {{ displayTitle(deleteTarget) }}
        </el-descriptions-item>
        <el-descriptions-item label="文件名">
          {{ deleteTarget.fileName }}
        </el-descriptions-item>
        <el-descriptions-item label="文件路径">
          {{ deleteTarget.filePath }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
    <template #footer>
      <el-button @click="deleteDialogVisible = false">取消</el-button>
      <el-button type="danger" :loading="deleteLoading" @click="handleDeleteConfirm">
        移入回收站
      </el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="trashVisible"
    title="回收站"
    width="900px"
    destroy-on-close
  >
    <el-table
      :data="trashList"
      v-loading="trashLoading"
      empty-text="回收站为空"
      max-height="480"
      style="width: 100%"
    >
      <el-table-column label="标题" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ trashDisplayTitle(row) }}
        </template>
      </el-table-column>
      <el-table-column label="歌手" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.artist || 'Unknown' }}
        </template>
      </el-table-column>
      <el-table-column label="专辑" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.album || '--' }}
        </template>
      </el-table-column>
      <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
      <el-table-column label="原路径" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.originalPath }}
        </template>
      </el-table-column>
      <el-table-column label="回收路径" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.trashPath }}
        </template>
      </el-table-column>
      <el-table-column label="删除时间" width="170">
        <template #default="{ row }">
          {{ row.deletedAt?.replace('T', ' ')?.substring(0, 19) || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="文件" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.trashFileExists ? 'success' : 'danger'" size="small">
            {{ row.trashFileExists ? '存在' : '缺失' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            text
            :icon="RefreshRight"
            @click="openRestoreConfirm(row)"
          >
            恢复
          </el-button>
          <el-button
            type="danger"
            size="small"
            text
            :icon="DeleteFilled"
            @click="openPermDeleteConfirm(row)"
          >
            彻底删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <el-dialog
    v-model="restoreConfirmVisible"
    title="确认恢复"
    width="460px"
    destroy-on-close
    append-to-body
  >
    <template v-if="restoreTarget">
      <div style="margin-bottom: 12px; color: #409eff">
        该操作会将 <strong>{{ trashDisplayTitle(restoreTarget) }}</strong> 恢复到原始路径，并重新显示在音乐列表。
      </div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="原路径">
          {{ restoreTarget.originalPath }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
    <template #footer>
      <el-button @click="restoreConfirmVisible = false">取消</el-button>
      <el-button type="primary" :loading="trashOperationLoading" @click="handleRestore">
        确认恢复
      </el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="permDeleteConfirmVisible"
    title="确认彻底删除"
    width="460px"
    destroy-on-close
    append-to-body
  >
    <template v-if="permDeleteTarget">
      <div style="margin-bottom: 12px; color: #f56c6c">
        此操作会 <strong>永久删除</strong> 回收目录中的文件，无法通过星语音库恢复。
      </div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="标题">
          {{ trashDisplayTitle(permDeleteTarget) }}
        </el-descriptions-item>
        <el-descriptions-item label="文件名">
          {{ permDeleteTarget.fileName }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
    <template #footer>
      <el-button @click="permDeleteConfirmVisible = false">取消</el-button>
      <el-button type="danger" :loading="trashOperationLoading" @click="handlePermanentlyDelete">
        彻底删除
      </el-button>
    </template>
  </el-dialog>
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
.lyric-content {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
}
.cover-thumb {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
}
.bind-filter-bar {
  display: flex;
  align-items: center;
}
.bind-thumb {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 3px;
}
.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
}
.upload-file-info {
  margin-top: 12px;
}
</style>
