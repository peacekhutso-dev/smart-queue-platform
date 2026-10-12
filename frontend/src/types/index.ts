export interface LoginRequest { identifier: string; password: string }
export interface OtpVerifyRequest { identifier: string; otp: string }
export interface SaIdVerifyRequest { saIdNumber: string }
export interface FaceVerifyRequest { imageData: string }
export interface FeedbackRequest { branchId: number; overallRating: number; staffHelpful?: boolean; emotionRating?: number; comment?: string; serviceType?: string }
export interface ChatMessage { id: string; role: 'user' | 'assistant'; content: string; timestamp: string }
export interface WaitTimePredictionRequest { queueSize: number; activeCounters: number; avgServiceTimeMinutes: number; serviceType: string }
export interface WaitTimePredictionResponse { estimatedWaitMinutes: number; confidence: number; method: string }
export interface Application { id: number; serviceType: string; status: string; createdAt: string }
export type ValidationStatus = 'PENDING' | 'VALID' | 'REJECTED' | 'IN_REVIEW'


export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface AuthResponse {
  token: string;
  type: string;
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  isVerified: boolean;
  isIdVerified: boolean;
}

export interface User {
  id: number;
  email: string;
  phoneNumber?: string;
  firstName: string;
  lastName: string;
  saIdNumber?: string;
  role: string;
  isVerified: boolean;
  isIdVerified: boolean;
  province?: string;
  createdAt: string;
}

export interface Branch {
  id: number;
  name: string;
  address: string;
  province: string;
  city: string;
  suburb: string;
  latitude: number;
  longitude: number;
  phoneNumber?: string;
  openingTime: string;
  closingTime: string;
  maxCapacity: number;
  activeCounters: number;
  avgServiceTimeMinutes: number;
  hasWifi: boolean;
  hasParking: boolean;
  isAccessible: boolean;
  currentQueueSize: number;
  estimatedWaitMinutes: number;
  capacityPercent: number;
  isOpen: boolean;
  distanceKm?: number;
}

export interface QueueEntry {
  id: number;
  ticketNumber: string;
  serviceType: string;
  status: 'WAITING' | 'CALLED' | 'SERVING' | 'COMPLETE' | 'CANCELLED';
  positionInQueue: number;
  estimatedWaitMinutes: number;
  counterNumber?: string;
  qrCode?: string;
  qrCodeImage?: string;
  branchId: number;
  branchName: string;
  branchAddress: string;
  joinedAt: string;
  calledAt?: string;
  completedAt?: string;
  totalInQueue: number;
}

export interface QueueBroadcast {
  branchId: number;
  branchName: string;
  totalWaiting: number;
  estimatedWaitMinutes: number;
  capacityPercent: number;
  currentlyCalledTicket?: string;
  currentlyCalledCounter?: string;
  topEntries: QueueEntry[];
  timestamp: string;
}


export interface Document {
  id: number
  fileName: string
  originalName: string
  fileType: string
  fileSize: number
  documentType: string
  docType?: string
  validationStatus: ValidationStatus
  validationNotes?: string
  uploadedAt: string
  expiresAt?: string
  rejectionReason?: string
 }

/** Catalog-driven now — codes come from catalog-service, not a fixed literal set. */
export type ServiceCode = string;

export type FeeType = 'FREE' | 'FIXED' | 'RANGE' | 'VARIES';

export interface DocumentRequirement {
  id: number;
  documentCategory: string;
  label: string;
  required: boolean;
  conditionNote?: string;
}

export interface ServiceOffering {
  id: number;
  applicantTypeCode: string;
  code: ServiceCode;
  name: string;
  description?: string;
  feeType: FeeType;
  feeMin?: number;
  feeMax?: number;
  currency: string;
  feeNote?: string;
  /** Server-formatted: "Free" / "R140" / "R70–R140" / "Varies". */
  feeDisplay: string;
  estimatedTurnaround?: string;
  documents: DocumentRequirement[];
}

export interface ApplicantType {
  id: number;
  code: string;
  displayName: string;
  description?: string;
  services: ServiceOffering[];
}

export interface CatalogResponse {
  applicantTypes: ApplicantType[];
}

export const PROVINCES = [
  'Eastern Cape', 'Free State', 'Gauteng', 'KwaZulu-Natal',
  'Limpopo', 'Mpumalanga', 'Northern Cape', 'North West', 'Western Cape'
];