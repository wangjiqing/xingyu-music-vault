<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const route = useRoute()
const { doLogin, loginError } = useAuth()

const username = ref('')
const password = ref('')
const submitting = ref(false)

async function handleLogin() {
  if (!username.value.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }
  if (submitting.value) return

  submitting.value = true
  try {
    const ok = await doLogin(username.value.trim(), password.value)
    if (ok) {
      const redirect = (route.query.redirect as string) || '/'
      router.push(redirect)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">星语音库</h1>
        <p class="login-subtitle">Xingyu Music Vault</p>
      </div>

      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="用户名">
          <el-input
            v-model="username"
            placeholder="请输入用户名"
            :disabled="submitting"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="请输入密码"
            :disabled="submitting"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item v-if="loginError">
          <el-alert :title="loginError" type="error" show-icon :closable="false" />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="submitting"
            style="width: 100%"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #131526 0%, #1a1d33 50%, #1f2347 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  padding: 40px 36px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 2px;
}

.login-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
  letter-spacing: 1px;
}
</style>
