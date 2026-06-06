<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Collection, DataAnalysis, Delete, Headset, MagicStick } from '@element-plus/icons-vue'
import { fetchMusicStats, type MusicStats } from '../api/music'
import { fetchCurrentTheme, type CurrentThemeConfig } from '../theme/currentTheme'

const quickEntries = [
  {
    title: '全部歌曲',
    description: '浏览、筛选和整理音乐库',
    path: '/music',
    image: '/themes/midsummer-starlight/empty-states/empty-songs.png',
  },
  {
    title: '歌手',
    description: '按歌手聚合音乐、专辑和整理状态',
    path: '/artists',
    image: '/themes/midsummer-starlight/empty-states/empty-artists.png',
  },
  {
    title: '专辑',
    description: '按专辑查看曲目、封面和歌词覆盖情况',
    path: '/albums',
    image: '/themes/midsummer-starlight/empty-states/empty-albums.png',
  },
  {
    title: '歌词',
    description: '扫描和查看歌词绑定状态',
    path: '/lyrics',
    image: '/themes/midsummer-starlight/empty-states/empty-lyrics.png',
  },
  {
    title: '封面',
    description: '管理封面素材和绑定关系',
    path: '/artwork',
    image: '/themes/midsummer-starlight/empty-states/empty-cover.png',
  },
  {
    title: '设置',
    description: '配置连接和访问令牌',
    path: '/settings',
    image: '/themes/midsummer-starlight/empty-states/metadata-pending.png',
  },
]

const stats = ref<MusicStats | null>(null)
const statsLoading = ref(false)
const statsError = ref(false)
const currentTheme = ref<CurrentThemeConfig | null>(null)

const heroSubtitle = computed(() => {
  const parts = ['Xingyu Music Vault']
  if (currentTheme.value?.englishName) {
    parts.push(currentTheme.value.englishName)
  }
  return parts.join(' · ')
})

const statCards = computed(() => [
  {
    label: '音乐总数',
    value: stats.value?.total,
    color: '#26384d',
    icon: Headset,
  },
  {
    label: '待整理',
    value: stats.value?.metadataIncomplete,
    color: '#e6a23c',
    icon: MagicStick,
  },
  {
    label: '有歌词',
    value: stats.value?.lyricsReady,
    color: '#67c23a',
    icon: DataAnalysis,
  },
  {
    label: '有封面',
    value: stats.value?.artworkReady,
    color: '#409eff',
    icon: Collection,
  },
  {
    label: '回收站',
    value: stats.value?.trashed,
    color: '#909399',
    icon: Delete,
  },
])

async function loadStats() {
  statsLoading.value = true
  statsError.value = false
  try {
    stats.value = await fetchMusicStats()
  } catch {
    statsError.value = true
  } finally {
    statsLoading.value = false
  }
}

function formatStatValue(value: number | undefined): string {
  if (value == null) return '--'
  return new Intl.NumberFormat('zh-CN').format(value)
}

onMounted(() => {
  loadStats()
  fetchCurrentTheme()
    .then((theme) => {
      currentTheme.value = theme
    })
    .catch(() => {
      currentTheme.value = null
    })
})
</script>

<template>
  <div class="dashboard-page">
    <section class="hero-panel">
      <div class="hero-copy">
        <img
          src="/themes/midsummer-starlight/logo/logo-horizontal.png"
          alt="星语音库"
          class="hero-logo"
        />
        <div class="hero-title">星语音库</div>
        <div class="hero-subtitle">{{ heroSubtitle }}</div>
      </div>
      <img
        src="/themes/midsummer-starlight/empty-states/empty-home.png"
        alt=""
        class="hero-illustration"
        aria-hidden="true"
      />
    </section>

    <section class="stats-grid" v-loading="statsLoading">
      <div
        v-for="card in statCards"
        :key="card.label"
        class="stat-card"
      >
        <div class="stat-icon" :style="{ color: card.color, backgroundColor: `${card.color}14` }">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-value" :style="{ color: card.color }">
          {{ formatStatValue(card.value) }}
        </div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </section>

    <el-alert
      v-if="statsError"
      title="音乐库统计加载失败"
      type="warning"
      show-icon
      closable
      class="stats-alert"
      @close="statsError = false"
    />

    <div class="home-grid">
      <router-link
        v-for="entry in quickEntries"
        :key="entry.path"
        :to="entry.path"
        class="home-entry"
      >
        <div class="home-entry-art">
          <img :src="entry.image" alt="" aria-hidden="true" />
        </div>
        <div class="home-entry-copy">
          <div class="home-entry-title">{{ entry.title }}</div>
          <div class="home-entry-description">{{ entry.description }}</div>
        </div>
        <div class="home-entry-action">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: calc(100vh - 126px);
}
.hero-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 220px;
  padding: 28px 34px;
  overflow: hidden;
  background:
    linear-gradient(90deg, rgba(246, 251, 255, 0.95), rgba(246, 251, 255, 0.7)),
    url('/themes/midsummer-starlight/banner/readme-banner.webp') center / cover;
  border: 1px solid rgba(221, 234, 245, 0.88);
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(38, 56, 77, 0.1);
}
.hero-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}
.hero-logo {
  width: 164px;
  max-width: 44vw;
  height: auto;
  object-fit: contain;
  margin-bottom: 16px;
}
.hero-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--xy-text-primary, #26384d);
}
.hero-subtitle {
  margin-top: 8px;
  font-size: 14px;
  color: var(--xy-text-secondary, #6e8198);
}
.hero-illustration {
  position: absolute;
  right: 34px;
  bottom: 0;
  width: min(300px, 30vw);
  max-height: 210px;
  object-fit: contain;
  opacity: 0.92;
  filter: drop-shadow(0 16px 24px rgba(38, 56, 77, 0.16));
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}
.stat-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  grid-template-areas:
    'icon value'
    'icon label';
  align-items: center;
  min-height: 72px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(221, 234, 245, 0.92);
  border-radius: 8px;
}
.stat-icon {
  grid-area: icon;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  font-size: 18px;
}
.stat-value {
  grid-area: value;
  min-width: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
}
.stat-label {
  grid-area: label;
  margin-top: 5px;
  color: var(--xy-text-secondary, #6e8198);
  font-size: 12px;
}
.stats-alert {
  flex: 0 0 auto;
}
.home-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}
.home-entry {
  position: relative;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 36px;
  align-items: center;
  min-height: 94px;
  padding: 12px 16px 12px 12px;
  color: inherit;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(221, 234, 245, 0.92);
  border-radius: 8px;
  overflow: hidden;
  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s,
    background-color 0.2s;
}
.home-entry::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(105deg, transparent 0%, rgba(142, 205, 248, 0.14) 42%, transparent 70%);
  opacity: 0;
  transform: translateX(-30%);
  transition:
    opacity 0.2s,
    transform 0.3s;
}
.home-entry:hover {
  transform: translateY(-2px);
  background: rgba(255, 255, 255, 0.92);
  border-color: var(--xy-primary, #409eff);
  box-shadow: 0 10px 26px rgba(142, 205, 248, 0.28);
}
.home-entry:hover::after {
  opacity: 1;
  transform: translateX(30%);
}
.home-entry:hover .home-entry-art img {
  transform: scale(1.08) rotate(-2deg);
}
.home-entry:hover .home-entry-action {
  transform: translateX(4px);
  color: var(--xy-primary, #409eff);
}
.home-entry-art {
  position: relative;
  z-index: 1;
  width: 58px;
  height: 58px;
  border-radius: 8px;
  overflow: hidden;
  background: rgba(246, 251, 255, 0.86);
  border: 1px solid rgba(221, 234, 245, 0.9);
}
.home-entry-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.24s;
}
.home-entry-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}
.home-entry-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--xy-text-primary, #303133);
}
.home-entry-description {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--xy-text-secondary, #606266);
}
.home-entry-action {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8a9ab0;
  font-size: 18px;
  transition:
    color 0.2s,
    transform 0.2s;
}
@media (max-width: 1180px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .hero-illustration {
    opacity: 0.5;
  }
}
@media (max-width: 760px) {
  .dashboard-page {
    min-height: auto;
  }
  .hero-panel {
    min-height: 190px;
    padding: 22px;
  }
  .stats-grid,
  .home-grid {
    grid-template-columns: 1fr;
  }
  .home-entry {
    grid-template-columns: 64px minmax(0, 1fr) 28px;
  }
}
</style>
