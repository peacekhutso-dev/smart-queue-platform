import { create } from 'zustand'
import type { AuthUser } from '../types'

const TWO_FA_TTL_MS = 8 * 60 * 60 * 1000 // 8 hours
const FINGERPRINT_TTL_MS = 4 * 60 * 60 * 1000 // 4 hours — matches app.zero-trust.fingerprint-ttl-hours

// The JWT lives in an httpOnly dha_token cookie now (set by identity-service),
// invisible to JS — this is just a non-sensitive local hint so the app knows
// whether to expect an authenticated session across reloads without needing
// to read the (unreadable) cookie. It grants nothing by itself; the cookie is
// the only thing any backend service actually trusts.
const AUTHED_HINT_KEY = 'dha_admin_authed'

interface AuthState {
  user: AuthUser | null
  isAuthenticated: boolean
  pendingIdentifier: string | null

  /** Timestamps set after each successful verification gate. */
  twoFaVerifiedAt: number | null
  fingerprintVerifiedAt: number | null

  /** Fingerprint-scan modal state (triggered by Axios interceptor on 403 REQUIRES_FINGERPRINT). */
  showFingerprintModal: boolean
  _fpOnSuccess: (() => void) | null
  _fpOnCancel: (() => void) | null

  isTwoFaValid: () => boolean
  isFingerprintValid: () => boolean

  setAuth: (user: AuthUser) => void
  setPendingIdentifier: (identifier: string) => void
  set2faVerified: () => void
  setFingerprintVerified: () => void
  openFingerprintModal: (onSuccess: () => void, onCancel: () => void) => void
  closeFingerprintModal: () => void
  onFingerprintSuccess: () => void
  onFingerprintCancel: () => void
  logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: (() => {
    try {
      const u = localStorage.getItem('dha_admin_user')
      return u ? JSON.parse(u) : null
    } catch {
      return null
    }
  })(),
  isAuthenticated: localStorage.getItem(AUTHED_HINT_KEY) === '1',
  pendingIdentifier: null,

  twoFaVerifiedAt: (() => {
    const v = localStorage.getItem('dha_admin_2fa_at')
    return v ? Number(v) : null
  })(),
  fingerprintVerifiedAt: (() => {
    const v = localStorage.getItem('dha_admin_fp_at')
    return v ? Number(v) : null
  })(),

  showFingerprintModal: false,
  _fpOnSuccess: null,
  _fpOnCancel: null,

  isTwoFaValid: () => {
    const at = get().twoFaVerifiedAt
    return !!at && Date.now() - at < TWO_FA_TTL_MS
  },

  isFingerprintValid: () => {
    const at = get().fingerprintVerifiedAt
    return !!at && Date.now() - at < FINGERPRINT_TTL_MS
  },

  setAuth: (user) => {
    localStorage.setItem(AUTHED_HINT_KEY, '1')
    localStorage.setItem('dha_admin_user', JSON.stringify(user))
    set({ user, isAuthenticated: true })
  },

  setPendingIdentifier: (identifier) => set({ pendingIdentifier: identifier }),

  set2faVerified: () => {
    const now = Date.now()
    localStorage.setItem('dha_admin_2fa_at', String(now))
    set({ twoFaVerifiedAt: now })
  },

  setFingerprintVerified: () => {
    const now = Date.now()
    localStorage.setItem('dha_admin_fp_at', String(now))
    set({ fingerprintVerifiedAt: now })
  },

  openFingerprintModal: (onSuccess, onCancel) =>
    set({ showFingerprintModal: true, _fpOnSuccess: onSuccess, _fpOnCancel: onCancel }),

  closeFingerprintModal: () =>
    set({ showFingerprintModal: false, _fpOnSuccess: null, _fpOnCancel: null }),

  onFingerprintSuccess: () => {
    const cb = get()._fpOnSuccess
    get().setFingerprintVerified()
    set({ showFingerprintModal: false, _fpOnSuccess: null, _fpOnCancel: null })
    cb?.()
  },

  onFingerprintCancel: () => {
    const cb = get()._fpOnCancel
    set({ showFingerprintModal: false, _fpOnSuccess: null, _fpOnCancel: null })
    cb?.()
  },

  logout: async () => {
    localStorage.removeItem(AUTHED_HINT_KEY)
    localStorage.removeItem('dha_admin_user')
    localStorage.removeItem('dha_admin_2fa_at')
    localStorage.removeItem('dha_admin_fp_at')
    set({
      user: null,
      isAuthenticated: false,
      pendingIdentifier: null,
      twoFaVerifiedAt: null,
      fingerprintVerifiedAt: null,
      showFingerprintModal: false,
      _fpOnSuccess: null,
      _fpOnCancel: null,
    })
    // The token is an httpOnly cookie — only the server can clear it.
    try {
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
    } catch {
      // Local state is already cleared; a failed logout call just leaves a
      // stale cookie that'll expire on its own.
    }
  },
}))
