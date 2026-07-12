import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  User, FileText, ChevronRight, LogOut, Shield,
  Bell, Globe, Upload, Trash2, AlertCircle, ArrowLeft
} from 'lucide-react'
import { DocumentListSkeleton } from '../components/Skeleton'
import toast from 'react-hot-toast'
import { useAuthStore } from '../store/AuthStore'
import { documentService } from '../api/Api'
import type { Document } from '../types'

const statusStyle: Record<string, string> = {
  VALID:     'text-green-600 bg-green-50 border-green-200',
  REJECTED:  'text-red-500 bg-red-50 border-red-200',
  PENDING:   'text-yellow-600 bg-yellow-50 border-yellow-200',
  IN_REVIEW: 'text-blue-600 bg-blue-50 border-blue-200',
}

const DOC_LABELS: Record<string, string> = {
  ID_DOCUMENT:          'SA ID Document',
  PROOF_OF_RESIDENCE:   'Proof of Residence',
  BIRTH_CERTIFICATE:    'Birth Certificate',
  PASSPORT:             'Passport',
  MARRIAGE_CERTIFICATE: 'Marriage Certificate',
}

const DOC_TYPES = [
  'ID_DOCUMENT',
  'PROOF_OF_RESIDENCE',
  'BIRTH_CERTIFICATE',
  'PASSPORT',
  'MARRIAGE_CERTIFICATE',
]

const formatSize = (bytes: number) => {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

type Tab = 'profile' | 'documents' | 'settings'

export default function SettingsPage() {
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()
  const [activeTab, setActiveTab] = useState<Tab>('profile')

  // Documents state
  const [docs, setDocs] = useState<Document[]>([])
  const [docsLoading, setDocsLoading] = useState(false)
  const [docsError, setDocsError] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [selectedDocType, setSelectedDocType] = useState(DOC_TYPES[0])
  const fileInputRef = useRef<HTMLInputElement>(null)

  const fetchDocs = async () => {
    setDocsLoading(true)
    setDocsError(false)
    try {
      const res = await documentService.getMyDocs()
      setDocs(res.data.data ?? [])
    } catch {
      setDocsError(true)
    } finally {
      setDocsLoading(false)
    }
  }

  useEffect(() => {
    if (activeTab === 'documents') fetchDocs()
  }, [activeTab])

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const formData = new FormData()
    formData.append('file', file)
    formData.append('documentType', selectedDocType)

    setUploading(true)
    try {
      await documentService.upload(formData)
      toast.success('Document uploaded successfully')
      fetchDocs()
    } catch {
      toast.error('Upload failed. Please try again.')
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleDelete = async (id: number) => {
    try {
      await documentService.delete(String(id))
      setDocs((prev) => prev.filter((d) => d.id !== id))
      toast.success('Document removed')
    } catch {
      toast.error('Could not delete document.')
    }
  }

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
    toast.success('Logged out successfully')
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'profile',   label: 'Profile'    },
    { key: 'documents', label: 'Documents'  },
    { key: 'settings',  label: 'Settings'  },
  ]

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white flex items-center justify-between px-5 h-20 border-b border-gray-100">
        <button
          onClick={() => navigate(-1)}
          className="w-9 h-9 flex items-center justify-center rounded-xl hover:bg-gray-100 transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-600" />
        </button>
        <span className="font-semibold text-gray-900">My Account</span>
        <div className="w-9" />
      </div>

      {/* Avatar + name */}
      <div className="bg-white px-5 py-6 flex flex-col items-center gap-3 border-b border-gray-100">
        <div className="w-20 h-20 rounded-full bg-green-100 flex items-center justify-center">
          <User className="w-10 h-10 text-green-600" />
        </div>
        <div className="text-center">
          <h2 className="text-xl font-bold text-gray-900">{user?.name ?? 'Citizen'}</h2>
          <div className="flex items-center justify-center gap-1.5 mt-0.5">
            <Shield className="w-4 h-4 text-green-500" />
            <span className="text-sm text-green-600 font-medium">
              {user?.verified ? 'Verified Citizen Account' : 'Unverified Account'}
            </span>
          </div>
          <p className="text-sm text-gray-400 mt-0.5">{user?.email}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="bg-white flex border-b border-gray-100">
        {tabs.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`flex-1 py-3 text-sm font-semibold transition-colors ${
              activeTab === key
                ? 'text-green-500 border-b-2 border-green-500'
                : 'text-gray-400'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="px-5 md:px-8 py-4 md:py-6 flex flex-col gap-4 pb-8 flex-1 max-w-3xl mx-auto w-full">

        {/* ── PROFILE TAB ── */}
        {activeTab === 'profile' && (
          <>
            <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-4">
              {[
                { label: 'Full Name',    value: user?.name       ?? '—' },
                { label: 'Phone',        value: user?.phone      ?? '—' },
                { label: 'Email',        value: user?.email      ?? '—' },
                { label: 'SA ID Number', value: user?.saIdNumber ? `${user.saIdNumber.slice(0,6)}*******` : '—' },
                { label: 'Province',     value: user?.province   ?? '—' },
              ].map(({ label, value }) => (
                <div key={label} className="flex items-center justify-between">
                  <div>
                    <p className="text-xs text-gray-400 uppercase font-bold tracking-wider">{label}</p>
                    <p className="text-sm font-semibold text-gray-800 mt-0.5">{value}</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-gray-300" />
                </div>
              ))}
            </div>

            <button
              onClick={handleLogout}
              className="w-full flex items-center justify-center gap-2 border-2 border-red-200 text-red-500 font-semibold py-4 rounded-2xl hover:bg-red-50 transition-colors"
            >
              <LogOut className="w-4 h-4" /> Sign Out
            </button>
          </>
        )}

        {/* ── DOCUMENTS TAB ── */}
        {activeTab === 'documents' && (
          <>
            <p className="text-sm text-gray-500">
              Documents stored here are reused for future applications automatically when still valid.
            </p>

            {/* Upload section */}
            <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-3">
              <p className="text-sm font-semibold text-gray-800">Upload a Document</p>
              <select
                value={selectedDocType}
                onChange={(e) => setSelectedDocType(e.target.value)}
                className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-green-500"
              >
                {DOC_TYPES.map((t) => (
                  <option key={t} value={t}>{DOC_LABELS[t] ?? t}</option>
                ))}
              </select>

              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                className="hidden"
                onChange={handleUpload}
              />
              <button
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className="w-full flex items-center justify-center gap-2 border-2 border-dashed border-green-300 rounded-xl py-3 text-green-600 font-semibold text-sm hover:bg-green-50 transition-colors disabled:opacity-50"
              >
                <Upload className="w-4 h-4" />
                {uploading ? 'Uploading…' : 'Choose File & Upload'}
              </button>
              <p className="text-xs text-gray-400 text-center">PDF, JPG, or PNG — max 10 MB</p>
            </div>

            {/* Docs list */}
            {docsLoading && <DocumentListSkeleton />}

            {docsError && !docsLoading && (
              <div className="flex flex-col items-center gap-3 py-6 text-center">
                <AlertCircle className="w-8 h-8 text-red-400" />
                <p className="text-sm text-gray-500">Could not load documents.</p>
                <button
                  onClick={fetchDocs}
                  className="text-sm text-green-500 font-semibold"
                >
                  Try again
                </button>
              </div>
            )}

            {!docsLoading && !docsError && docs.length === 0 && (
              <p className="text-sm text-gray-400 text-center py-6">No documents uploaded yet.</p>
            )}

            {!docsLoading && !docsError && docs.length > 0 && (
              <div className="flex flex-col gap-3">
                {docs.map((doc) => {
                  const docType = doc.documentType ?? doc.docType ?? ''
                  const status  = doc.validationStatus
                  return (
                    <div key={doc.id} className="bg-white border border-gray-200 rounded-2xl p-4 flex items-start gap-3">
                      <div className="w-10 h-10 bg-gray-100 rounded-xl flex items-center justify-center shrink-0">
                        <FileText className="w-5 h-5 text-gray-500" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="text-sm font-bold text-gray-800">
                            {DOC_LABELS[docType] ?? docType}
                          </p>
                          <span className={`text-xs font-bold px-2 py-0.5 rounded-full border ${statusStyle[status] ?? statusStyle.PENDING}`}>
                            {status}
                          </span>
                        </div>
                        <p className="text-xs text-gray-400 truncate mt-0.5">{doc.originalName ?? doc.fileName}</p>
                        <p className="text-xs text-gray-400 mt-0.5">{formatSize(doc.fileSize)}</p>
                        {doc.expiresAt && (
                          <p className="text-xs text-gray-400 mt-0.5">
                            Expires: {new Date(doc.expiresAt).toLocaleDateString('en-ZA')}
                          </p>
                        )}
                        {doc.rejectionReason && (
                          <p className="text-xs text-red-400 mt-0.5">{doc.rejectionReason}</p>
                        )}
                        {doc.validationNotes && (
                          <p className="text-xs text-gray-400 mt-0.5">{doc.validationNotes}</p>
                        )}
                      </div>
                      <button
                        onClick={() => handleDelete(doc.id)}
                        className="p-1.5 rounded-lg hover:bg-red-50 transition-colors shrink-0 mt-0.5"
                        aria-label="Delete document"
                      >
                        <Trash2 className="w-4 h-4 text-red-400" />
                      </button>
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}

        {/* ── SETTINGS TAB ── */}
        {activeTab === 'settings' && (
          <>
            <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col divide-y divide-gray-100">
              {[
                { icon: Bell,   label: 'Notifications',  sub: 'Manage push & SMS alerts' },
                { icon: Globe,  label: 'Language',        sub: 'English' },
                { icon: Shield, label: 'Privacy & POPIA', sub: 'Data usage and consent' },
              ].map(({ icon: Icon, label, sub }) => (
                <button key={label} className="flex items-center gap-3 py-3 w-full text-left first:pt-0 last:pb-0">
                  <div className="w-9 h-9 bg-gray-100 rounded-xl flex items-center justify-center">
                    <Icon className="w-4 h-4 text-gray-600" />
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-semibold text-gray-800">{label}</p>
                    <p className="text-xs text-gray-400">{sub}</p>
                  </div>
                  <ChevronRight className="w-4 h-4 text-gray-300" />
                </button>
              ))}
            </div>

            <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col divide-y divide-gray-100">
              <button className="flex items-center gap-3 py-3 w-full text-left first:pt-0">
                <div className="w-9 h-9 bg-gray-100 rounded-xl flex items-center justify-center">
                  <FileText className="w-4 h-4 text-gray-600" />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-semibold text-gray-800">Terms of Service</p>
                  <p className="text-xs text-gray-400">DHA usage terms</p>
                </div>
                <ChevronRight className="w-4 h-4 text-gray-300" />
              </button>
              <div className="py-3 last:pb-0">
                <p className="text-xs text-gray-400 text-center">
                  DHA Smart Queue v1.0 · Republic of South Africa
                </p>
              </div>
            </div>

            <button
              onClick={handleLogout}
              className="w-full flex items-center justify-center gap-2 border-2 border-red-200 text-red-500 font-semibold py-4 rounded-2xl hover:bg-red-50 transition-colors"
            >
              <LogOut className="w-4 h-4" /> Sign Out
            </button>
          </>
        )}
      </div>
    </div>
  )
}
