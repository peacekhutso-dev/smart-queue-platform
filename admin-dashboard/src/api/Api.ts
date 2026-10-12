import api from './Axios'
import type {
  ApiResponse, AuthResponse, LoginRequest, OtpVerifyRequest,
  ForgotPasswordRequest, ResetPasswordRequest,
  Branch, BranchStats, QueueEntry, QueueStatus, CitizenRecord,
} from '../types'

// ── AUTH ──────────────────────────────────────────────
export const authService = {
  login: (data: LoginRequest) =>
    api.post<ApiResponse<AuthResponse>>('/auth/login', data),

  sendOtp: (identifier: string) =>
    api.post<ApiResponse<string>>('/auth/send-otp', { identifier }),

  verifyOtp: (data: OtpVerifyRequest) =>
    api.post<ApiResponse<boolean>>('/auth/verify-otp', data),

  forgotPassword: (data: ForgotPasswordRequest) =>
    api.post<ApiResponse<string>>('/auth/forgot-password', data),

  resetPassword: (data: ResetPasswordRequest) =>
    api.post<ApiResponse<string>>('/auth/reset-password', data),

  getMe: () =>
    api.get<ApiResponse<unknown>>('/auth/me'),
}

// ── WEBAUTHN (fingerprint gate) ───────────────────────
export const webauthnService = {
  registerOptions: () =>
    api.post<ApiResponse<string>>('/auth/webauthn/register/options'),

  registerVerify: (credential: unknown) =>
    api.post<ApiResponse<null>>('/auth/webauthn/register/verify', credential),

  authenticateOptions: () =>
    api.post<ApiResponse<string>>('/auth/webauthn/authenticate/options'),

  authenticateVerify: (credential: unknown) =>
    api.post<ApiResponse<boolean>>('/auth/webauthn/authenticate/verify', credential),
}

// ── BRANCHES ──────────────────────────────────────────
export const branchService = {
  getAll: () =>
    api.get<ApiResponse<Branch[]>>('/branches'),

  getById: (id: number) =>
    api.get<ApiResponse<Branch>>(`/branches/${id}`),
}

// ── QUEUE (staff/admin operations) ────────────────────
export const queueService = {
  getBranchQueue: (branchId: number) =>
    api.get<ApiResponse<QueueEntry[]>>(`/queue/branch/${branchId}`),

  getBranchStats: (branchId: number) =>
    api.get<ApiResponse<BranchStats>>(`/queue/branch/${branchId}/stats`),

  advance: (entryId: number, status: QueueStatus, counter?: string) =>
    api.put<ApiResponse<QueueEntry>>(`/queue/${entryId}/advance`, null, {
      params: { status, ...(counter ? { counter } : {}) },
    }),
}

// ── CITIZEN RECORDS (DEV ONLY — admin stand-in for DHA's population register) ──
export const citizenRecordService = {
  list: () =>
    api.get<ApiResponse<CitizenRecord[]>>('/auth/admin/citizen-records'),

  save: (record: Omit<CitizenRecord, 'hasPhoto'> & { hasPhoto?: boolean }) =>
    api.put<ApiResponse<CitizenRecord>>('/auth/admin/citizen-records', record),

  uploadPhoto: (saIdNumber: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.put<ApiResponse<null>>(`/auth/admin/citizen-records/${saIdNumber}/photo`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  getPhoto: (saIdNumber: string) =>
    api.get<Blob>(`/auth/admin/citizen-records/${saIdNumber}/photo`, { responseType: 'blob' }),

  deletePhoto: (saIdNumber: string) =>
    api.delete<ApiResponse<null>>(`/auth/admin/citizen-records/${saIdNumber}/photo`),
}
