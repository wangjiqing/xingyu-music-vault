import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('../views/Dashboard.vue'),
          meta: { title: '仪表盘' },
        },
        {
          path: 'music',
          name: 'MusicList',
          component: () => import('../views/MusicListView.vue'),
          meta: { title: '音乐库' },
        },
        {
          path: 'lyrics',
          name: 'LyricsManage',
          component: () => import('../views/LyricsManage.vue'),
          meta: { title: '歌词管理' },
        },
        {
          path: 'artwork',
          name: 'ArtworkManage',
          component: () => import('../views/ArtworkManage.vue'),
          meta: { title: '封面管理' },
        },
        {
          path: 'review',
          name: 'ReviewQueue',
          component: () => import('../views/ReviewQueue.vue'),
          meta: { title: '待处理队列' },
        },
        {
          path: 'scan-jobs',
          name: 'ScanJobs',
          component: () => import('../views/ScanJobs.vue'),
          meta: { title: '扫描任务' },
        },
        {
          path: 'track-files',
          name: 'TrackFiles',
          component: () => import('../views/TrackFilesView.vue'),
          meta: { title: '音乐文件' },
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('../views/Settings.vue'),
          meta: { title: '系统设置' },
        },
      ],
    },
  ],
})

export default router
