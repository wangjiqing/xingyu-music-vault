<template>
  <div v-if="loading" class="app-loading">
    <div class="app-loading-spinner"></div>
    <p>加载中...</p>
  </div>
  <router-view v-else />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuth } from './composables/useAuth'

const { loading, init } = useAuth()

onMounted(() => {
  init()
})
</script>

<style scoped>
.app-loading {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #131526;
  color: #a0a4b8;
  gap: 16px;
}

.app-loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(142, 205, 248, 0.2);
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
