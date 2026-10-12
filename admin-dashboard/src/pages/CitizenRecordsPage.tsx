import { useEffect, useRef, useState } from 'react'
import { AlertTriangle, Camera, Check, Plus, Search, Trash2, X } from 'lucide-react'
import { citizenRecordService } from '../api/Api'
import type { CitizenRecord } from '../types'

/**
 * DEV ONLY: stands in for Home Affairs' National Population Register.
 * In a real deployment citizen records and ID photos come from DHA's own
 * systems; this screen exists so the prototype has records (and the photos
 * a citizen's live face scan is compared against) to test with.
 */

const EMPTY: CitizenRecord = {
  saIdNumber: '', firstName: '', middleName: '', lastName: '', dateOfBirth: '',
  gender: '', nationality: 'South African', citizenshipStatus: 'Citizen by birth',
  phoneNumber: '', email: '', residentialAddress: '', hasPhoto: false,
}

function errorMessage(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export default function CitizenRecordsPage() {
  const [records, setRecords] = useState<CitizenRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<CitizenRecord | null>(null)
  const [isNew, setIsNew] = useState(false)
  const [photoUrl, setPhotoUrl] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [status, setStatus] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const load = async () => {
    setLoading(true)
    try {
      const res = await citizenRecordService.list()
      setRecords(res.data.data ?? [])
    } catch (err) {
      setStatus({ kind: 'error', text: errorMessage(err, 'Could not load citizen records.') })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  // Preview the photo on file for the record being edited.
  useEffect(() => {
    let url: string | null = null
    setPhotoUrl(null)
    if (editing?.hasPhoto && !isNew) {
      citizenRecordService.getPhoto(editing.saIdNumber)
        .then((res) => { url = URL.createObjectURL(res.data); setPhotoUrl(url) })
        .catch(() => setPhotoUrl(null))
    }
    return () => { if (url) URL.revokeObjectURL(url) }
  }, [editing?.saIdNumber, editing?.hasPhoto, isNew])

  const open = (record: CitizenRecord | null) => {
    setStatus(null)
    setIsNew(record === null)
    setEditing(record ? { ...record } : { ...EMPTY })
  }

  const update = (field: keyof CitizenRecord, value: string) =>
    setEditing((r) => (r ? { ...r, [field]: value } : r))

  const save = async () => {
    if (!editing) return
    if (!/^\d{13}$/.test(editing.saIdNumber)) {
      setStatus({ kind: 'error', text: 'SA ID number must be 13 digits.' }); return
    }
    if (!editing.firstName.trim() || !editing.lastName.trim()) {
      setStatus({ kind: 'error', text: 'First and last name are required.' }); return
    }
    setSaving(true)
    try {
      const res = await citizenRecordService.save({ ...editing, dateOfBirth: editing.dateOfBirth || null })
      setEditing(res.data.data)
      setIsNew(false)
      setStatus({ kind: 'ok', text: 'Record saved.' })
      load()
    } catch (err) {
      setStatus({ kind: 'error', text: errorMessage(err, 'Could not save the record.') })
    } finally {
      setSaving(false)
    }
  }

  const uploadPhoto = async (file: File) => {
    if (!editing) return
    setSaving(true)
    try {
      await citizenRecordService.uploadPhoto(editing.saIdNumber, file)
      setEditing({ ...editing, hasPhoto: true })
      setPhotoUrl(URL.createObjectURL(file))
      setStatus({ kind: 'ok', text: 'Photo saved. This citizen can now face-verify.' })
      load()
    } catch (err) {
      setStatus({ kind: 'error', text: errorMessage(err, 'Could not save the photo.') })
    } finally {
      setSaving(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const removePhoto = async () => {
    if (!editing) return
    setSaving(true)
    try {
      await citizenRecordService.deletePhoto(editing.saIdNumber)
      setEditing({ ...editing, hasPhoto: false })
      setPhotoUrl(null)
      setStatus({ kind: 'ok', text: 'Photo removed.' })
      load()
    } catch (err) {
      setStatus({ kind: 'error', text: errorMessage(err, 'Could not remove the photo.') })
    } finally {
      setSaving(false)
    }
  }

  const q = query.trim().toLowerCase()
  const visible = records.filter((r) =>
    !q || r.saIdNumber.includes(q) || `${r.firstName} ${r.lastName}`.toLowerCase().includes(q))

  const field = (label: string, key: keyof CitizenRecord, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <label className="flex flex-col gap-1">
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <input
        value={(editing?.[key] as string | null) ?? ''}
        onChange={(e) => update(key, e.target.value)}
        className="border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40 disabled:bg-slate-50"
        {...props}
      />
    </label>
  )

  return (
    <main className="flex-1 p-6">
      <div className="max-w-6xl mx-auto flex flex-col gap-5 animate-fade-in">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <h1 className="text-xl font-bold text-slate-900">Citizen Records</h1>
          <button
            onClick={() => open(null)}
            className="flex items-center gap-1.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold px-4 py-2 rounded-lg"
          >
            <Plus size={16} /> Add citizen
          </button>
        </div>

        <div className="flex items-start gap-3 bg-amber-50 border border-amber-200 text-amber-900 rounded-xl px-4 py-3 text-sm">
          <AlertTriangle size={18} className="shrink-0 mt-0.5" />
          <p>
            <strong>Development tool.</strong> This stands in for Home Affairs' population register so the
            prototype has records to test with. A citizen's live face scan at registration and sign-in is
            compared against the photo loaded here. In production this data would come from DHA's own systems.
          </p>
        </div>

        <div className="grid gap-5 lg:grid-cols-[1fr_380px]">
          {/* ── Record list ── */}
          <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
            <div className="flex items-center gap-2 px-4 py-3 border-b border-slate-100">
              <Search size={16} className="text-slate-400" />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search by name or SA ID"
                className="flex-1 text-sm focus:outline-none"
              />
            </div>
            {loading ? (
              <p className="px-4 py-6 text-sm text-slate-500">Loading…</p>
            ) : visible.length === 0 ? (
              <p className="px-4 py-6 text-sm text-slate-500">No citizen records found.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-slate-50 text-left text-xs text-slate-500">
                    <tr>
                      <th className="px-4 py-2 font-semibold">Name</th>
                      <th className="px-4 py-2 font-semibold">SA ID number</th>
                      <th className="px-4 py-2 font-semibold">Photo</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {visible.map((r) => (
                      <tr
                        key={r.saIdNumber}
                        onClick={() => open(r)}
                        className={`cursor-pointer hover:bg-slate-50 ${editing?.saIdNumber === r.saIdNumber ? 'bg-blue-50' : ''}`}
                      >
                        <td className="px-4 py-2.5 font-medium text-slate-800">{r.firstName} {r.lastName}</td>
                        <td className="px-4 py-2.5 font-mono text-slate-600">{r.saIdNumber}</td>
                        <td className="px-4 py-2.5">
                          {r.hasPhoto
                            ? <span className="inline-flex items-center gap-1 text-green-700"><Check size={14} /> On file</span>
                            : <span className="text-slate-400">Missing</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* ── Editor ── */}
          {editing && (
            <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5 flex flex-col gap-4 self-start">
              <div className="flex items-center justify-between">
                <h2 className="font-bold text-slate-900">{isNew ? 'New citizen' : `${editing.firstName} ${editing.lastName}`}</h2>
                <button onClick={() => setEditing(null)} className="text-slate-400 hover:text-slate-700" aria-label="Close">
                  <X size={18} />
                </button>
              </div>

              {!isNew && (
                <div className="flex items-center gap-4">
                  <div className="w-24 h-28 rounded-xl bg-slate-100 overflow-hidden flex items-center justify-center shrink-0">
                    {photoUrl
                      ? <img src={photoUrl} alt="ID photo on file" className="w-full h-full object-cover" />
                      : <Camera size={22} className="text-slate-400" />}
                  </div>
                  <div className="flex flex-col gap-2">
                    <input
                      ref={fileRef}
                      type="file"
                      accept="image/jpeg,image/png"
                      className="hidden"
                      onChange={(e) => { const f = e.target.files?.[0]; if (f) uploadPhoto(f) }}
                    />
                    <button
                      onClick={() => fileRef.current?.click()}
                      disabled={saving}
                      className="text-sm font-semibold text-blue-600 hover:text-blue-800 disabled:opacity-50 text-left"
                    >
                      {editing.hasPhoto ? 'Replace photo' : 'Upload photo'}
                    </button>
                    {editing.hasPhoto && (
                      <button
                        onClick={removePhoto}
                        disabled={saving}
                        className="flex items-center gap-1 text-sm text-red-600 hover:text-red-800 disabled:opacity-50"
                      >
                        <Trash2 size={14} /> Remove photo
                      </button>
                    )}
                    <p className="text-xs text-slate-400">JPG or PNG, max 5MB. Clear, front-facing.</p>
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <div className="col-span-2">{field('SA ID number', 'saIdNumber', { disabled: !isNew, inputMode: 'numeric', maxLength: 13 })}</div>
                {field('First name', 'firstName')}
                {field('Last name', 'lastName')}
                {field('Middle name', 'middleName')}
                {field('Date of birth', 'dateOfBirth', { type: 'date' })}
                {field('Gender', 'gender')}
                {field('Nationality', 'nationality')}
                <div className="col-span-2">{field('Citizenship status', 'citizenshipStatus')}</div>
                {field('Phone number', 'phoneNumber')}
                {field('Email', 'email', { type: 'email' })}
                <div className="col-span-2">{field('Residential address', 'residentialAddress')}</div>
              </div>

              {isNew && <p className="text-xs text-slate-500">Save the record first, then add the photo.</p>}

              {status && (
                <p className={`text-sm ${status.kind === 'ok' ? 'text-green-700' : 'text-red-600'}`}>{status.text}</p>
              )}

              <button
                onClick={save}
                disabled={saving}
                className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold py-2.5 rounded-lg disabled:opacity-50"
              >
                {saving ? 'Saving…' : 'Save record'}
              </button>
            </div>
          )}
        </div>
      </div>
    </main>
  )
}
