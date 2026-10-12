// Reuses the shared client (relative '/api' baseURL, proxied same-origin
// through nginx) instead of building a second axios instance pointed at the
// Gateway's absolute origin — that second client bypassed the nginx proxy
// entirely, turning every call cross-origin. Simple GET/POST requests mostly
// got away with it, but the Gateway has no CORS config of its own (it's only
// ever meant to be reached same-origin — see Axios.ts), so any non-simple
// method (DELETE, PUT) failed its CORS preflight with a 403 and no
// CORS headers, which is what made ticket cancellation silently fail.
import api from './Axios';

export default api;

// Auth
export const authApi = {
  register: (data: any) => api.post('/auth/register', data),
  login: (data: any) => api.post('/auth/login', data),
  sendOtp: (identifier: string) => api.post('/auth/send-otp', { identifier }),
  verifyOtp: (identifier: string, otp: string) => api.post('/auth/verify-otp', { identifier, otp }),
  verifyId: (saIdNumber: string) => api.post('/auth/verify-id', { saIdNumber }),
  me: () => api.get('/auth/me'),
};

// Branches
export const branchApi = {
  getAll: () => api.get('/branches'),
  getById: (id: number) => api.get(`/branches/${id}`),
  getNearby: (lat: number, lng: number, radius = 100) =>
    api.get(`/branches/nearby?lat=${lat}&lng=${lng}&radius=${radius}`),
  getByProvince: (province: string) => api.get(`/branches/province/${province}`),
};

// Queue
export const queueApi = {
  join: (branchId: number, serviceType: string) => api.post('/queue/join', { branchId, serviceType }),
  myTicket: () => api.get('/queue/my-ticket'),
  cancel: (ticketId: number) => api.delete(`/queue/${ticketId}/cancel`),
  branchQueue: (branchId: number) => api.get(`/queue/branch/${branchId}`),
  history: () => api.get('/queue/history'),
};

// Documents
export const documentApi = {
  upload: (file: File, documentType: string) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('documentType', documentType);
    return api.post('/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  myDocs: () => api.get('/documents/my-docs'),
  delete: (id: number) => api.delete(`/documents/${id}`),
};

// Feedback
export const feedbackApi = {
  submit: (data: any) => api.post('/feedback', data),
};