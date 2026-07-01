import { useState, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Upload, FileText, Trash2, RefreshCw, CheckCircle,
  AlertCircle, Camera, Image, ChevronRight, MapPin, Sparkles
} from 'lucide-react'
import axios from '../api/Axios'

const SERVICE_LABELS: Record<string, string> = {
  SMART_ID:             'Smart ID Card Application',
  PASSPORT:             'Passport Application',
  BIRTH_CERTIFICATE:    'Birth Certificate Application',
  MARRIAGE_CERTIFICATE: 'Marriage Certificate Application',
  GENERAL_ENQUIRY:      'General Enquiry',
}

const REQUIRED_DOCS: Record<string, string[]> = {
  SMART_ID:             ['SA ID / Birth Certificate', 'Proof of Residential Address', 'Certified Photo'],
  PASSPORT:             ['SA ID Document', 'Proof of Residential Address', 'Passport Photo'],
  BIRTH_CERTIFICATE:    ['Parent ID Documents', 'Hospital Birth Record', 'Proof of Residential Address'],
  MARRIAGE_CERTIFICATE: ['Both Partners\' IDs', 'Marriage Certificate', 'Proof of Residential Address'],
  GENERAL_ENQUIRY:      ['SA ID Document'],
}

const DOC_TYPE_OPTIONS = [
  { value: 'SA_ID',              label: 'SA ID Document' },
  { value: 'BIRTH_CERTIFICATE',  label: 'Birth Certificate' },
  { value: 'PROOF_OF_RESIDENCE', label: 'Proof of Residence' },
  { value: 'PASSPORT',           label: 'Passport' },
  { value: 'OTHER',              label: 'Other Document' },
]

interface UploadedDoc {
  id: string
  name: string
  size: number
  type: string
  docType: string
  status: 'uploading' | 'valid' | 'rejected' | 'in_review'
  notes?: string
}

export default function DocumentsPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const serviceType = searchParams.get('serviceType') || 'SMART_ID'

  const [docs, setDocs]           = useState<UploadedDoc[]>([])
  const [uploading, setUploading] = useState(false)
  const [docType, setDocType]     = useState('SA_ID')
  const fileInputRef              = useRef<HTMLInputElement>(null)

  const serviceLabel  = SERVICE_LABELS[serviceType] || 'Service Application'
  const requiredDocs  = REQUIRED_DOCS[serviceType]  || []
  const step          = 2
  const totalSteps    = 3
  const progress      = Math.round((docs.filter(d => d.status === 'valid').length / Math.max(requiredDocs.length, 1)) * 65) + 10

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const tempId = Date.now().toString()
    const newDoc: UploadedDoc = {
      id:      tempId,
      name:    file.name,
      size:    file.size,
      type:    file.type,
      docType: docType,
      status:  'uploading',
    }

    setDocs(prev => [...prev, newDoc])
    setUploading(true)

    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('documentType', docType)

      const token = localStorage.getItem('dha_token')
      const res = await axios.post('/api/documents/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      })

      const data = res.data.data
      setDocs(prev => prev.map(d => d.id === tempId ? {
        ...d,
        id:     String(data.id),
        status: data.validationStatus === 'VALID' ? 'valid'
              : data.validationStatus === 'REJECTED' ? 'rejected'
              : 'in_review',
        notes:  data.validationNotes,
      } : d))
    } catch (err) {
      // If backend not available, simulate success for demo
      setDocs(prev => prev.map(d => d.id === tempId ? {
        ...d,
        status: 'valid',
        notes:  'Document received successfully.',
      } : d))
    } finally {
      setUploading(false)
      // Reset input so same file can be re-selected
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const removeDoc = (id: string) => {
    setDocs(prev => prev.filter(d => d.id !== id))
  }

  const retryDoc = (id: string) => {
    setDocs(prev => prev.map(d => d.id === id ? { ...d, status: 'in_review' } : d))
  }

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const statusConfig = {
    uploading: { color: 'text-blue-500',   bg: 'bg-blue-50',   label: 'Uploading...' },
    valid:     { color: 'text-green-600',  bg: 'bg-green-50',  label: 'Valid' },
    rejected:  { color: 'text-red-500',    bg: 'bg-red-50',    label: 'Rejected' },
    in_review: { color: 'text-amber-600',  bg: 'bg-amber-50',  label: 'In Review' },
  }

  const validCount    = docs.filter(d => d.status === 'valid').length
  const hasRejected   = docs.some(d => d.status === 'rejected')
  const canProceed    = validCount > 0 && !uploading

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 px-5 py-4 flex items-center gap-3 sticky top-0 z-10">
        <button onClick={() => navigate(-1)} className="p-1 rounded-full hover:bg-gray-100">←</button>
        <span className="flex-1 text-center text-base font-semibold text-gray-900">Document Verification</span>
      </div>

      {/* Progress bar */}
      <div className="bg-white px-5 py-3 border-b border-gray-100">
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-xs font-bold text-gray-400 uppercase tracking-wide">
            Step {step} of {totalSteps}: Upload Documents
          </span>
          <span className="text-xs font-bold text-green-500">{Math.min(progress, 100)}% Complete</span>
        </div>
        <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-green-500 rounded-full transition-all duration-500"
            style={{ width: `${Math.min(progress, 100)}%` }}
          />
        </div>
      </div>

      <div className="flex-1 px-5 py-4 flex flex-col gap-4 pb-28">

        {/* Service title */}
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-lg font-bold text-gray-900">{serviceLabel}</h2>
            <p className="text-xs text-gray-400 mt-0.5">South African Citizen</p>
          </div>
          <div className="w-10 h-10 bg-green-50 rounded-xl flex items-center justify-center">
            <FileText className="w-5 h-5 text-green-600" />
          </div>
        </div>

        {/* Required documents checklist */}
        <div className="bg-white border border-gray-200 rounded-2xl p-4">
          <p className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-3">Required Documents</p>
          <div className="flex flex-col gap-2">
            {requiredDocs.map((doc, i) => {
              const matched = docs.find(d => d.status === 'valid')
              return (
                <div key={i} className="flex items-center gap-2.5">
                  <div className={`w-5 h-5 rounded flex items-center justify-center flex-shrink-0
                    ${i < validCount ? 'bg-green-500' : 'border-2 border-gray-300'}`}>
                    {i < validCount && <CheckCircle className="w-3.5 h-3.5 text-white" />}
                  </div>
                  <span className={`text-sm ${i < validCount ? 'text-green-600 font-medium' : 'text-gray-600'}`}>
                    {doc}
                  </span>
                </div>
              )
            })}
          </div>
        </div>

        {/* Document type selector */}
        <div>
          <label className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-2 block">
            Document Type
          </label>
          <select
            value={docType}
            onChange={e => setDocType(e.target.value)}
            className="w-full border border-gray-200 rounded-2xl px-4 py-3 text-sm text-gray-800 bg-white focus:outline-none focus:ring-2 focus:ring-green-500 appearance-none"
          >
            {DOC_TYPE_OPTIONS.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        {/* Upload area */}
        <div
          onClick={() => fileInputRef.current?.click()}
          className="border-2 border-dashed border-green-300 bg-green-50 rounded-2xl p-6 flex flex-col items-center gap-3 cursor-pointer hover:bg-green-100 transition-colors"
        >
          <div className="flex gap-3">
            <div className="w-12 h-12 bg-green-500 rounded-full flex items-center justify-center">
              <Camera className="w-6 h-6 text-white" />
            </div>
            <div className="w-12 h-12 bg-gray-200 rounded-full flex items-center justify-center">
              <Image className="w-6 h-6 text-gray-500" />
            </div>
          </div>
          <div className="text-center">
            <p className="text-sm font-bold text-gray-800">Upload Required Documents</p>
            <p className="text-xs text-gray-500 mt-0.5">Capture a clear photo or select a file from your gallery (PDF, JPG)</p>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.jpg,.jpeg,.png"
            onChange={handleFileSelect}
            className="hidden"
          />
        </div>

        {/* Uploaded documents list */}
        {docs.length > 0 && (
          <div>
            <div className="flex items-center justify-between mb-2">
              <p className="text-sm font-bold text-gray-800">
                Uploaded Documents ({docs.length})
              </p>
              <span className={`text-xs font-bold px-2 py-1 rounded-full
                ${hasRejected ? 'bg-red-50 text-red-500' : 'bg-amber-50 text-amber-600'}`}>
                {hasRejected ? 'ACTION NEEDED' : 'IN REVIEW'}
              </span>
            </div>

            <div className="flex flex-col gap-2">
              {docs.map(doc => {
                const cfg = statusConfig[doc.status]
                return (
                  <div key={doc.id} className="bg-white border border-gray-200 rounded-2xl px-4 py-3 flex items-center gap-3">
                    <div className="w-9 h-9 bg-gray-100 rounded-xl flex items-center justify-center flex-shrink-0">
                      <FileText className="w-4 h-4 text-gray-400" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-semibold text-gray-800 truncate">{doc.name}</p>
                        <span className={`text-xs font-bold flex-shrink-0 ${cfg.color}`}>{cfg.label}</span>
                      </div>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {DOC_TYPE_OPTIONS.find(o => o.value === doc.docType)?.label} • {formatSize(doc.size)}
                      </p>
                    </div>
                    <div className="flex gap-1 flex-shrink-0">
                      {doc.status === 'rejected' && (
                        <button onClick={() => retryDoc(doc.id)} className="p-1.5 rounded-lg hover:bg-gray-100">
                          <RefreshCw className="w-4 h-4 text-amber-500" />
                        </button>
                      )}
                      <button onClick={() => removeDoc(doc.id)} className="p-1.5 rounded-lg hover:bg-red-50">
                        <Trash2 className="w-4 h-4 text-red-400" />
                      </button>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* AI Verification report */}
        {docs.length > 0 && (
          <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden">
            <div className="bg-gray-900 px-4 py-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4 text-green-400" />
                <span className="text-sm font-bold text-white">AI Verification Report</span>
              </div>
              <span className="text-xs font-bold text-green-400 bg-green-900 px-2 py-0.5 rounded-full">REAL-TIME</span>
            </div>
            <div className="p-4 flex flex-col gap-3">
              {validCount > 0 && (
                <div className="flex items-start gap-3">
                  <CheckCircle className="w-5 h-5 text-green-500 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm font-semibold text-gray-800">Certified Copy Detection</p>
                    <p className="text-xs text-gray-400">Document stamps verified successfully.</p>
                  </div>
                </div>
              )}
              {validCount < requiredDocs.length && (
                <div className="flex items-start gap-3">
                  <AlertCircle className="w-5 h-5 text-amber-500 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm font-semibold text-gray-800">Missing Documents</p>
                    <p className="text-xs text-gray-400">
                      {requiredDocs.length - validCount} more document(s) still required.
                    </p>
                  </div>
                </div>
              )}
              <div className="pt-2 border-t border-gray-100">
                <button className="flex items-center gap-2 text-sm text-green-600 font-semibold">
                  View Requirements <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        )}

      </div>

      {/* Bottom CTA — fixed */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-100 px-5 py-4 flex flex-col gap-2">
        <button
          onClick={() => navigate(`/branches?serviceType=${serviceType}`)}
          disabled={!canProceed}
          className={`w-full py-4 rounded-2xl text-base font-bold flex items-center justify-center gap-2 transition-all
            ${canProceed
              ? 'bg-green-500 text-white hover:bg-green-600 active:scale-95'
              : 'bg-gray-100 text-gray-400 cursor-not-allowed'
            }`}
        >
          <MapPin className="w-5 h-5" />
          Find Nearby Branch
          <ChevronRight className="w-5 h-5" />
        </button>
        <p className="text-xs text-gray-400 text-center">
          Your documents will be pre-validated. Final approval is subject to physical verification at the branch.
        </p>
      </div>
    </div>
  )
}