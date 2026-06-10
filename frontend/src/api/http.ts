import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((config) => {
  if (config.data instanceof FormData) {
    delete config.headers['Content-Type']
  }
  return config
})

let isRedirecting = false

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const url: string = error.config?.url || ''

    if (status === 401) {
      const isAuthEndpoint = url.includes('/api/admin/auth/')

      if (!isAuthEndpoint) {
        if (!isRedirecting) {
          isRedirecting = true
          ElMessage.error('登录已过期，请重新登录')
          const currentPath = window.location.pathname
          if (currentPath !== '/login' && currentPath !== '/setup') {
            window.location.href = '/login'
          }
          setTimeout(() => {
            isRedirecting = false
          }, 1000)
        }
      }
    }

    return Promise.reject(error)
  },
)

export default http
