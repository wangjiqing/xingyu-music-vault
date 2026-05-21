<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Edit, View, Delete } from '@element-plus/icons-vue'
import {
  fetchArtistDetail,
  fetchMusicList,
  updateMusicMetadata,
  deleteMusic,
  type MusicItem,
  type MusicMetadataUpdate,
  type ArtistDetailResponse,
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
import ArtistSummaryHeader from '../components/artist/ArtistSummaryHeader.vue'
import ArtistAlbumCard from '../components/artist/ArtistAlbumCard.vue'

const route = useRoute()
const router = useRouter()

const artistKey = computed(() => route.params.artistKey as string)

const detail = ref<ArtistDetailResponse | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const songList = ref<MusicItem[]>([])
const songLoading = ref(false)
const songTotal = ref(0)
const songQuery = reactive({
  page: 1,
  size: 20,
})
const songError = ref('')

const activeTab = ref('songs')

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

async function loadDetail() {
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await fetchArtistDetail(artistKey.value)
  } catch {
    detailError.value = '加载歌手详情失败，请检查后端服务是否运行'
    ElMessage.error('加载歌手详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function loadSongList() {
  songLoading.value = true
  songError.value = ''
  try {
    const res = await fetchMusicList({
      page: songQuery.page - 1,
      size: songQuery.size,
      artistKey: artistKey.value,
    })
    songList.value = res.items
    songTotal.value = res.total
  } catch {
    songError.value = '加载歌曲列表失败'
    ElMessage.error('加载歌曲列表失败')
  } finally {
    songLoading.value = false
  }
}

function handleSongPageChange(page: number) {
  songQuery.page = page
  loadSongList()
}

function handleSongSizeChange(size: number) {
  songQuery.size = size
  songQuery.page = 1
  loadSongList()
}

function goBack() {
  router.push('/artists')
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
    const idx = songList.value.findIndex((item) => item.id === res.id)
    if (idx !== -1) {
      songList.value[idx] = res
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
    await loadSongList()
  } catch (e: any) {
    const msg = e?.response?.data?.message || '删除失败'
    ElMessage.error(msg)
  } finally {
    deleteLoading.value = false
  }
}

function handleAlbumClick() {
  ElMessage.info('专辑详情将在后续版本实现')
}

onMounted(() => {
  loadDetail()
  loadSongList()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <el-button :icon="ArrowLeft" text size="small" @click="goBack">
          返回歌手列表
        </el-button>
      </div>
    </template>

    <div v-loading="detailLoading" class="artist-detail">
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
        <ArtistSummaryHeader :detail="detail" />

        <el-tabs v-model="activeTab" @tab-change="() => {}">
          <el-tab-pane label="歌曲" name="songs">
            <el-alert
              v-if="songError"
              :title="songError"
              type="error"
              show-icon
              closable
              style="margin-bottom: 12px"
              @close="songError = ''"
            />

            <el-table
              :data="songList"
              v-loading="songLoading"
              empty-text="暂无歌曲"
              style="width: 100%"
            >
              <el-table-column label="歌曲名" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ displayTitle(row) }}
                </template>
              </el-table-column>
              <el-table-column label="专辑" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.album || '--' }}
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
              <el-table-column label="操作" width="140" fixed="right">
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
                v-model:current-page="songQuery.page"
                v-model:page-size="songQuery.size"
                :total="songTotal"
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next"
                @current-change="handleSongPageChange"
                @size-change="handleSongSizeChange"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane label="专辑" name="albums">
            <div v-if="detail.albums.length > 0" class="album-card-grid">
              <ArtistAlbumCard
                v-for="album in detail.albums"
                :key="album.albumKey"
                :item="album"
                @click="handleAlbumClick"
              />
            </div>
            <el-empty v-else description="暂无专辑" />
          </el-tab-pane>
        </el-tabs>
      </template>

      <el-empty v-else-if="!detailLoading" description="未找到该歌手" />
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
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
}
.artist-detail {
  min-height: 200px;
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
.album-card-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 14px;
}
@media (max-width: 1680px) {
  .album-card-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
@media (max-width: 1360px) {
  .album-card-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 1080px) {
  .album-card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .album-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 420px) {
  .album-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
