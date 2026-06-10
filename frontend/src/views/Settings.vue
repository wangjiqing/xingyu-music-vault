<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Clock, Key } from '@element-plus/icons-vue'

const router = useRouter()

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '（默认空，使用 Vite 代理）'

function goToCredentials() {
  router.push('/openapi/credentials')
}
</script>

<template>
  <el-card>
    <template #header>
      <span>设置</span>
    </template>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
      v1.1.3 起 OpenAPI 使用 AK/SK + HMAC-SHA256 签名认证。旧静态 API Token 配置已移除。请前往「OpenAPI 凭证」页面管理客户端凭证。
    </el-alert>
    <el-descriptions title="连接信息" :column="1" border style="margin-bottom: 16px">
      <el-descriptions-item label="API 基础地址">
        {{ apiBaseUrl }}
      </el-descriptions-item>
    </el-descriptions>
    <el-divider />
    <div class="tools-section">
      <h4 style="margin-bottom: 12px; color: #303133">工具</h4>
      <el-button :icon="Key" type="primary" @click="goToCredentials">
        OpenAPI 凭证管理
      </el-button>
      <el-button :icon="Clock" @click="router.push('/metadata-audit')">
        元数据同步审计
      </el-button>
    </div>
  </el-card>
</template>
