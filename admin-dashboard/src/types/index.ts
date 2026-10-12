export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface LoginRequest { identifier: string; password: string }
export interface OtpVerifyRequest { identifier: string; otp: string }
export interface ForgotPasswordRequest { identifier: string }
export interface ResetPasswordRequest { identifier: string; code: string; newPassword: string }

export type UserRole = 'CITIZEN' | 'STAFF' | 'ADMIN'

export interface AuthUser {
  userId: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
  isVerified: boolean
  isIdVerified: boolean
  phoneNumber?: string
  province?: string
  assignedBranchId?: number | null
}

export interface AuthResponse {
  type: string
  userId: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
  isVerified: boolean
  isIdVerified: boolean
  phoneNumber?: string
  province?: string
  assignedBranchId?: number | null
}

export interface Branch {
  id: number
  name: string
  address: string
  province: string
  city: string
  suburb: string
  latitude: number
  longitude: number
  phoneNumber?: string
  openingTime: string
  closingTime: string
  maxCapacity: number
  activeCounters: number
  avgServiceTimeMinutes: number
  hasWifi: boolean
  hasParking: boolean
  isAccessible: boolean
  currentQueueSize: number
  estimatedWaitMinutes: number
  capacityPercent: number
  isOpen: boolean
  distanceKm?: number
}

export type QueueStatus = 'WAITING' | 'CALLED' | 'SERVING' | 'COMPLETE' | 'CANCELLED'

export interface QueueEntry {
  id: number
  ticketNumber: string
  serviceType: string
  status: QueueStatus
  positionInQueue: number
  estimatedWaitMinutes: number
  counterNumber?: string
  qrCode?: string
  branchId: number
  branchName: string
  branchAddress: string
  joinedAt: string
  calledAt?: string
  completedAt?: string
  totalInQueue: number
}

export interface QueueBroadcast {
  branchId: number
  branchName: string
  totalWaiting: number
  estimatedWaitMinutes: number
  capacityPercent: number
  currentlyCalledTicket?: string
  currentlyCalledCounter?: string
  topEntries: QueueEntry[]
  timestamp: string
}

export interface BranchStats {
  inQueue: number
  avgWaitMinutes: number
  totalServedToday: number
  efficiencyPercent: number
}

export const SERVICE_TYPE_LABELS: Record<string, string> = {
  SMART_ID: 'ID Application',
  PASSPORT: 'Passport Renewal',
  BIRTH_CERTIFICATE: 'Birth Certificate',
  MARRIAGE_CERTIFICATE: 'Marriage Certificate',
  GENERAL_ENQUIRY: 'Other Services',
}

// DEV ONLY: Home Affairs citizen record (Citizen Records dev tool).
export interface CitizenRecord {
  saIdNumber: string
  firstName: string
  middleName: string | null
  lastName: string
  dateOfBirth: string | null
  gender: string | null
  nationality: string | null
  citizenshipStatus: string | null
  phoneNumber: string | null
  email: string | null
  residentialAddress: string | null
  hasPhoto: boolean
}
