import axios from 'axios'
import { useAuthStore } from '../store/AuthStore'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

// Attach JWT to every request
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Handle Zero Trust gate rejections + standard auth errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const code   = error.response?.data?.code as string | undefined

    if (status === 401) {
      // JWT invalid / expired — full logout
      useAuthStore.getState().logout()
      window.location.href = '/login'
      return Promise.reject(error)
    }

    if (status === 403) {
      if (code === 'REQUIRES_2FA') {
        // 2FA session expired — log out so user re-authenticates cleanly
        useAuthStore.getState().logout()
        window.location.href = '/login?reason=session_expired'
        return Promise.reject(error)
      }

      if (code === 'REQUIRES_FACE') {
        // Face session expired — open modal; retry original request on success
        return new Promise((resolve, reject) => {
          useAuthStore.getState().openFaceModal(
            () => resolve(api(error.config)),   // retry after face verify
            () => reject(error),                 // user dismissed modal
          )
        })
      }
    }

    return Promise.reject(error)
  },
)

export default api
