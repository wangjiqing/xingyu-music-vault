import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { useAuth } from '../composables/useAuth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/setup',
      name: 'Setup',
      component: () => import('../views/SetupView.vue'),
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/LoginView.vue'),
    },
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
          path: 'music/workbench',
          name: 'SongWorkbench',
          component: () => import('../views/SongWorkbenchView.vue'),
          meta: { title: '歌曲工作台' },
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
          path: 'albums/detail',
          name: 'AlbumDetail',
          component: () => import('../views/AlbumDetailView.vue'),
          meta: { title: '专辑详情', hidden: true },
        },
        {
          path: 'lyrics',
          name: 'LyricsManage',
          component: () => import('../views/LyricsManage.vue'),
          meta: { title: '歌词' },
        },
        {
          path: 'lyric-alignment',
          name: 'LyricAlignmentJobs',
          component: () => import('../views/LyricAlignmentJobsView.vue'),
          meta: { title: '歌词对齐' },
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
        {
          path: 'metadata-audit',
          name: 'MetadataAudit',
          component: () => import('../views/MetadataAuditView.vue'),
          meta: { title: '元数据审计', hidden: true },
        },
        {
          path: 'openapi',
          name: 'OpenApi',
          component: () => import('../views/OpenApiView.vue'),
          meta: { title: 'OpenAPI' },
        },
        {
          path: 'openapi/credentials',
          name: 'OpenApiCredentials',
          component: () => import('../views/OpenApiCredentialsView.vue'),
          meta: { title: 'OpenAPI 凭证', hidden: false },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const { initialized, isLoggedIn, init } = useAuth()

  await init()

  if (to.path === '/setup') {
    if (initialized.value && isLoggedIn.value) {
      return '/'
    }
    if (initialized.value) {
      return '/login'
    }
    return true
  }

  if (to.path === '/login') {
    if (isLoggedIn.value) {
      return '/'
    }
    if (initialized.value === false) {
      return '/setup'
    }
    return true
  }

  if (initialized.value === false) {
    return '/setup'
  }

  if (!isLoggedIn.value) {
    return '/login'
  }

  return true
})

export default router
