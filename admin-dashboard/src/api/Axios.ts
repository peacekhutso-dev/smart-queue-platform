import axios from 'axios'
import { useAuthStore } from '../store/AuthStore'

// The JWT lives in an httpOnly dha_token cookie set by identity-service, not
// a header this code attaches manually — withCredentials makes the browser
// send it (and honors the server's Set-Cookie) on every request.
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
  withCredentials: true,
})

// Handle Zero Trust gate rejections + standard auth errors.
// Mirrors smart-queue-platform/frontend's interceptor, with the citizen face gate
// swapped for the staff/admin fingerprint (WebAuthn) gate.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const code = error.response?.data?.code as string | undefined

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

      if (code === 'REQUIRES_FINGERPRINT') {
        // Fingerprint session expired — open modal; retry original request on success
        return new Promise((resolve, reject) => {
          useAuthStore.getState().openFingerprintModal(
            () => resolve(api(error.config)),
            () => reject(error),
          )
        })
      }
    }

    return Promise.reject(error)
  },
)

export default api
