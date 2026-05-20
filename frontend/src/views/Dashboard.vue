<script setup lang="ts">
const quickEntries = [
  { title: '全部歌曲', description: '浏览、筛选和整理音乐库', path: '/music' },
  { title: '歌手', description: '歌手浏览入口，v0.8.1 完善', path: '/artists' },
  { title: '专辑', description: '专辑浏览入口，v0.8.3 完善', path: '/albums' },
  { title: '歌词', description: '扫描和查看歌词绑定状态', path: '/lyrics' },
  { title: '封面', description: '管理封面素材和绑定关系', path: '/artwork' },
  { title: '设置', description: '配置连接和访问令牌', path: '/settings' },
]
</script>

<template>
  <el-card>
    <template #header>
      <span>首页</span>
    </template>
    <div class="home-grid">
      <router-link
        v-for="entry in quickEntries"
        :key="entry.path"
        :to="entry.path"
        class="home-entry"
      >
        <div class="home-entry-title">{{ entry.title }}</div>
        <div class="home-entry-description">{{ entry.description }}</div>
      </router-link>
    </div>
  </el-card>
</template>

<style scoped>
.home-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.home-entry {
  display: block;
  min-height: 92px;
  padding: 16px;
  color: inherit;
  text-decoration: none;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.home-entry:hover {
  border-color: #409eff;
  box-shadow: 0 6px 18px rgba(64, 158, 255, 0.12);
}
.home-entry-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.home-entry-description {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.5;
  color: #606266;
}
</style>
