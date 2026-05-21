<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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

const sortOptions = [
  { label: '歌曲数 ↓', value: 'trackCountDesc' },
  { label: '名称 A-Z', value: 'nameAsc' },
  { label: '专辑数 ↓', value: 'albumCountDesc' },
  { label: '待整理 ↓', value: 'metadataIncompleteDesc' },
]

async function loadList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await fetchArtistList({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      sort: query.sort,
    })
    list.value = res.items
    total.value = res.total
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

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
}

function handleArtistClick(item: ArtistItem) {
  router.push({
    path: `/artists/${encodeURIComponent(item.artistKey)}`,
    query: { name: item.artist || item.artistKey },
  })
}

function emptyText(): string {
  return query.keyword ? '没有匹配的歌手' : '暂无歌手数据，请先扫描音乐目录'
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

    <div v-loading="loading" class="artist-view">
      <div v-if="list.length > 0" class="artist-card-grid">
        <ArtistCard
          v-for="item in list"
          :key="item.artistKey"
          :item="item"
          @click="handleArtistClick"
        />
      </div>
      <el-empty v-else :description="emptyText()" />
    </div>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
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
  min-height: 240px;
}
.artist-card-grid {
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  gap: 14px;
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
