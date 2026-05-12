<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DataBoard,
  Headset,
  Mic,
  Picture,
  List,
  VideoPlay,
  Setting,
  Expand,
  Fold,
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isCollapsed = ref(false)

const activeMenu = computed(() => {
  if (route.path.startsWith('/tracks')) return '/tracks'
  return route.path
})

const menuItems = [
  { path: '/dashboard', title: '仪表盘', icon: DataBoard },
  { path: '/tracks', title: '歌曲列表', icon: Headset },
  { path: '/lyrics', title: '歌词管理', icon: Mic },
  { path: '/artwork', title: '封面管理', icon: Picture },
  { path: '/review', title: '待处理队列', icon: List },
  { path: '/scan-jobs', title: '扫描任务', icon: VideoPlay },
  { path: '/settings', title: '系统设置', icon: Setting },
]

function navigate(path: string) {
  router.push(path)
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<template>
  <el-container class="main-container">
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="main-aside">
      <div class="logo">
        <span v-if="!isCollapsed" class="logo-text">星语音库</span>
        <span v-else class="logo-text-small">星语</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        background-color="#1d1e2b"
        text-color="#a0a4b8"
        active-text-color="#409eff"
        @select="navigate"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="main-header">
        <div class="header-left">
          <el-button :icon="isCollapsed ? Expand : Fold" text @click="toggleCollapse" />
          <span class="header-title">Xingyu Music Vault / 星语音库</span>
        </div>
        <div class="header-right">
          <el-tag type="info" size="small">
            {{ route.meta.title || '首页' }}
          </el-tag>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-container {
  height: 100vh;
}
.main-aside {
  background-color: #1d1e2b;
  overflow: hidden;
  transition: width 0.2s;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #2d2e3b;
}
.logo-text-small {
  font-size: 14px;
}
.main-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.main-content {
  background: #f5f7fa;
  padding: 20px;
}
.el-menu {
  border-right: none;
}
</style>
