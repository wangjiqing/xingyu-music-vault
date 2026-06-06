<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight } from '@element-plus/icons-vue'
import {
  fetchArtistList,
  type ArtistItem,
} from '../api/music'
import ArtistCard from '../components/artist/ArtistCard.vue'

const router = useRouter()

const list = ref<ArtistItem[]>([])
const loading = ref(false)
const errorMessage = ref('')

const query = reactive({
  page: 1,
  pageSize: 20,
  keyword: '',
  sort: 'trackCountDesc' as string,
})

const total = ref(0)
const loadingMore = ref(false)
const loadedCount = computed(() => list.value.length)
const hasMoreRows = computed(() => loadedCount.value < total.value)
const loadedRangeText = computed(() => {
  if (total.value === 0) return '当前 0 - 0'
  return `当前 1 - ${Math.min(loadedCount.value, total.value)}`
})

const sortOptions = [
  { label: '歌曲数 ↓', value: 'trackCountDesc' },
  { label: '名称 A-Z', value: 'nameAsc' },
  { label: '专辑数 ↓', value: 'albumCountDesc' },
  { label: '待整理 ↓', value: 'metadataIncompleteDesc' },
]

function listParams(page: number) {
  return {
    page,
    pageSize: query.pageSize,
    keyword: query.keyword || undefined,
    sort: query.sort,
  }
}

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    query.page = 1
    const [currentRes, nextRes] = await Promise.all([
      fetchArtistList(listParams(1)),
      fetchArtistList(listParams(2)),
    ])
    list.value = [...currentRes.items, ...nextRes.items]
    total.value = nextRes.total || currentRes.total
    query.page = nextRes.items.length > 0 ? 2 : 1
  } catch {
    errorMessage.value = '加载歌手列表失败，请检查后端服务是否运行'
    ElMessage.error('加载歌手列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleSortChange() {
  query.page = 1
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
}

async function appendNextPage() {
  if (loadingMore.value || !hasMoreRows.value) return
  loadingMore.value = true
  try {
    const nextPage = query.page + 1
    const res = await fetchArtistList(listParams(nextPage))
    list.value = [...list.value, ...res.items]
    total.value = res.total
    if (res.items.length > 0) {
      query.page = nextPage
    }
  } finally {
    loadingMore.value = false
  }
}

function handleListScroll(event: Event) {
  const target = event.currentTarget as HTMLElement
  const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight
  if (distanceToBottom <= 720) {
    appendNextPage()
  }
}

function handleArtistClick(item: ArtistItem) {
  router.push({
    path: `/artists/${item.artistKey}`,
    query: { name: item.artist || item.artistKey },
  })
}

function emptyText(): string {
  return query.keyword ? '没有匹配的歌手' : '暂无歌手数据，请先扫描音乐目录'
}

function emptyImage(): string {
  return '/themes/midsummer-starlight/empty-states/empty-artists.png'
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>歌手</span>
        <div class="header-actions">
          <el-button size="small" :icon="RefreshRight" @click="loadList">刷新</el-button>
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

    <div class="search-bar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索歌手名..."
        clearable
        size="small"
        style="width: 280px"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="query.sort"
        size="small"
        style="width: 140px; margin-left: 8px"
        @change="handleSortChange"
      >
        <el-option
          v-for="opt in sortOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <div v-loading="loading" class="artist-view" @scroll="handleListScroll">
      <div v-if="list.length > 0" class="artist-card-grid">
        <ArtistCard
          v-for="item in list"
          :key="item.artistKey"
          :item="item"
          @click="handleArtistClick"
        />
      </div>
      <el-empty v-else :description="emptyText()" :image="emptyImage()" :image-size="180" />
    </div>

    <div class="cursor-pager">
      <div class="cursor-pager-meta">
        <span>共 {{ total }} 位歌手</span>
        <span>{{ loadedRangeText }}</span>
        <span v-if="loadingMore">加载下一页...</span>
        <span v-else-if="!hasMoreRows">已加载全部</span>
      </div>
      <el-select
        v-model="query.pageSize"
        size="small"
        style="width: 96px"
        @change="handleSizeChange"
      >
        <el-option label="10 条" :value="10" />
        <el-option label="20 条" :value="20" />
        <el-option label="50 条" :value="50" />
        <el-option label="100 条" :value="100" />
      </el-select>
    </div>
  </el-card>
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
  margin-bottom: 12px;
}
.artist-view {
  height: calc(100vh - 318px);
  overflow-y: auto;
  padding-right: 4px;
}
.artist-card-grid {
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  gap: 14px;
}
.cursor-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding: 8px 10px;
  color: #606266;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(221, 234, 245, 0.9);
  border-radius: 6px;
}
.cursor-pager-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
}
@media (max-width: 1680px) {
  .artist-card-grid {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }
}
@media (max-width: 1360px) {
  .artist-card-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
@media (max-width: 1080px) {
  .artist-card-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .search-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .search-bar :deep(.el-input),
  .search-bar :deep(.el-select) {
    width: 100% !important;
    margin-left: 0 !important;
  }
  .artist-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 420px) {
  .artist-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
