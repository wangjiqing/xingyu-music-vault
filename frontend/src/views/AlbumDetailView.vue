<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Edit, View, Delete, Connection } from '@element-plus/icons-vue'
import {
  fetchAlbumDetail,
  fetchMusicList,
  updateMusicMetadata,
  deleteMusic,
  type AlbumItem,
  type MusicItem,
  type MusicMetadataUpdate,
} from '../api/music'
import { fetchSongLyric, type SongLyric } from '../api/lyrics'
import {
  ARTWORK_STATUS,
  LYRIC_STATUS,
  lyricStatusLabel,
  lyricStatusTagType,
  artworkStatusLabel,
  hasCompleteMusicMetadata,
} from '../constants/musicStatus'
import ArtworkImage from '../components/music/ArtworkImage.vue'
import MetadataCompareDialog from '../components/music/MetadataCompareDialog.vue'

const route = useRoute()
const router = useRouter()

const albumKey = computed(() => route.query.albumKey as string)
const artistKey = computed(() => route.query.artistKey as string)

const detail = ref<AlbumItem | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const trackList = ref<MusicItem[]>([])
const trackLoading = ref(false)
const trackTotal = ref(0)
const trackQuery = reactive({
  page: 1,
  size: 20,
})
const trackError = ref('')

const coverUrl = ref<string | null>(null)

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

const lyricDialogVisible = ref(false)
const lyricLoading = ref(false)
const currentLyric = ref<SongLyric | null>(null)
const currentSongTitle = ref('')
const currentSongArtist = ref('')

const deleteDialogVisible = ref(false)
const deleteLoading = ref(false)
const deleteTarget = ref<MusicItem | null>(null)

const metadataCompareRef = ref<InstanceType<typeof MetadataCompareDialog>>()
const metadataSyncMusicId = ref(0)

async function loadDetail() {
  if (!albumKey.value || !artistKey.value) {
    detailError.value = '缺少 albumKey 或 artistKey 参数'
    return
  }
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await fetchAlbumDetail(albumKey.value, artistKey.value)
  } catch {
    detailError.value = '加载专辑详情失败，请检查后端服务是否运行'
    ElMessage.error('加载专辑详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadTrackList() {
  if (!albumKey.value || !artistKey.value) return
  trackLoading.value = true
  trackError.value = ''
  try {
    const res = await fetchMusicList({
      page: trackQuery.page - 1,
      size: trackQuery.size,
      albumKey: albumKey.value,
      artistKey: artistKey.value,
    })
    trackList.value = res.items
    trackTotal.value = res.total
    if (res.items.length > 0 && !coverUrl.value) {
      const first = res.items[0]
      if (first.artworkStatus === ARTWORK_STATUS.BOUND && first.artworkPreviewUrl) {
        coverUrl.value = first.artworkPreviewUrl
      }
    }
  } catch {
    trackError.value = '加载曲目列表失败'
    ElMessage.error('加载曲目列表失败')
  } finally {
    trackLoading.value = false
  }
}

function handleTrackPageChange(page: number) {
  trackQuery.page = page
  loadTrackList()
}

function handleTrackSizeChange(size: number) {
  trackQuery.size = size
  trackQuery.page = 1
  loadTrackList()
}

function goBack() {
  router.push('/albums')
}

function displayTitle(row: MusicItem): string {
  return row.title || row.fileName || '--'
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
    const idx = trackList.value.findIndex((item) => item.id === res.id)
    if (idx !== -1) {
      trackList.value[idx] = res
    }
    ElMessage.success('元数据已保存')
    editDialogVisible.value = false
  } catch {
    ElMessage.error('保存元数据失败')
  } finally {
    editSaving.value = false
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
    await loadTrackList()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '删除失败'
    ElMessage.error(msg)
  } finally {
    deleteLoading.value = false
  }
}

async function openMetadataSync(row: MusicItem) {
  metadataSyncMusicId.value = row.id
  await nextTick()
  metadataCompareRef.value?.open()
}

function onMetadataSyncDone() {
  loadTrackList()
  loadDetail()
}

onMounted(() => {
  loadDetail()
  loadTrackList()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <el-button :icon="ArrowLeft" text size="small" @click="goBack">
          返回专辑列表
        </el-button>
      </div>
    </template>

    <div v-loading="detailLoading" class="album-detail">
      <el-alert
        v-if="detailError"
        :title="detailError"
        type="error"
        show-icon
        closable
        style="margin-bottom: 12px"
        @close="detailError = ''"
      />

      <template v-if="detail">
        <div class="album-overview">
          <div class="album-cover">
            <ArtworkImage
              v-if="coverUrl"
              :src="coverUrl"
              :alt="detail.album"
              :size="180"
              :radius="8"
            />
            <div v-else class="album-cover-placeholder">
              <span class="album-cover-initial">{{ detail.album?.charAt(0).toUpperCase() || '?' }}</span>
            </div>
          </div>
          <div class="album-info">
            <h2 class="album-info-name">{{ detail.album || 'Unknown' }}</h2>
            <div class="album-info-artist">{{ detail.albumArtist || 'Unknown' }}</div>
            <div class="album-info-year" v-if="detail.year">{{ detail.year }}</div>
            <div class="album-info-stats">
              <div class="info-stat-item">
                <el-tag size="small" type="info">{{ detail.trackCount }} 曲目</el-tag>
              </div>
              <div class="info-stat-item">
                <el-tag size="small" :type="detail.lyricsCount > 0 ? 'success' : 'info'">
                  {{ detail.lyricsCount }} 歌词
                </el-tag>
              </div>
              <div class="info-stat-item">
                <el-tag size="small" :type="detail.artworkCount > 0 ? 'success' : 'info'">
                  {{ detail.artworkCount }} 封面
                </el-tag>
              </div>
              <div v-if="detail.metadataIncompleteCount > 0" class="info-stat-item">
                <el-tag size="small" type="warning">{{ detail.metadataIncompleteCount }} 待完善</el-tag>
              </div>
            </div>
          </div>
        </div>

        <el-alert
          v-if="trackError"
          :title="trackError"
          type="error"
          show-icon
          closable
          style="margin: 16px 0"
          @close="trackError = ''"
        />

        <h3 style="margin: 16px 0 12px; font-size: 15px; color: #303133">曲目列表</h3>

        <el-table
          :data="trackList"
          v-loading="trackLoading"
          empty-text="暂无曲目"
          style="width: 100%"
        >
          <el-table-column label="歌曲名" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{ displayTitle(row) }}
            </template>
          </el-table-column>
          <el-table-column label="歌手" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.artist || 'Unknown' }}
            </template>
          </el-table-column>
          <el-table-column label="年份" width="70" align="center">
            <template #default="{ row }">
              {{ row.year ?? '--' }}
            </template>
          </el-table-column>
          <el-table-column label="时长" width="80" align="center">
            <template #default="{ row }">
              {{ formatDuration(row.duration) }}
            </template>
          </el-table-column>
          <el-table-column label="歌词" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="lyricStatusTagType(row.lyricStatus)" size="small">
                {{ lyricStatusLabel(row.lyricStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="封面" width="90" align="center">
            <template #default="{ row }">
              <ArtworkImage
                v-if="row.artworkStatus === ARTWORK_STATUS.BOUND"
                :src="row.artworkPreviewUrl"
                :file-exists="row.artworkFileExists"
                :alt="displayTitle(row)"
                :size="40"
                :radius="4"
              />
              <el-tag v-else size="small" type="info">
                {{ artworkStatusLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="元数据" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="hasCompleteMusicMetadata(row) ? 'success' : 'warning'" size="small">
                {{ hasCompleteMusicMetadata(row) ? '完整' : '待完善' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
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
                type="primary"
                size="small"
                text
                :icon="Connection"
                @click="openMetadataSync(row)"
              >
                同步
              </el-button>
              <el-button
                v-if="row.lyricStatus === LYRIC_STATUS.BOUND"
                type="primary"
                size="small"
                text
                :icon="View"
                @click="handleViewLyric(row)"
              >
                歌词
              </el-button>
              <el-button
                type="danger"
                size="small"
                text
                :icon="Delete"
                @click="openDeleteDialog(row)"
              />
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 12px; display: flex; justify-content: flex-end">
          <el-pagination
            v-model:current-page="trackQuery.page"
            v-model:page-size="trackQuery.size"
            :total="trackTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="handleTrackPageChange"
            @size-change="handleTrackSizeChange"
          />
        </div>
      </template>

      <el-empty v-else-if="!detailLoading" description="未找到该专辑" />
    </div>
  </el-card>

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
            {{ currentLyric.sourceType }}
          </el-descriptions-item>
          <el-descriptions-item label="格式">
            {{ currentLyric.format }}
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
    v-model="deleteDialogVisible"
    title="确认移入音乐库回收目录？"
    width="480px"
    destroy-on-close
  >
    <template v-if="deleteTarget">
      <div style="margin-bottom: 12px; color: #e6a23c">
        该操作会将音乐文件移动到回收目录，并从列表中隐藏。
      </div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="歌曲标题">
          {{ displayTitle(deleteTarget) }}
        </el-descriptions-item>
        <el-descriptions-item label="文件名">
          {{ deleteTarget.fileName }}
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

  <MetadataCompareDialog
    ref="metadataCompareRef"
    :music-id="metadataSyncMusicId"
    @done="onMetadataSyncDone"
  />
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
}
.album-detail {
  min-height: 200px;
}
.album-overview {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.album-cover {
  flex-shrink: 0;
}
.album-cover-placeholder {
  width: 180px;
  height: 180px;
  background: linear-gradient(135deg, #f0f2f5 0%, #dcdfe6 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.album-cover-initial {
  font-size: 64px;
  font-weight: 700;
  color: #a8abb2;
  user-select: none;
}
.album-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.album-info-name {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.album-info-artist {
  font-size: 15px;
  color: #606266;
}
.album-info-year {
  font-size: 14px;
  color: #909399;
}
.album-info-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
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
</style>
