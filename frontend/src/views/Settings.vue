<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const token = ref('')
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '（默认空，使用 Vite 代理）'

onMounted(() => {
  token.value = localStorage.getItem('apiToken') || ''
})

function saveToken() {
  if (!token.value.trim()) {
    ElMessage.warning('请输入 API Token')
    return
  }
  localStorage.setItem('apiToken', token.value.trim())
  ElMessage.success('API Token 已保存')
}

function clearToken() {
  localStorage.removeItem('apiToken')
  token.value = ''
  ElMessage.success('API Token 已清除')
}
</script>

<template>
  <el-card>
    <template #header>
      <span>系统设置</span>
    </template>
    <el-form label-width="120px" style="max-width: 600px">
      <el-form-item label="API Token">
        <el-input
          v-model="token"
          type="password"
          show-password
          placeholder="请输入 API Token"
          clearable
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveToken">保存</el-button>
        <el-button @click="clearToken">清除</el-button>
      </el-form-item>
    </el-form>
    <el-divider />
    <el-descriptions title="连接信息" :column="1" border>
      <el-descriptions-item label="API 基础地址">
        {{ apiBaseUrl }}
      </el-descriptions-item>
      <el-descriptions-item label="Token 状态">
        <el-tag :type="token ? 'success' : 'info'">
          {{ token ? '已配置' : '未配置' }}
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>
