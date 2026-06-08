<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const { setup, setupError } = useAuth()

const username = ref('admin')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)

async function handleSetup() {
  if (!username.value.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }
  if (password.value.length < 8) {
    ElMessage.warning('密码至少需要 8 位')
    return
  }
  if (password.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (submitting.value) return

  submitting.value = true
  try {
    const ok = await setup(username.value.trim(), password.value)
    if (ok) {
      ElMessage.success('管理员账号创建成功')
      router.push('/login')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="setup-page">
    <div class="setup-card">
      <div class="setup-header">
        <h1 class="setup-title">星语音库</h1>
        <p class="setup-subtitle">Xingyu Music Vault</p>
      </div>

      <div class="setup-notice">
        <p>首次使用需要创建管理员账号。</p>
        <ul>
          <li>当前仅支持单一管理员账号</li>
          <li>初始化完成后无法再次注册或开放注册</li>
          <li>请妥善保管管理员密码，当前暂不支持密码重置</li>
        </ul>
      </div>

      <el-form label-position="top" @submit.prevent="handleSetup">
        <el-form-item label="用户名">
          <el-input
            v-model="username"
            placeholder="请输入管理员用户名"
            :disabled="submitting"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="请输入密码（至少 8 位）"
            :disabled="submitting"
          />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input
            v-model="confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入密码"
            :disabled="submitting"
            @keyup.enter="handleSetup"
          />
        </el-form-item>

        <el-form-item v-if="setupError">
          <el-alert :title="setupError" type="error" show-icon :closable="false" />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="submitting"
            style="width: 100%"
            @click="handleSetup"
          >
            创建管理员账号
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.setup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #131526 0%, #1a1d33 50%, #1f2347 100%);
  padding: 20px;
}

.setup-card {
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  padding: 40px 36px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.3);
}

.setup-header {
  text-align: center;
  margin-bottom: 28px;
}

.setup-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 2px;
}

.setup-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
  letter-spacing: 1px;
}

.setup-notice {
  margin-bottom: 24px;
  padding: 14px 16px;
  background: #fdf6ec;
  border-radius: 8px;
  border: 1px solid #faecd8;
}

.setup-notice p {
  margin: 0 0 8px;
  font-size: 14px;
  color: #e6a23c;
  font-weight: 500;
}

.setup-notice ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #909399;
  line-height: 1.8;
}
</style>
