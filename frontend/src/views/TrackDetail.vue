<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
const trackId = route.params.id
</script>

<template>
  <el-card>
    <template #header>
      <span>歌曲详情 #{{ trackId }}</span>
    </template>
    <el-empty description="歌曲详情功能开发中" />
  </el-card>
</template>
