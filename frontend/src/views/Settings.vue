<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Key, Search } from '@element-plus/icons-vue'
import {
  fetchBraveSearchStatus,
  saveBraveSearchKey,
  setBraveSearchEnabled,
  testBraveSearchConnection,
  type BraveSearchStatus,
} from '../api/braveSearch'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { user } = useAuth()

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '（默认空，使用 Vite 代理）'
const braveStatus = ref<BraveSearchStatus | null>(null)
const braveKey = ref('')
const braveLoading = ref(false)
const braveEnvMode = computed(() => braveStatus.value?.mode === 'ENV')

function goToCredentials() {
  router.push('/openapi/credentials')
}

function operator(): string {
  return user.value?.username || 'admin'
}

function errorText(error: any, fallback: string): string {
  const message = error?.response?.data?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

async function loadBraveStatus() {
  braveLoading.value = true
  try {
    braveStatus.value = await fetchBraveSearchStatus()
  } catch (error: any) {
    ElMessage.error(errorText(error, '读取 Brave 配置状态失败'))
  } finally {
    braveLoading.value = false
  }
}

async function handleSaveBraveKey() {
  braveLoading.value = true
  try {
    braveStatus.value = await saveBraveSearchKey({ apiKey: braveKey.value, updatedBy: operator() })
    braveKey.value = ''
    ElMessage.success('Brave Search API Key 已保存')
  } catch (error: any) {
    ElMessage.error(errorText(error, '保存 Brave Search API Key 失败'))
  } finally {
    braveLoading.value = false
  }
}

async function handleSetBraveEnabled(enabled: boolean) {
  braveLoading.value = true
  try {
    braveStatus.value = await setBraveSearchEnabled({ enabled, updatedBy: operator() })
    ElMessage.success(enabled ? 'Brave 搜索已启用' : 'Brave 搜索已暂停')
  } catch (error: any) {
    ElMessage.error(errorText(error, '更新 Brave 搜索状态失败'))
  } finally {
    braveLoading.value = false
  }
}

async function handleTestBrave() {
  braveLoading.value = true
  try {
    braveStatus.value = await testBraveSearchConnection()
    ElMessage.success('Brave Search 连接测试通过')
  } catch (error: any) {
    ElMessage.error(errorText(error, 'Brave Search 连接测试失败'))
    await loadBraveStatus()
  } finally {
    braveLoading.value = false
  }
}

onMounted(loadBraveStatus)
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
    <el-descriptions title="Brave Search" :column="1" border style="margin-bottom: 16px">
      <el-descriptions-item label="配置状态">
        {{ braveStatus?.message || '读取中' }}
      </el-descriptions-item>
      <el-descriptions-item label="Key 来源">
        {{ braveStatus?.mode || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="可搜索">
        {{ braveStatus?.searchable ? '是' : '否' }}
      </el-descriptions-item>
      <el-descriptions-item label="加密存储">
        {{ braveStatus?.encryptionAvailable ? '可用' : '未配置 MUSIC_VAULT_SETTINGS_ENCRYPTION_KEY' }}
      </el-descriptions-item>
      <el-descriptions-item v-if="braveStatus?.lastError" label="最近错误">
        {{ braveStatus.lastError }}
      </el-descriptions-item>
    </el-descriptions>
    <div class="brave-key-row">
      <el-input
        v-model="braveKey"
        type="password"
        show-password
        placeholder="保存或替换 Brave Search API Key"
        :disabled="braveStatus?.mode === 'ENV'"
      />
      <el-button
        type="primary"
        :icon="Search"
        :loading="braveLoading"
        :disabled="!braveKey || braveStatus?.mode === 'ENV'"
        @click="handleSaveBraveKey"
      >
        保存 Key
      </el-button>
      <el-tooltip content="环境变量接管中，请在部署配置中暂停" :disabled="!braveEnvMode">
        <span>
          <el-button :loading="braveLoading" :disabled="braveEnvMode" @click="handleSetBraveEnabled(false)">暂停</el-button>
        </span>
      </el-tooltip>
      <el-tooltip content="环境变量接管中，请在部署配置中启用" :disabled="!braveEnvMode">
        <span>
          <el-button :loading="braveLoading" :disabled="braveEnvMode" @click="handleSetBraveEnabled(true)">启用</el-button>
        </span>
      </el-tooltip>
      <el-button :loading="braveLoading" @click="handleTestBrave">测试连接</el-button>
    </div>
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

<style scoped>
.brave-key-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
.brave-key-row .el-input {
  max-width: 420px;
}
</style>
