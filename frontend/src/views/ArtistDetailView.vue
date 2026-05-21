<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const artistKey = computed(() => route.params.artistKey as string)
const artistName = computed(() => (route.query.name as string) || artistKey.value)

function goBack() {
  router.push('/artists')
}
</script>

<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <el-button :icon="ArrowLeft" text size="small" @click="goBack">
          返回歌手列表
        </el-button>
      </div>
    </template>

    <div class="detail-placeholder">
      <div class="artist-name">{{ artistName }}</div>
      <div class="artist-key">路由参数: {{ artistKey }}</div>
      <el-divider />
      <el-empty description="歌手详情将在 v0.8.2 实现" :image-size="120" />
      <div class="placeholder-hint">
        <p>计划功能：</p>
        <ul>
          <li>歌手歌曲列表</li>
          <li>专辑聚合展示</li>
          <li>歌词与封面统计详情</li>
          <li>元数据批量整理</li>
        </ul>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
}
.detail-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0;
}
.artist-name {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}
.artist-key {
  font-size: 13px;
  color: #909399;
  font-family: monospace;
  margin-bottom: 8px;
}
.placeholder-hint {
  margin-top: 16px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
.placeholder-hint ul {
  list-style: disc;
  padding-left: 20px;
  margin-top: 8px;
  text-align: left;
}
.placeholder-hint li {
  margin-bottom: 4px;
}
</style>
