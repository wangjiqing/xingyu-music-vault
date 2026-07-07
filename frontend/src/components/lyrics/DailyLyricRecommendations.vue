<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { RefreshRight, VideoPlay, Switch } from '@element-plus/icons-vue'
import {
  fetchDailyLyricRecommendations,
  replaceDailyLyricRecommendation,
  skipDailyLyricRecommendation,
  startDailyLyricRecommendation,
  type DailyLyricRecommendation,
} from '../../api/lyricDashboard'
import { lyricStatusLabel, lyricStatusTagType } from '../../constants/musicStatus'

const router = useRouter()
const loading = ref(false)
const items = ref<DailyLyricRecommendation[]>([])

function recommendationTypeLabel(type: string) {
  return type === 'LRC_UPGRADE' ? '已有 LRC，建议升级逐字歌词' : '暂无歌词，可开始整理'
}

async function load() {
  loading.value = true
  try {
    const res = await fetchDailyLyricRecommendations()
    items.value = res.items
  } catch {
    ElMessage.error('加载每日歌词推荐失败')
  } finally {
    loading.value = false
  }
}

async function start(item: DailyLyricRecommendation) {
  try {
    await startDailyLyricRecommendation(item.id)
  } catch {
    // 跳转工作台优先，标记 STARTED 失败不阻断制作入口。
  }
  router.push({ path: '/music/workbench', query: { id: item.music.id } })
}

async function skip(item: DailyLyricRecommendation) {
  try {
    const res = await skipDailyLyricRecommendation(item.id)
    items.value = res.items
    ElMessage.success('今天已跳过')
  } catch {
    ElMessage.error('跳过失败')
  }
}

async function replace(item: DailyLyricRecommendation) {
  try {
    const created = await replaceDailyLyricRecommendation(item.id)
    const index = items.value.findIndex((entry) => entry.id === item.id)
    if (index !== -1) {
      items.value.splice(index, 1, created)
    } else {
      await load()
    }
    ElMessage.success('已换一首')
  } catch (error: any) {
    ElMessage.warning(error?.response?.data?.message || '暂无可替换候选')
  }
}

onMounted(load)
</script>

<template>
  <section class="daily-lyrics" v-loading="loading">
    <div class="section-title">
      <div>
        <h2>今日歌词待办</h2>
        <p>每天固定推荐最多 5 首缺少 SWLRC 的歌曲</p>
      </div>
      <el-button size="small" :icon="RefreshRight" @click="load">刷新</el-button>
    </div>

    <div v-if="items.length > 0" class="recommendation-grid">
      <article v-for="item in items" :key="item.id" class="recommendation-card">
        <div class="song-copy">
          <div class="song-title">{{ item.music.title || item.music.fileName }}</div>
          <div class="song-meta">{{ item.music.artist || 'Unknown' }}</div>
        </div>
        <div class="tag-row">
          <el-tag :type="lyricStatusTagType(item.music.lyricStatus)" size="small">
            {{ lyricStatusLabel(item.music.lyricStatus) }}
          </el-tag>
          <el-tag type="info" size="small">{{ recommendationTypeLabel(item.recommendationType) }}</el-tag>
        </div>
        <div class="card-actions">
          <el-button type="primary" size="small" :icon="VideoPlay" @click="start(item)">
            开始制作
          </el-button>
          <el-button size="small" @click="skip(item)">今天跳过</el-button>
          <el-button size="small" :icon="Switch" @click="replace(item)">换一首</el-button>
        </div>
      </article>
    </div>

    <el-empty v-else description="今天暂无可推荐的歌词待办" :image-size="110" />
  </section>
</template>

<style scoped>
.daily-lyrics {
  padding: 16px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(221, 234, 245, 0.92);
  border-radius: 8px;
}
.section-title,
.card-actions,
.tag-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-title {
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title h2 {
  margin: 0;
  font-size: 16px;
}
.section-title p {
  margin: 4px 0 0;
  color: var(--xy-text-secondary, #6e8198);
  font-size: 12px;
}
.recommendation-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 10px;
}
.recommendation-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(221, 234, 245, 0.92);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}
.song-title {
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.song-meta {
  margin-top: 3px;
  color: var(--xy-text-secondary, #6e8198);
  font-size: 12px;
}
.tag-row {
  flex-wrap: wrap;
}
.card-actions {
  flex-wrap: wrap;
}
</style>
