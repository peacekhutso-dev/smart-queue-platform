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
// The face session has no TTL — it lasts until the next login/logout
// (identity-service clears the server-side face gate on every login).

interface AuthState {
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
  setAuth:          (user: AuthUser) => void
  setPending:       (phone: string) => void
  set2faVerified:   () => void
  setFaceVerified:  () => void
  openFaceModal:    (onSuccess: () => void, onCancel: () => void) => void
  closeFaceModal:   () => void
  onFaceSuccess:    () => void
  onFaceCancel:     () => void
  logout:           () => Promise<void>
}

// The JWT itself lives in an httpOnly cookie now (set by identity-service),
// invisible to JS — this is just a non-sensitive local hint so the app
// knows whether to expect an authenticated session across reloads without
// needing to read the (unreadable) cookie. It grants nothing by itself; the
// cookie is the only thing any backend service actually trusts.
const AUTHED_HINT_KEY = 'dha_authed'

export const useAuthStore = create<AuthState>((set, get) => ({
  user: (() => {
    try { const u = localStorage.getItem('dha_user'); return u ? JSON.parse(u) : null }
    catch { return null }
  })(),
  isAuthenticated: localStorage.getItem(AUTHED_HINT_KEY) === '1',
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

  isFaceValid: () => !!get().faceVerifiedAt,

  // ── Actions ───────────────────────────────────────────────────────────────

  // A new login/registration starts without a face session — the server
  // clears its face gate on login, so the local hint must match.
  setAuth: (user) => {
    localStorage.setItem(AUTHED_HINT_KEY, '1')
    localStorage.setItem('dha_user', JSON.stringify(user))
    localStorage.removeItem('dha_face_at')
    set({ user, isAuthenticated: true, faceVerifiedAt: null })
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

  logout: async () => {
    localStorage.removeItem(AUTHED_HINT_KEY)
    localStorage.removeItem('dha_user')
    localStorage.removeItem('dha_2fa_at')
    localStorage.removeItem('dha_face_at')
    set({
      user: null, isAuthenticated: false, pendingPhone: null,
      twoFaVerifiedAt: null, faceVerifiedAt: null,
      showFaceModal: false, _faceOnSuccess: null, _faceOnCancel: null,
    })
    // The token is an httpOnly cookie — only the server can clear it.
    try {
      const base = import.meta.env.VITE_API_URL ?? ''
      await fetch(`${base}/api/auth/logout`, { method: 'POST', credentials: 'include' })
    } catch {
      // Local state is already cleared; a failed logout call just leaves a
      // stale cookie that'll expire on its own.
    }
  },
}))
