import { create } from 'zustand'

interface AuthUser {
  id: string
  name: string
  email: string
  phone: string
  saIdNumber: string
  verified: boolean
  province: string
  createdAt: string
}

const TWO_FA_TTL_MS = 8  * 60 * 60 * 1000   // 8 hours
const FACE_TTL_MS   = 4  * 60 * 60 * 1000   // 4 hours

interface AuthState {
  token:     string | null
  user:      AuthUser | null
  isAuthenticated: boolean
  pendingPhone:    string | null

  /** Timestamps set after each successful verification gate. */
  twoFaVerifiedAt: number | null
  faceVerifiedAt:  number | null

  /** Face-scan modal state (triggered by Axios interceptor on 403 REQUIRES_FACE). */
  showFaceModal:   boolean
  _faceOnSuccess:  (() => void) | null
  _faceOnCancel:   (() => void) | null

  // ── Computed helpers ──────────────────────────────────────────────────────
  isTwoFaValid: () => boolean
  isFaceValid:  () => boolean

  // ── Actions ───────────────────────────────────────────────────────────────
  setAuth:          (user: AuthUser, token: string) => void
  setPending:       (phone: string) => void
  set2faVerified:   () => void
  setFaceVerified:  () => void
  openFaceModal:    (onSuccess: () => void, onCancel: () => void) => void
  closeFaceModal:   () => void
  onFaceSuccess:    () => void
  onFaceCancel:     () => void
  logout:           () => void
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token:   localStorage.getItem('dha_token'),
  user: (() => {
    try { const u = localStorage.getItem('dha_user'); return u ? JSON.parse(u) : null }
    catch { return null }
  })(),
  isAuthenticated: !!localStorage.getItem('dha_token'),
  pendingPhone:    null,

  twoFaVerifiedAt: (() => {
    const v = localStorage.getItem('dha_2fa_at'); return v ? Number(v) : null
  })(),
  faceVerifiedAt: (() => {
    const v = localStorage.getItem('dha_face_at'); return v ? Number(v) : null
  })(),

  showFaceModal:  false,
  _faceOnSuccess: null,
  _faceOnCancel:  null,

  // ── Computed ──────────────────────────────────────────────────────────────

  isTwoFaValid: () => {
    const at = get().twoFaVerifiedAt
    return !!at && (Date.now() - at < TWO_FA_TTL_MS)
  },

  isFaceValid: () => {
    const at = get().faceVerifiedAt
    return !!at && (Date.now() - at < FACE_TTL_MS)
  },

  // ── Actions ───────────────────────────────────────────────────────────────

  setAuth: (user, token) => {
    localStorage.setItem('dha_token', token)
    localStorage.setItem('dha_user', JSON.stringify(user))
    set({ user, token, isAuthenticated: true })
  },

  setPending: (phone) => set({ pendingPhone: phone }),

  set2faVerified: () => {
    const now = Date.now()
    localStorage.setItem('dha_2fa_at', String(now))
    set({ twoFaVerifiedAt: now })
  },

  setFaceVerified: () => {
    const now = Date.now()
    localStorage.setItem('dha_face_at', String(now))
    set({ faceVerifiedAt: now })
  },

  openFaceModal: (onSuccess, onCancel) =>
    set({ showFaceModal: true, _faceOnSuccess: onSuccess, _faceOnCancel: onCancel }),

  closeFaceModal: () =>
    set({ showFaceModal: false, _faceOnSuccess: null, _faceOnCancel: null }),

  onFaceSuccess: () => {
    const cb = get()._faceOnSuccess
    get().setFaceVerified()
    set({ showFaceModal: false, _faceOnSuccess: null, _faceOnCancel: null })
    cb?.()
  },

  onFaceCancel: () => {
    const cb = get()._faceOnCancel
    set({ showFaceModal: false, _faceOnSuccess: null, _faceOnCancel: null })
    cb?.()
  },

  logout: () => {
    localStorage.removeItem('dha_token')
    localStorage.removeItem('dha_user')
    localStorage.removeItem('dha_2fa_at')
    localStorage.removeItem('dha_face_at')
    set({
      token: null, user: null, isAuthenticated: false, pendingPhone: null,
      twoFaVerifiedAt: null, faceVerifiedAt: null,
      showFaceModal: false, _faceOnSuccess: null, _faceOnCancel: null,
    })
  },
}))
