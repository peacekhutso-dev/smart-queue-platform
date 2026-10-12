import axios from 'axios'
import { useAuthStore } from '../store/AuthStore'

// The JWT lives in an httpOnly dha_token cookie now, not a header this code
// attaches manually — withCredentials makes the browser send it (and lets
// the server's Set-Cookie be honored) even when this request crosses origins
// (the built app talking to the Gateway directly, not through Vite's proxy).
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  // Face verification (verification-service -> face-service, DeepFace CPU
  // inference) genuinely takes 20-30s+ on this hardware — 15s was aborting
  // the request client-side on calls that were actually about to succeed
  // (see api-gateway's response-timeout and verification-service's
  // RestTemplateConfig, bumped for the same reason).
  timeout: 60000,
  withCredentials: true,
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
      if (code === 'REQUIRES_2FA' || code === 'REQUIRES_IDENTITY_CONFIRMATION') {
        // 2FA / Home-Affairs identity-confirmation session expired — log out
        // so the user re-authenticates cleanly (LoginPage's flow is what
        // re-grants both).
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
