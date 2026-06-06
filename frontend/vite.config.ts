import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { rmSync } from 'node:fs'
import { resolve } from 'node:path'

function excludeThemeArchiveFromDist() {
  return {
    name: 'exclude-theme-archive-from-dist',
    closeBundle() {
      rmSync(resolve(__dirname, 'dist/themes/midsummer-starlight/archive'), {
        force: true,
        recursive: true,
      })
    },
  }
}

export default defineConfig({
  plugins: [vue(), excludeThemeArchiveFromDist()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/q': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/vue/') || id.includes('node_modules/vue-router/')) {
            return 'vue-vendor'
          }
          if (id.includes('node_modules/element-plus/')) {
            return 'element-plus'
          }
        },
      },
    },
  },
})
