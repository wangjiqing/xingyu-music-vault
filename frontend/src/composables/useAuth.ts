import { ref, computed } from 'vue'
import {
  getSetupStatus,
  getCurrentUser,
  login as loginApi,
  logout as logoutApi,
  setupAdminAccount,
  type AdminUserResponse,
} from '../api/auth'

const initialized = ref<boolean | null>(null)
const user = ref<AdminUserResponse | null>(null)
const loading = ref(true)
const loginError = ref('')
const setupError = ref('')

let initPromise: Promise<void> | null = null

export function useAuth() {
  const isLoggedIn = computed(() => user.value !== null)

  async function checkSetupStatus() {
    try {
      const res = await getSetupStatus()
      initialized.value = res.initialized
    } catch {
      initialized.value = true
    }
  }

  async function checkLoginStatus() {
    try {
      const res = await getCurrentUser()
      user.value = res
    } catch {
      user.value = null
    }
  }

  async function init() {
    if (initPromise) return initPromise
    initPromise = (async () => {
      loading.value = true
      await checkSetupStatus()
      if (initialized.value) {
        await checkLoginStatus()
      }
      loading.value = false
    })()
    return initPromise
  }

  async function setup(username: string, password: string) {
    setupError.value = ''
    try {
      await setupAdminAccount({ username, password })
      initialized.value = true
      return true
    } catch (e: any) {
      const message = e.response?.data?.message
      setupError.value = typeof message === 'string' ? message : '初始化失败，请稍后重试'
      return false
    }
  }

  async function doLogin(username: string, password: string) {
    loginError.value = ''
    try {
      await loginApi({ username, password })
      await checkLoginStatus()
      return true
    } catch (e: any) {
      if (e.response?.status === 401) {
        loginError.value = '用户名或密码错误'
      } else if (e.response?.status) {
        const message = e.response?.data?.message
        loginError.value = typeof message === 'string' ? message : '登录失败'
      } else {
        loginError.value = '登录失败，请检查网络连接'
      }
      return false
    }
  }

  async function doLogout() {
    try {
      await logoutApi()
    } finally {
      user.value = null
    }
  }

  function clearUser() {
    user.value = null
  }

  return {
    initialized,
    user,
    loading,
    loginError,
    setupError,
    isLoggedIn,
    init,
    setup,
    doLogin,
    doLogout,
    clearUser,
    checkSetupStatus,
    checkLoginStatus,
  }
}
