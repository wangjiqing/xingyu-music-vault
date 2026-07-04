<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import {
  fetchMusicList,
  fetchMusicOpenApiPreview,
  fetchMusicWorkbench,
  musicAudioUrl,
  type MusicItem,
  type MusicWorkbench,
  type OpenApiPreview,
} from '../api/music'
import SongWorkbenchPlayer from '../components/workbench/SongWorkbenchPlayer.vue'
import type { PlaybackMode } from '../components/workbench/SongWorkbenchPlayer.vue'
import SongWorkbenchMetadataPanel from '../components/workbench/SongWorkbenchMetadataPanel.vue'
import SongWorkbenchLyricsPanel from '../components/workbench/SongWorkbenchLyricsPanel.vue'
import SongWorkbenchArtworkPanel from '../components/workbench/SongWorkbenchArtworkPanel.vue'
import SongWorkbenchOpenApiPanel from '../components/workbench/SongWorkbenchOpenApiPanel.vue'
import SongWorkbenchAlignmentPanel from '../components/workbench/SongWorkbenchAlignmentPanel.vue'
import SongWorkbenchDraftPanel from '../components/workbench/SongWorkbenchDraftPanel.vue'
import { currentThemeAssets } from '../theme/currentTheme'

const route = useRoute()
const router = useRouter()

const list = ref<MusicItem[]>([])
const total = ref(0)
const listLoading = ref(false)
const detailLoading = ref(false)
const selectedId = ref<number | null>(null)
const workbench = ref<MusicWorkbench | null>(null)
const openApiPreview = ref<OpenApiPreview | null>(null)
const openApiError = ref('')
const playbackError = ref('')
const activeTab = ref(resolveRouteTab(route.query.tab))
const preselectedSourceLyricsId = ref<number | null>(null)
const prefetching = ref(false)
const playbackMode = ref<PlaybackMode>('order')
const playerCurrentTime = ref(0)
const playerDuration = ref(0)
const playbackActive = ref(false)
const autoplayOnSourceChange = ref(false)

const query = reactive({
  page: 0,
  size: 30,
  keyword: '',
})

const selectedIndex = computed(() => list.value.findIndex((item) => item.id === selectedId.value))
const canPrevious = computed(() => selectedIndex.value > 0)
const canNext = computed(() => selectedIndex.value >= 0 && selectedIndex.value < list.value.length - 1)
const currentMusic = computed(() => workbench.value?.music || list.value.find((item) => item.id === selectedId.value) || null)
const hasMoreRows = computed(() => list.value.length < total.value)
const playerCanNext = computed(() => canNext.value || hasMoreRows.value || playbackMode.value === 'repeat-list' || playbackMode.value === 'shuffle')
const artworkBackgroundUrl = computed(() => workbench.value?.artwork.available ? workbench.value.artwork.previewUrl : '')

function resolveRouteTab(value: unknown): string {
  const tab = typeof value === 'string' ? value : ''
  return ['lyrics', 'draft', 'alignment', 'artwork', 'metadata', 'openapi'].includes(tab) ? tab : 'lyrics'
}

function resolveSourceLyricsId(value: unknown): number | null {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function workbenchQuery(id: number) {
  const query: Record<string, string> = { id: String(id) }
  if (activeTab.value !== 'lyrics') {
    query.tab = activeTab.value
  }
  if (preselectedSourceLyricsId.value && activeTab.value === 'alignment') {
    query.sourceLyricsAssetId = String(preselectedSourceLyricsId.value)
  }
  return query
}

function displayTitle(item: MusicItem | null): string {
  if (!item) return '未选择歌曲'
  return item.title || item.fileName || `#${item.id}`
}

function displaySubtitle(item: MusicItem | null): string {
  if (!item) return ''
  return [item.artist || 'Unknown', item.album || '未知专辑'].join(' / ')
}

async function loadList(keepSelected = false) {
  listLoading.value = true
  try {
    const res = await fetchMusicList({ page: query.page, size: query.size, keyword: query.keyword || undefined })
    list.value = res.items
    total.value = res.total
    if (!keepSelected || !selectedId.value) {
      const routeId = Number(route.query.id)
      selectedId.value = Number.isFinite(routeId) && routeId > 0 ? routeId : list.value[0]?.id || null
    }
    if (selectedId.value) {
      await loadWorkbench(selectedId.value)
    }
  } catch {
    ElMessage.error('加载工作台歌曲列表失败')
  } finally {
    listLoading.value = false
  }
}

async function appendNextPage(): Promise<boolean> {
  if (prefetching.value || !hasMoreRows.value) return false
  prefetching.value = true
  try {
    const nextPage = query.page + 1
    const res = await fetchMusicList({ page: nextPage, size: query.size, keyword: query.keyword || undefined })
    list.value = [...list.value, ...res.items]
    total.value = res.total
    if (res.items.length > 0) {
      query.page = nextPage
      return true
    }
    return false
  } catch {
    ElMessage.error('加载更多歌曲失败')
    return false
  } finally {
    prefetching.value = false
  }
}

async function loadWorkbench(id: number, autoplay = false) {
  detailLoading.value = true
  playbackError.value = ''
  openApiError.value = ''
  autoplayOnSourceChange.value = autoplay
  try {
    const data = await fetchMusicWorkbench(id)
    workbench.value = data
    openApiPreview.value = data.openApiPreview
    selectedId.value = id
    router.replace({ path: '/music/workbench', query: workbenchQuery(id) })
  } catch {
    workbench.value = null
    openApiPreview.value = null
    ElMessage.error('加载歌曲工作台数据失败')
  } finally {
    detailLoading.value = false
  }
}

async function refreshOpenApiPreview() {
  if (!selectedId.value) return
  openApiError.value = ''
  try {
    openApiPreview.value = await fetchMusicOpenApiPreview(selectedId.value)
  } catch {
    openApiError.value = 'OpenAPI 输出预览获取失败'
  }
}

function selectMusic(item: MusicItem) {
  loadWorkbench(item.id, playbackActive.value)
}

function previous() {
  if (!canPrevious.value) return
  loadWorkbench(list.value[selectedIndex.value - 1].id, playbackActive.value)
}

async function next(autoplay = playbackActive.value) {
  if (playbackMode.value === 'shuffle') {
    const candidate = randomTrack()
    if (candidate) {
      await loadWorkbench(candidate.id, autoplay)
    }
    return
  }
  if (canNext.value) {
    await loadWorkbench(list.value[selectedIndex.value + 1].id, autoplay)
    return
  }
  if (hasMoreRows.value) {
    await appendNextPage()
    if (selectedIndex.value >= 0 && selectedIndex.value < list.value.length - 1) {
      await loadWorkbench(list.value[selectedIndex.value + 1].id, autoplay)
      return
    }
  }
  if (playbackMode.value === 'repeat-list' && list.value.length > 0) {
    await loadWorkbench(list.value[0].id, autoplay)
  }
}

function handleSearch() {
  query.page = 0
  loadList(false)
}

function handleListScroll(event: Event) {
  const target = event.currentTarget as HTMLElement
  const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight
  if (distanceToBottom <= 520) {
    appendNextPage()
  }
}

async function handleTrackEnded() {
  if (playbackMode.value === 'order' && !canNext.value && hasMoreRows.value) {
    await appendNextPage()
  }
  if (playbackMode.value === 'order' && !canNext.value) {
    playbackActive.value = false
    return
  }
  await next(true)
}

function randomTrack(): MusicItem | null {
  if (list.value.length === 0) return null
  if (list.value.length === 1) return list.value[0]
  const currentId = selectedId.value
  const candidates = list.value.filter((item) => item.id !== currentId)
  return candidates[Math.floor(Math.random() * candidates.length)] || list.value[0]
}

function handlePlayingChange(value: boolean) {
  playbackActive.value = value
  if (value) {
    autoplayOnSourceChange.value = false
  }
}

async function handleAlignmentImported() {
  if (!selectedId.value) return
  await loadWorkbench(selectedId.value, false)
}

function handleDraftTrustedConfirmed(trustedLyricsId: number) {
  preselectedSourceLyricsId.value = trustedLyricsId
  activeTab.value = 'alignment'
}

watch(
  () => route.query.id,
  (value) => {
    const id = Number(value)
    if (Number.isFinite(id) && id > 0 && id !== selectedId.value) {
      loadWorkbench(id)
    }
  },
)

watch(
  () => route.query.tab,
  (value) => {
    const nextTab = resolveRouteTab(value)
    if (nextTab !== activeTab.value) {
      activeTab.value = nextTab
    }
  },
)

watch(
  () => route.query.sourceLyricsAssetId,
  (value) => {
    preselectedSourceLyricsId.value = resolveSourceLyricsId(value)
  },
  { immediate: true },
)

watch(activeTab, () => {
  if (selectedId.value) {
    router.replace({ path: '/music/workbench', query: workbenchQuery(selectedId.value) })
  }
})

onMounted(() => loadList(false))
</script>

<template>
  <div class="workbench-view">
    <aside class="workbench-list">
      <div class="list-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索歌曲 / 歌手 / 专辑"
          clearable
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button :icon="RefreshRight" @click="loadList(true)" />
      </div>
      <div v-loading="listLoading" class="song-list" @scroll="handleListScroll">
        <button
          v-for="item in list"
          :key="item.id"
          class="song-row"
          :class="{ active: item.id === selectedId }"
          type="button"
          @click="selectMusic(item)"
        >
          <span class="song-title">{{ displayTitle(item) }}</span>
          <span class="song-meta">{{ displaySubtitle(item) }}</span>
        </button>
        <el-empty
          v-if="!listLoading && list.length === 0"
          description="暂无歌曲"
          :image="currentThemeAssets.emptyStates.songs"
          :image-size="140"
        />
      </div>
      <div class="list-footer">
        <span>共 {{ total }} 首</span>
        <span v-if="prefetching">加载下一页...</span>
        <span v-else-if="!hasMoreRows">已加载全部</span>
        <span v-else>已加载 {{ list.length }} 首</span>
      </div>
    </aside>

    <main
      v-loading="detailLoading"
      class="workbench-main"
      :class="{ 'has-artwork-bg': Boolean(artworkBackgroundUrl) }"
      :style="artworkBackgroundUrl ? { '--workbench-artwork-bg': `url(${artworkBackgroundUrl})` } : undefined"
    >
      <template v-if="currentMusic && workbench">
        <div class="workbench-bg" aria-hidden="true" />
        <header class="workbench-header">
          <div>
            <h1>{{ displayTitle(currentMusic) }}</h1>
            <p>{{ displaySubtitle(currentMusic) }}</p>
          </div>
          <el-tag type="info">{{ currentMusic.fileExtension?.toUpperCase() || 'AUDIO' }}</el-tag>
        </header>

        <el-alert
          v-if="playbackError"
          class="workbench-alert"
          type="error"
          show-icon
          :title="playbackError"
        />

        <el-tabs v-model="activeTab" class="workbench-tabs">
          <el-tab-pane label="歌词" name="lyrics">
            <SongWorkbenchLyricsPanel
              :lyrics="workbench.lyrics"
              :music="workbench.music"
              :artwork="workbench.artwork"
              :current-time="playerCurrentTime"
              :duration="playerDuration"
            />
          </el-tab-pane>
          <el-tab-pane label="歌词草稿" name="draft" class="workbench-scroll-pane">
            <SongWorkbenchDraftPanel
              :music="workbench.music"
              :current-lyric-available="workbench.lyrics.available"
              @trusted-confirmed="handleDraftTrustedConfirmed"
            />
          </el-tab-pane>
          <el-tab-pane label="歌词对齐" name="alignment" class="workbench-scroll-pane">
            <SongWorkbenchAlignmentPanel
              :music="workbench.music"
              :preselected-source-lyrics-id="preselectedSourceLyricsId"
              @imported="handleAlignmentImported"
              @view-lyrics="activeTab = 'lyrics'"
            />
          </el-tab-pane>
          <el-tab-pane label="封面" name="artwork">
            <SongWorkbenchArtworkPanel :artwork="workbench.artwork" />
          </el-tab-pane>
          <el-tab-pane label="元数据" name="metadata" class="workbench-scroll-pane">
            <SongWorkbenchMetadataPanel :music="workbench.music" />
          </el-tab-pane>
          <el-tab-pane label="OpenAPI 输出" name="openapi" class="workbench-scroll-pane">
            <div class="openapi-toolbar">
              <span>OpenAPI 输出预览</span>
              <el-button size="small" :icon="RefreshRight" @click="refreshOpenApiPreview">刷新</el-button>
            </div>
            <SongWorkbenchOpenApiPanel :preview="openApiPreview" :error="openApiError" />
          </el-tab-pane>
        </el-tabs>

        <SongWorkbenchPlayer
          :src="musicAudioUrl(currentMusic.id)"
          :title="displayTitle(currentMusic)"
          :artist="currentMusic.artist || 'Unknown'"
          :album="currentMusic.album || '未知专辑'"
          :can-previous="canPrevious"
          :can-next="playerCanNext"
          :mode="playbackMode"
          :autoplay-on-source-change="autoplayOnSourceChange"
          @previous="previous"
          @next="next"
          @ended="handleTrackEnded"
          @error="playbackError = $event"
          @timeupdate="playerCurrentTime = $event"
          @durationchange="playerDuration = $event"
          @playing-change="handlePlayingChange"
          @mode-change="playbackMode = $event"
        />
      </template>
      <el-empty
        v-else
        description="请选择歌曲"
        :image="currentThemeAssets.emptyStates.songs"
        :image-size="180"
      />
    </main>
  </div>
</template>

<style scoped>
.workbench-view {
  --workbench-scale: 1;
  --workbench-gap: calc(16px * var(--workbench-scale));
  --workbench-panel-padding: calc(18px * var(--workbench-scale));
  --workbench-list-min: calc(260px * var(--workbench-scale));
  --workbench-list-max: calc(340px * var(--workbench-scale));
  --workbench-header-title-size: calc(24px * var(--workbench-scale));
  --workbench-header-meta-size: calc(14px * var(--workbench-scale));
  --workbench-tab-gap: calc(18px * var(--workbench-scale));
  display: grid;
  grid-template-columns: minmax(var(--workbench-list-min), var(--workbench-list-max)) minmax(0, 1fr);
  gap: var(--workbench-gap);
  height: calc(100vh - 144px);
  min-height: 0;
  overflow: hidden;
}
.workbench-list,
.workbench-main {
  min-width: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-bg-color) 94%, transparent);
}
.workbench-list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.list-toolbar {
  display: flex;
  gap: 8px;
  padding: calc(12px * var(--workbench-scale));
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.song-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: calc(8px * var(--workbench-scale));
}
.song-row {
  display: block;
  width: 100%;
  min-height: calc(58px * var(--workbench-scale));
  margin: 0 0 6px;
  padding: calc(9px * var(--workbench-scale)) calc(10px * var(--workbench-scale));
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
}
.song-row:hover,
.song-row.active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.song-title,
.song-meta {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.song-title {
  font-size: calc(14px * var(--workbench-scale));
  font-weight: 600;
}
.song-meta {
  margin-top: calc(5px * var(--workbench-scale));
  color: var(--el-text-color-secondary);
  font-size: calc(12px * var(--workbench-scale));
}
.list-footer {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: calc(10px * var(--workbench-scale)) calc(12px * var(--workbench-scale));
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: calc(12px * var(--workbench-scale));
}
.workbench-main {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: var(--workbench-panel-padding);
  overflow: hidden;
}
.workbench-main > :not(.workbench-bg) {
  position: relative;
  z-index: 1;
}
.workbench-bg {
  position: absolute;
  inset: calc(-1 * var(--workbench-panel-padding));
  z-index: 0;
  pointer-events: none;
  opacity: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(255, 255, 255, 0.97)),
    var(--workbench-artwork-bg) center / cover;
  filter: blur(14px) saturate(0.92);
  transform: scale(1.04);
  transition: opacity 0.2s ease;
}
.workbench-main.has-artwork-bg .workbench-bg {
  opacity: 0.74;
}
.workbench-header {
  display: flex;
  justify-content: space-between;
  gap: calc(12px * var(--workbench-scale));
  margin-bottom: calc(14px * var(--workbench-scale));
  flex-shrink: 0;
}
.workbench-header h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: var(--workbench-header-title-size);
  line-height: 1.25;
}
.workbench-header p {
  margin: calc(8px * var(--workbench-scale)) 0 0;
  color: var(--el-text-color-secondary);
  font-size: var(--workbench-header-meta-size);
}
.workbench-alert {
  margin-top: 12px;
}
.workbench-tabs {
  min-height: 0;
  margin-top: var(--workbench-tab-gap);
  flex: 1;
  overflow: hidden;
}
.workbench-tabs :deep(.el-tabs__content) {
  height: calc(100% - (55px * var(--workbench-scale)));
  overflow: hidden;
}
.workbench-tabs :deep(.el-tabs__item) {
  height: calc(40px * var(--workbench-scale));
  padding-inline: calc(20px * var(--workbench-scale));
  font-size: calc(14px * var(--workbench-scale));
  line-height: calc(40px * var(--workbench-scale));
}
.workbench-tabs :deep(.el-tab-pane) {
  height: 100%;
  overflow: hidden;
}
.workbench-tabs :deep(.workbench-scroll-pane) {
  overflow: auto;
  padding-right: calc(8px * var(--workbench-scale));
  scrollbar-width: thin;
}
.openapi-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: calc(10px * var(--workbench-scale));
  color: var(--el-text-color-regular);
  font-size: calc(14px * var(--workbench-scale));
  font-weight: 600;
}
@media (max-width: 1500px), (max-height: 880px) {
  .workbench-view {
    --workbench-scale: 0.92;
  }
}
@media (max-width: 1280px), (max-height: 760px) {
  .workbench-view {
    --workbench-scale: 0.84;
    height: calc(100vh - 124px);
  }
}
@media (max-width: 980px) {
  .workbench-view {
    --workbench-scale: 0.9;
    grid-template-columns: 1fr;
    height: auto;
    min-height: calc(100vh - 144px);
    overflow: visible;
  }
  .workbench-list {
    min-height: 320px;
  }
}
</style>
