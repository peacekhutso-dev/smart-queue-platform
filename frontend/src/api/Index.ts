import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: `${API_URL}/api`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

// Request interceptor — attach JWT
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('dha_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — handle 401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('dha_token');
      localStorage.removeItem('dha_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

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