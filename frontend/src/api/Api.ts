import api from './Axios'
import {
  LoginRequest, OtpVerifyRequest, SaIdVerifyRequest,
  FaceVerifyRequest, QueueEntry, Branch, Document,
  FeedbackRequest, ChatMessage, WaitTimePredictionRequest,
  WaitTimePredictionResponse, ApiResponse, User, Application, ServiceCode,
  CatalogResponse
} from '@/types'



// ── AUTH ──────────────────────────────────────────────
export const authService = {
  register: (data: { name: string; email: string; phone: string; password: string }) =>
    api.post<ApiResponse<{ userId: string }>>('/auth/register', data),

  login: (data: LoginRequest) =>
    api.post<ApiResponse<{ userId: string; phone: string }>>('/auth/login', data),

  sendOtp: (userId: string) =>
    api.post<ApiResponse<void>>('/auth/send-otp', { userId }),

  verifyOtp: (data: OtpVerifyRequest) =>
    api.post<ApiResponse<{ token: string; user: User }>>('/auth/verify-otp', data),

  verifySaId: (data: SaIdVerifyRequest) =>
    api.post<ApiResponse<void>>('/auth/verify-id', data),

  verifyFace: (data: FaceVerifyRequest) =>
    api.post<ApiResponse<{ verified: boolean }>>('/auth/verify-face', data),
}

// ── USER ──────────────────────────────────────────────
export const userService = {
  getMe: () =>
    api.get<ApiResponse<User>>('/users/me'),

  updateMe: (data: Partial<User>) =>
    api.put<ApiResponse<User>>('/users/me', data),
}

// ── APPLICATIONS ──────────────────────────────────────
export const applicationService = {
  create: (data: { serviceType: ServiceCode; branchId: string }) =>
    api.post<ApiResponse<Application>>('/applications', data),

  getMyApplications: () =>
    api.get<ApiResponse<Application[]>>('/applications/my'),

  getById: (id: string) =>
    api.get<ApiResponse<Application>>(`/applications/${id}`),
}

// ── QUEUE ──────────────────────────────────────────────
export const queueService = {
  join: (data: { branchId: string; serviceType: ServiceCode }) =>
    api.post<ApiResponse<QueueEntry>>('/queue/join', data),

  getMyTicket: () =>
    api.get<ApiResponse<QueueEntry>>('/queue/my-ticket'),

  cancel: (ticketId: string) =>
    api.delete<ApiResponse<void>>(`/queue/${ticketId}/cancel`),

  getBranchQueue: (branchId: string) =>
    api.get<ApiResponse<QueueEntry[]>>(`/queue/branch/${branchId}`),
}

// ── BRANCHES ──────────────────────────────────────────
export const branchService = {
  getAll: () =>
    api.get<ApiResponse<Branch[]>>('/branches'),

  getById: (id: string) =>
    api.get<ApiResponse<Branch>>(`/branches/${id}`),

  getNearby: (lat: number, lng: number) =>
    api.get<ApiResponse<Branch[]>>(`/branches/nearby?lat=${lat}&lng=${lng}`),
}

// ── DOCUMENTS ──────────────────────────────────────────
export const documentService = {
  upload: (formData: FormData) =>
    api.post<ApiResponse<Document>>('/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),

  getMyDocs: () =>
    api.get<ApiResponse<Document[]>>('/documents/my-docs'),

  delete: (id: string) =>
    api.delete<ApiResponse<void>>(`/documents/${id}`),
}

// ── CATALOG ──────────────────────────────────────────
export const catalogService = {
  getCatalog: () =>
    api.get<ApiResponse<CatalogResponse>>('/catalog'),
}

// ── FEEDBACK ──────────────────────────────────────────
export const feedbackService = {
  submit: (data: FeedbackRequest) =>
    api.post<ApiResponse<void>>('/feedback', data),
}

// ── ASSISTANT ──────────────────────────────────────────
export const assistantService = {
  sendMessage: (message: string, history: ChatMessage[]) =>
    api.post<ApiResponse<{ reply: string }>>('/assistant/chat', { message, history }),
}

// ── ML SERVICE (called via Spring Boot proxy) ──────────
export const mlService = {
  predictWaitTime: (data: WaitTimePredictionRequest) =>
    api.post<WaitTimePredictionResponse>('/ml/predict/wait-time', data),
}