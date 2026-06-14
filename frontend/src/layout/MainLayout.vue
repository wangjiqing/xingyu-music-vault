<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DataBoard,
  Headset,
  Mic,
  Picture,
  Setting,
  Expand,
  Fold,
  User,
  Collection,
  Connection,
  Key,
  ArrowRight,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchAdminServerInfo } from '../api/serverInfo'
import {
  activeThemeId,
  availableThemes,
  currentThemeAssets,
  fetchCurrentTheme,
  setCurrentThemeId,
  type CurrentThemeConfig,
} from '../theme/currentTheme'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const route = useRoute()
const { user, doLogout } = useAuth()

const isCollapsed = ref(false)

const activeMenu = computed(() => route.path)
const serviceVersion = ref('')
const currentTheme = ref<CurrentThemeConfig | null>(null)
const themeStyleVars = computed(() => ({
  '--xy-aside-background-image': `url("${currentThemeAssets.value.backgroundMobile}")`,
  '--xy-banner-background-image': `url("${currentThemeAssets.value.banner}")`,
  '--xy-content-background-image': `url("${currentThemeAssets.value.backgroundDesktop}")`,
}))

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function formatVersion(version: string): string {
  const normalized = version.trim()
  if (!normalized) return ''
  return normalized.startsWith('v') ? normalized : `v${normalized}`
}

const footerText = computed(() => {
  const parts = ['星语音库', 'Xingyu Music Vault']
  const versionLabel = formatVersion(serviceVersion.value)
  if (versionLabel) {
    parts.push(versionLabel)
  }
  if (currentTheme.value) {
    const themeNames = [currentTheme.value.name, currentTheme.value.englishName].filter(Boolean)
    if (themeNames.length > 0) {
      parts.push(`当前主题：${themeNames.join(' / ')}`)
    }
  }
  parts.push('Apache License 2.0')
  return parts.join(' · ')
})

const menuItems = [
  { path: '/dashboard', title: '首页', icon: DataBoard },
  { path: '/music', title: '全部歌曲', icon: Headset },
  { path: '/music/workbench', title: '歌曲工作台', icon: VideoPlay },
  { path: '/artists', title: '歌手', icon: User },
  { path: '/albums', title: '专辑', icon: Collection },
  { path: '/lyrics', title: '歌词', icon: Mic },
  { path: '/artwork', title: '封面', icon: Picture },
  { path: '/openapi', title: 'OpenAPI', icon: Connection },
  { path: '/openapi/credentials', title: 'OpenAPI 凭证', icon: Key },
  { path: '/settings', title: '设置', icon: Setting },
]

function navigate(path: string) {
  router.push(path)
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

async function loadServiceVersion() {
  try {
    const info = await fetchAdminServerInfo()
    serviceVersion.value = normalizeText(info.serviceVersion)
  } catch {
    serviceVersion.value = ''
  }
}

async function loadCurrentTheme() {
  try {
    currentTheme.value = await fetchCurrentTheme(activeThemeId.value)
  } catch {
    currentTheme.value = null
  }
}

function handleThemeChange(themeId: string) {
  setCurrentThemeId(themeId)
  loadCurrentTheme()
}

async function handleLogout() {
  try {
    await doLogout()
    ElMessage.success('已登出')
  } catch {
    ElMessage.warning('登出请求失败，但本地状态已清除')
  }
  router.push('/login')
}

onMounted(() => {
  loadServiceVersion()
  loadCurrentTheme()
})
</script>

<template>
  <el-container class="main-container" :style="themeStyleVars">
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="main-aside">
      <div class="aside-top">
        <div class="logo">
          <img
            class="logo-mark"
            :src="currentThemeAssets.logoMark"
            alt=""
            aria-hidden="true"
          />
          <span v-if="!isCollapsed" class="logo-text">星语音库</span>
        </div>
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapsed"
          :collapse-transition="false"
          background-color="transparent"
          text-color="#a0a4b8"
          active-text-color="#409eff"
          @select="navigate"
        >
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-menu>
      </div>
      <div v-if="user" class="aside-bottom">
        <el-dropdown trigger="click" placement="top-start">
          <div class="aside-user">
            <el-icon><User /></el-icon>
            <span v-if="!isCollapsed" class="aside-user-name">{{ user.username }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                {{ user.username }}
                <el-tag size="small" style="margin-left: 8px">{{ user.role }}</el-tag>
              </el-dropdown-item>
              <el-dropdown-item divided :icon="ArrowRight" @click="handleLogout">
                登出
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-aside>
    <el-container>
      <el-header class="main-header">
        <div class="header-left">
          <el-button :icon="isCollapsed ? Expand : Fold" text @click="toggleCollapse" />
          <span class="header-title">Xingyu Music Vault / 星语音库</span>
        </div>
        <div class="header-right">
          <el-select
            :model-value="activeThemeId"
            size="small"
            class="theme-select"
            aria-label="当前主题"
            @change="handleThemeChange"
          >
            <el-option
              v-for="theme in availableThemes"
              :key="theme.id"
              :label="`${theme.name} / ${theme.englishName}`"
              :value="theme.id"
            />
          </el-select>
          <el-tag type="info" size="small">
            {{ route.meta.title || '概览' }}
          </el-tag>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
      <el-footer class="main-footer">
        <span>{{ footerText }}</span>
      </el-footer>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-container {
  height: 100vh;
}
.main-aside {
  position: relative;
  display: flex;
  flex-direction: column;
  background:
    linear-gradient(180deg, rgba(19, 21, 38, 0.78), rgba(19, 21, 38, 0.93)),
    url('/themes/midsummer-starlight/background/background-mobile.webp') center bottom / cover;
  background:
    linear-gradient(180deg, rgba(19, 21, 38, 0.78), rgba(19, 21, 38, 0.93)),
    var(--xy-aside-background-image) center bottom / cover;
  overflow: hidden;
  transition: width 0.2s;
}
.aside-top {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.aside-bottom {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  border-top: 1px solid #2d2e3b;
  padding: 8px;
}
.aside-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #a0a4b8;
  font-size: 14px;
  transition: background 0.2s;
}
.aside-user:hover {
  background: rgba(142, 205, 248, 0.12);
  color: #c0c4d0;
}
.aside-user-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.main-aside::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 50% 18%, rgba(142, 205, 248, 0.18), transparent 36%),
    linear-gradient(90deg, rgba(12, 14, 26, 0.32), rgba(12, 14, 26, 0.08));
}
.logo {
  position: relative;
  z-index: 1;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #2d2e3b;
}
.logo-mark {
  width: 24px;
  height: 24px;
  object-fit: contain;
  filter: drop-shadow(0 0 8px rgba(142, 205, 248, 0.32));
}
.main-header {
  position: relative;
  overflow: hidden;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.82)),
    url('/themes/midsummer-starlight/banner/readme-banner.webp') center 44% / cover;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.82)),
    var(--xy-banner-background-image) center 44% / cover;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(221, 234, 245, 0.92);
  box-shadow: 0 2px 12px rgba(38, 56, 77, 0.05);
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.theme-select {
  width: 214px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.main-content {
  position: relative;
  isolation: isolate;
  overflow: auto;
  background: #f5f7fa;
  padding: 20px;
}
.main-content::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -2;
  background: url('/themes/midsummer-starlight/background/background-desktop.webp') center bottom / cover;
  background: var(--xy-content-background-image) center bottom / cover;
  opacity: 0.44;
}
.main-content::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: rgba(246, 251, 255, 0);
}
.main-footer {
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 0 20px;
  font-size: 12px;
  color: rgba(38, 56, 77, 0.72);
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.76)),
    url('/themes/midsummer-starlight/banner/readme-banner.webp') center 72% / cover;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.76)),
    var(--xy-banner-background-image) center 72% / cover;
  border-top: 1px solid rgba(221, 234, 245, 0.9);
}
.footer-divider {
  width: 1px;
  height: 12px;
  background: rgba(110, 129, 152, 0.28);
}
.el-menu {
  position: relative;
  z-index: 1;
  border-right: none;
}
.el-menu :deep(.el-menu-item) {
  background-color: transparent;
}
.el-menu :deep(.el-menu-item:hover) {
  background-color: rgba(142, 205, 248, 0.12);
}
.el-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(142, 205, 248, 0.16);
}
</style>
