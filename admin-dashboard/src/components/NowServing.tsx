import { useState } from 'react'
import { CheckCircle, RotateCcw, X, Volume2 } from 'lucide-react'
import { useQueueStore } from '../store/QueueStore'
import { useAuthStore } from '../store/AuthStore'
import { SERVICE_TYPE_LABELS } from '../types'

export default function NowServing() {
  const { entries, advance } = useQueueStore()
  const user = useAuthStore((s) => s.user)
  const [busy, setBusy] = useState(false)

  // userId is a UUID string (identity-service), not a sequential number — hash
  // it to a stable pseudo-random counter assignment instead of `% 12`.
  const userIdHash = user
    ? [...user.userId].reduce((sum, ch) => sum + ch.charCodeAt(0), 0)
    : 0
  const counterNumber = String((userIdHash % 12) + 1).padStart(2, '0')

  const current = entries.find((e) => e.status === 'CALLED' || e.status === 'SERVING')
  const nextWaiting = [...entries]
    .filter((e) => e.status === 'WAITING')
    .sort((a, b) => a.positionInQueue - b.positionInQueue)[0]

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    try {
      await action()
    } catch (err) {
      console.error('[queue] action failed', err)
    } finally {
      setBusy(false)
    }
  }

  const callNext = () => {
    if (!nextWaiting) return
    run(() => advance(nextWaiting.id, 'CALLED', counterNumber))
  }

  const complete = () => {
    if (!current) return
    run(() => advance(current.id, 'COMPLETE'))
  }

  const recall = () => {
    if (!current) return
    run(() => advance(current.id, 'CALLED', counterNumber))
  }

  const noShow = () => {
    if (!current) return
    run(() => advance(current.id, 'CANCELLED'))
  }

  if (!current) {
    return (
      <div className="w-72 shrink-0 bg-white rounded-2xl shadow-sm border-2 border-slate-200 p-6 flex flex-col gap-4 animate-fade-in">
        <div className="flex items-center gap-2">
          <span className="bg-slate-400 text-white text-[10px] font-bold px-3 py-1.5 rounded-full tracking-wider">
            COUNTER IDLE
          </span>
          <span className="text-xs text-slate-500 font-medium">Counter {counterNumber}</span>
        </div>
        <div className="flex-1 flex items-center justify-center">
          <p className="text-sm text-slate-400 text-center">No ticket currently being served.</p>
        </div>
        <button
          onClick={callNext}
          disabled={!nextWaiting || busy}
          className="w-full btn-primary flex items-center justify-center gap-2"
        >
          <Volume2 size={16} />
          {nextWaiting ? 'CALL NEXT CUSTOMER' : 'QUEUE EMPTY'}
        </button>
      </div>
    )
  }

  return (
    <div className="w-72 shrink-0 bg-gradient-to-br from-green-50 to-white rounded-2xl shadow-lg border-2 border-green-400 p-6 flex flex-col gap-4 animate-fade-in relative overflow-hidden">
      <div className="absolute top-0 right-0 w-40 h-40 bg-gradient-to-br from-green-100 to-transparent rounded-full -mr-20 -mt-20 opacity-60"></div>

      <div className="relative">
        <div className="flex items-center gap-2">
          <span className="bg-green-500 text-white text-[10px] font-bold px-3 py-1.5 rounded-full tracking-wider flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 bg-white rounded-full animate-pulse"></span>
            CURRENTLY SERVING
          </span>
          <span className="text-xs text-slate-500 font-medium">Counter {counterNumber}</span>
        </div>

        <div className="mt-6">
          <p className="text-7xl font-black text-slate-900 tracking-tighter leading-none animate-slide-up">
            {current.ticketNumber}
          </p>
          <p className="text-sm text-slate-600 mt-3 font-medium">
            {SERVICE_TYPE_LABELS[current.serviceType] ?? current.serviceType}
          </p>
        </div>

        <button
          onClick={complete}
          disabled={busy}
          className="w-full btn-success mt-6 flex items-center justify-center gap-2"
        >
          <CheckCircle size={18} />
          COMPLETE SERVICE
        </button>

        <div className="flex gap-2 mt-3">
          <button
            onClick={recall}
            disabled={busy}
            className="flex-1 border-2 border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-50 font-semibold text-sm py-2.5 rounded-xl flex items-center justify-center gap-1.5 transition-all hover:shadow-sm"
          >
            <RotateCcw size={14} />
            RECALL
          </button>
          <button
            onClick={noShow}
            disabled={busy}
            className="flex-1 border-2 border-red-400 text-red-500 hover:bg-red-50 disabled:opacity-50 font-semibold text-sm py-2.5 rounded-xl flex items-center justify-center gap-1.5 transition-all hover:shadow-sm"
          >
            <X size={14} />
            NO SHOW
          </button>
        </div>

        <button
          onClick={callNext}
          disabled={!nextWaiting || busy}
          className="w-full btn-primary mt-3 flex items-center justify-center gap-2"
        >
          <Volume2 size={16} />
          CALL NEXT CUSTOMER
        </button>
      </div>
    </div>
  )
}
