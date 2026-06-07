import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { existsSync, readdirSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

function excludeThemeArchiveFromDist() {
  return {
    name: 'exclude-theme-archive-from-dist',
    closeBundle() {
      const themesRoot = resolve(__dirname, 'dist/themes')
      if (!existsSync(themesRoot)) return
      for (const entry of readdirSync(themesRoot, { withFileTypes: true })) {
        if (!entry.isDirectory()) continue
        rmSync(resolve(themesRoot, entry.name, 'archive'), {
          force: true,
          recursive: true,
        })
      }
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
