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
          meta: { title: '首页' },
        },
        {
          path: 'music',
          name: 'Music',
          component: () => import('../views/MusicListView.vue'),
          meta: { title: '全部歌曲' },
        },
        {
          path: 'artists',
          name: 'Artists',
          component: () => import('../views/ArtistsView.vue'),
          meta: { title: '歌手' },
        },
        {
          path: 'artists/:artistKey',
          name: 'ArtistDetail',
          component: () => import('../views/ArtistDetailView.vue'),
          meta: { title: '歌手详情', hidden: true },
        },
        {
          path: 'albums',
          name: 'Albums',
          component: () => import('../views/AlbumsView.vue'),
          meta: { title: '专辑' },
        },
        {
          path: 'lyrics',
          name: 'LyricsManage',
          component: () => import('../views/LyricsManage.vue'),
          meta: { title: '歌词' },
        },
        {
          path: 'artwork',
          name: 'ArtworkManage',
          component: () => import('../views/ArtworkManage.vue'),
          meta: { title: '封面' },
        },
        {
          path: 'review',
          name: 'ReviewQueue',
          component: () => import('../views/ReviewQueue.vue'),
          meta: { title: '待处理队列', hidden: true },
        },
        {
          path: 'scan-jobs',
          name: 'ScanJobs',
          component: () => import('../views/ScanJobs.vue'),
          meta: { title: '扫描任务', hidden: true },
        },
        {
          path: 'track-files',
          name: 'TrackFiles',
          component: () => import('../views/TrackFilesView.vue'),
          meta: { title: '音乐文件', hidden: true },
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('../views/Settings.vue'),
          meta: { title: '设置' },
        },
      ],
    },
  ],
})

export default router
