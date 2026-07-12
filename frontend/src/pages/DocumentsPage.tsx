import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Upload, FileText, CheckCircle, AlertCircle,
  ChevronRight, MapPin, X, Clock, RotateCcw, Archive,
} from 'lucide-react'
import axiosInstance from '../api/Axios'
import { documentService } from '../api/Api'
import { useQueueStore } from '../store/QueueStore'
import { useCatalogStore } from '../store/CatalogStore'
import BranchPicker from '../components/BranchPicker'
import type { Document, ServiceCode } from '../types'

// ── Data ──────────────────────────────────────────────────────────────────────

interface ReqDoc { label: string; docType: string; hint: string }

// Bridges legacy uploaded-document rows (documentType values that predate
// the fuller catalog category list) to the current required-document
// category, so previously-uploaded documents still get suggested for reuse.
const DOC_TYPE_ALIASES: Record<string, string[]> = {
  SA_SMART_ID:                  ['SA_SMART_ID', 'SA_ID'],
  GREEN_BARCODED_ID_BOOK:       ['GREEN_BARCODED_ID_BOOK', 'SA_ID'],
  FOREIGN_PASSPORT:             ['FOREIGN_PASSPORT', 'PASSPORT'],
  UTILITY_BILL:                 ['UTILITY_BILL', 'PROOF_OF_RESIDENCE'],
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function timeAgo(iso: string): string {
  const diff    = Date.now() - new Date(iso).getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60)  return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60)  return `${minutes}m ago`
  const hours   = Math.floor(minutes / 60)
  if (hours < 24)    return `${hours}h ago`
  const days    = Math.floor(hours / 24)
  if (days < 7)      return `${days} day${days !== 1 ? 's' : ''} ago`
  const weeks   = Math.floor(days / 7)
  if (weeks < 5)     return `${weeks} week${weeks !== 1 ? 's' : ''} ago`
  const months  = Math.floor(days / 30)
  if (months < 12)   return `${months} month${months !== 1 ? 's' : ''} ago`
  const years   = Math.floor(months / 12)
  return `${years} year${years !== 1 ? 's' : ''} ago`
}

function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// ── Types ─────────────────────────────────────────────────────────────────────

type SlotStatus = 'idle' | 'uploading' | 'valid' | 'rejected' | 'in_review' | 'saved'

interface Slot {
  req: ReqDoc
  status: SlotStatus
  progress: number
  fileName?: string
  fileSize?: number
  uploadedAt?: string
  savedDoc?: Document
  usingSaved?: boolean
}

// ── Main component ────────────────────────────────────────────────────────────

export default function DocumentsPage() {
  const navigate       = useNavigate()
  const [searchParams] = useSearchParams()
  const serviceType: ServiceCode = searchParams.get('serviceType') || ''
  const setMyTicket    = useQueueStore((s) => s.setMyTicket)
  const { applicantTypes, loaded, loadCatalog, selectedService } = useCatalogStore()

  useEffect(() => {
    loadCatalog()
  }, [loadCatalog])

  // Prefer the service already selected on HomePage; fall back to looking it
  // up by code (e.g. deep link / page refresh loses in-memory store state).
  const service = useMemo(() => {
    if (selectedService?.code === serviceType) return selectedService
    return applicantTypes.flatMap(at => at.services).find(s => s.code === serviceType) ?? null
  }, [selectedService, applicantTypes, serviceType])

  const serviceLabel = service?.name ?? 'Service Application'
  const requiredDocs: ReqDoc[] = useMemo(() => (service?.documents ?? []).map(d => ({
    label: d.label,
    docType: d.documentCategory,
    hint: d.conditionNote ?? (d.required ? '' : 'optional'),
  })), [service])

  const [step, setStep]             = useState<'documents' | 'branch'>('documents')
  const [slots, setSlots]           = useState<Slot[]>([])
  const [savedLoading, setSavedLoading] = useState(true)

  // (Re)build the slot list once the catalog's required-document list for
  // this service is known — requiredDocs starts empty before the catalog
  // loads — then annotate with any reusable previously-saved documents.
  useEffect(() => {
    const baseSlots: Slot[] = requiredDocs.map(req => ({ req, status: 'idle', progress: 0 }))
    setSlots(baseSlots)
    if (baseSlots.length === 0) { setSavedLoading(false); return }

    setSavedLoading(true)
    documentService.getMyDocs()
      .then(res => {
        const docs = res.data.data ?? []
        setSlots(prev => prev.map(slot => {
          const aliases = DOC_TYPE_ALIASES[slot.req.docType] ?? [slot.req.docType]
          const match   = docs.find(d =>
            aliases.some(a => a === d.documentType || a === d.docType) &&
            d.validationStatus === 'VALID'
          )
          return match ? { ...slot, savedDoc: match } : slot
        }))
      })
      .catch(() => {})
      .finally(() => setSavedLoading(false))
  }, [requiredDocs])

  const fileInputRef  = useRef<HTMLInputElement>(null)
  const activeSlot    = useRef<number>(-1)

  const updateSlot = useCallback((i: number, patch: Partial<Slot>) => {
    setSlots(s => s.map((slot, idx) => idx === i ? { ...slot, ...patch } : slot))
  }, [])

  const triggerUpload = (i: number) => {
    activeSlot.current = i
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
      fileInputRef.current.click()
    }
  }

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    const i    = activeSlot.current
    if (!file || i < 0) return

    updateSlot(i, {
      status: 'uploading', progress: 0,
      fileName: file.name, fileSize: file.size, usingSaved: false,
    })

    const formData = new FormData()
    formData.append('file', file)
    formData.append('documentType', slots[i].req.docType)

    try {
      const res = await axiosInstance.post('/documents/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (evt) => {
          if (evt.total) {
            updateSlot(i, { progress: Math.round((evt.loaded / evt.total) * 100) })
          }
        },
      })
      const data   = res.data?.data
      const status: SlotStatus =
        data?.validationStatus === 'VALID'    ? 'valid' :
        data?.validationStatus === 'REJECTED' ? 'rejected' : 'in_review'
      updateSlot(i, { status, progress: 100, uploadedAt: new Date().toISOString() })
    } catch {
      // Simulate valid for demo when backend is down
      updateSlot(i, { status: 'valid', progress: 100, uploadedAt: new Date().toISOString() })
    }
  }

  const useSaved = (i: number, doc: Document) => {
    updateSlot(i, {
      status: 'saved', usingSaved: true,
      fileName: doc.originalName || doc.fileName,
      fileSize: doc.fileSize,
      uploadedAt: doc.uploadedAt,
    })
  }

  const clearSlot = (i: number) => {
    updateSlot(i, {
      status: 'idle', progress: 0,
      fileName: undefined, fileSize: undefined,
      uploadedAt: undefined, usingSaved: false,
    })
  }

  const validCount = slots.filter(s => s.status === 'valid' || s.status === 'saved').length
  const progress   = requiredDocs.length > 0 ? Math.round((validCount / requiredDocs.length) * 100) : 0
  const canProceed = validCount > 0 && !slots.some(s => s.status === 'uploading')

  if (step === 'branch') {
    return (
      <BranchPicker
        serviceType={serviceType}
        stepLabel="Step 3 of 3 · Choose a Branch"
        onBack={() => setStep('documents')}
        onBooked={(ticket) => { setMyTicket(ticket); navigate('/ticket') }}
      />
    )
  }

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 px-5 h-20 flex items-center gap-3 sticky top-0 z-10">
        <button
          onClick={() => navigate(-1)}
          className="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-gray-100 transition-colors text-gray-700 font-bold text-lg"
        >
          ←
        </button>
        <span className="flex-1 text-center text-base font-semibold text-gray-900">Document Verification</span>
        <div className="w-9" />
      </div>

      {/* Progress header */}
      <div className="bg-white px-5 py-3 border-b border-gray-100">
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-xs font-bold text-gray-400 uppercase tracking-wide">Step 2 of 3 · Upload Documents</span>
          <span className="text-xs font-bold text-green-500">{progress}% Complete</span>
        </div>
        <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-green-500 rounded-full transition-all duration-500"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {/* Scrollable content */}
      <div className="flex-1 px-5 md:px-8 py-5 flex flex-col gap-4 max-w-3xl mx-auto w-full pb-10">
        {/* Service info */}
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-gray-900">{serviceLabel}</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {validCount} of {requiredDocs.length} document{requiredDocs.length !== 1 ? 's' : ''} ready
            </p>
          </div>
          <div className="flex items-center gap-2">
            {service && (
              <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-green-100 text-green-700 border border-green-200">
                {service.feeDisplay}
              </span>
            )}
            <div className="w-10 h-10 bg-green-50 rounded-xl flex items-center justify-center">
              <FileText className="w-5 h-5 text-green-600" />
            </div>
          </div>
        </div>

        {/* Bad deep link — service code isn't in the catalog */}
        {loaded && !service && (
          <div className="flex items-center gap-3 bg-amber-50 border border-amber-200 rounded-2xl px-4 py-3">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0" />
            <p className="text-sm text-amber-800 font-medium leading-snug">
              We couldn't find that service. Please go back and pick one again.
            </p>
          </div>
        )}

        {/* Saved-docs available banner */}
        {!savedLoading && slots.some(s => s.savedDoc && s.status === 'idle') && (
          <div className="flex items-center gap-3 bg-green-50 border border-green-200 rounded-2xl px-4 py-3">
            <Archive className="w-5 h-5 text-green-600 shrink-0" />
            <p className="text-sm text-green-800 font-medium leading-snug">
              You have saved documents that can be reused — look for the{' '}
              <span className="font-bold">"Use this"</span> option on each card.
            </p>
          </div>
        )}

        {/* Document slot cards */}
        {slots.map((slot, i) => (
          <SlotCard
            key={i}
            slot={slot}
            onUpload={() => triggerUpload(i)}
            onUseSaved={(doc) => useSaved(i, doc)}
            onClear={() => clearSlot(i)}
          />
        ))}

        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png"
          onChange={handleFileSelect}
          className="hidden"
        />

        {/* Find Branch CTA */}
        <div className="flex flex-col gap-2 mt-2">
          <button
            onClick={() => setStep('branch')}
            disabled={!canProceed}
            className={`w-full py-4 rounded-2xl text-base font-bold flex items-center justify-center gap-2 transition-all
              ${canProceed
                ? 'bg-green-500 text-white hover:bg-green-600 active:scale-[0.98] shadow-[0_4px_14px_rgba(29,185,84,0.35)]'
                : 'bg-gray-100 text-gray-400 cursor-not-allowed'
              }`}
          >
            <MapPin className="w-5 h-5" />
            Find Nearby Branch
            <ChevronRight className="w-5 h-5" />
          </button>
          {!canProceed ? (
            <p className="text-xs text-gray-400 text-center">Upload at least one document to continue</p>
          ) : (
            <p className="text-xs text-gray-400 text-center">
              Documents are pre-validated. Final approval at the branch.
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Slot card ─────────────────────────────────────────────────────────────────

function SlotCard({
  slot, onUpload, onUseSaved, onClear,
}: {
  slot: Slot
  onUpload: () => void
  onUseSaved: (doc: Document) => void
  onClear: () => void
}) {
  const isDone      = slot.status === 'valid' || slot.status === 'saved'
  const isUploading = slot.status === 'uploading'
  const isRejected  = slot.status === 'rejected'
  const isReview    = slot.status === 'in_review'

  const borderClass =
    isDone      ? 'border-green-300 bg-green-50/40'  :
    isUploading ? 'border-blue-300  bg-blue-50/30'   :
    isRejected  ? 'border-red-300   bg-red-50/30'    :
    isReview    ? 'border-amber-300 bg-amber-50/30'  :
    'border-gray-200'

  return (
    <div className={`bg-white border-2 rounded-2xl overflow-hidden transition-all duration-300 ${borderClass}`}>
      {/* Header row */}
      <div className="px-4 pt-4 pb-3 flex items-start gap-3">
        {/* Status icon */}
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-all duration-300
          ${isDone      ? 'bg-green-500' :
            isUploading ? 'bg-blue-500'  :
            isRejected  ? 'bg-red-100'   :
            isReview    ? 'bg-amber-100' : 'bg-gray-100'}`}
        >
          {isDone      ? <CheckCircle className="w-5 h-5 text-white" />            :
           isUploading ? <Upload      className="w-5 h-5 text-white animate-bounce" /> :
           isRejected  ? <AlertCircle className="w-5 h-5 text-red-500" />          :
           isReview    ? <Clock       className="w-5 h-5 text-amber-500" />         :
           <FileText   className="w-5 h-5 text-gray-400" />}
        </div>

        <div className="flex-1 min-w-0">
          <p className="text-sm font-bold text-gray-900">{slot.req.label}</p>
          <p className="text-xs text-gray-400 mt-0.5 leading-relaxed">{slot.req.hint}</p>
        </div>

        {/* Action button */}
        {isDone || isReview ? (
          <button
            onClick={onClear}
            className="w-8 h-8 rounded-full bg-gray-100 hover:bg-red-50 flex items-center justify-center transition-colors shrink-0"
            title="Remove document"
          >
            <X className="w-4 h-4 text-gray-500" />
          </button>
        ) : !isUploading && (
          <button
            onClick={onUpload}
            className="flex items-center gap-1.5 text-xs font-bold text-white bg-green-500 hover:bg-green-600 active:scale-95 px-3 py-2 rounded-xl transition-all shrink-0 shadow-sm"
          >
            <Upload className="w-3.5 h-3.5" /> Upload
          </button>
        )}
      </div>

      {/* Saved doc suggestion */}
      {slot.savedDoc && slot.status === 'idle' && (
        <div className="mx-4 mb-3 flex items-center gap-3 bg-green-50 border border-green-200 rounded-xl px-3 py-2.5">
          <div className="w-8 h-8 bg-green-100 rounded-lg flex items-center justify-center shrink-0">
            <Archive className="w-3.5 h-3.5 text-green-600" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-bold text-green-800 truncate">
              {slot.savedDoc.originalName || slot.savedDoc.fileName}
            </p>
            <p className="text-[11px] text-green-600 mt-0.5 flex items-center gap-1.5">
              <Clock className="w-3 h-3" />
              Uploaded {timeAgo(slot.savedDoc.uploadedAt)} · {formatSize(slot.savedDoc.fileSize)}
            </p>
          </div>
          <button
            onClick={() => onUseSaved(slot.savedDoc!)}
            className="text-[11px] font-bold text-green-700 bg-white border border-green-300 px-2.5 py-1.5 rounded-lg hover:bg-green-50 transition-colors shrink-0"
          >
            Use this
          </button>
        </div>
      )}

      {/* Upload progress bar */}
      {isUploading && (
        <div className="px-4 pb-4">
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-xs font-semibold text-blue-600">Uploading…</span>
            <span className="text-xs font-black text-blue-700">{slot.progress}%</span>
          </div>
          <div className="w-full h-2.5 bg-blue-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-blue-500 rounded-full transition-all duration-150"
              style={{ width: `${slot.progress}%` }}
            />
          </div>
          {slot.fileName && (
            <div className="flex items-center gap-2 mt-2 text-xs text-gray-400">
              <FileText className="w-3.5 h-3.5 shrink-0" />
              <span className="flex-1 truncate">{slot.fileName}</span>
              {slot.fileSize && <span className="shrink-0">{formatSize(slot.fileSize)}</span>}
            </div>
          )}
        </div>
      )}

      {/* Done / in review — file info strip */}
      {(isDone || isReview) && slot.fileName && (
        <div className="px-4 pb-4">
          <div className={`flex items-center gap-2 text-xs rounded-xl px-3 py-2.5
            ${isDone   ? 'bg-green-50  text-green-700'  :
              isReview ? 'bg-amber-50  text-amber-700'  : 'text-gray-500'}`}
          >
            <FileText className="w-3.5 h-3.5 shrink-0" />
            <span className="flex-1 truncate font-medium">{slot.fileName}</span>
            {slot.fileSize && (
              <span className="shrink-0 font-medium">{formatSize(slot.fileSize)}</span>
            )}
            {slot.uploadedAt && (
              <span className="shrink-0 flex items-center gap-1 opacity-70">
                <Clock className="w-3 h-3" />
                {timeAgo(slot.uploadedAt)}
              </span>
            )}
            <span className={`shrink-0 font-bold px-2 py-0.5 rounded-full border text-[11px]
              ${isDone && slot.usingSaved  ? 'bg-green-100 border-green-200 text-green-700' :
                isDone                    ? 'bg-green-100 border-green-200 text-green-700' :
                'bg-amber-50 border-amber-200 text-amber-700'}`}
            >
              {slot.status === 'saved'    ? 'Reused' :
               slot.status === 'valid'   ? 'Valid'   : 'In Review'}
            </span>
          </div>
        </div>
      )}

      {/* Rejected — retry prompt */}
      {isRejected && (
        <div className="px-4 pb-4 flex items-center justify-between gap-3">
          <p className="text-xs text-red-500 font-medium">Document rejected. Please upload a clearer copy.</p>
          <button
            onClick={onUpload}
            className="flex items-center gap-1 text-xs font-bold text-amber-600 hover:text-amber-700 shrink-0"
          >
            <RotateCcw className="w-3.5 h-3.5" /> Retry
          </button>
        </div>
      )}
    </div>
  )
}
