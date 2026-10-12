import { useState } from 'react'
import { Search, ChevronDown, FileText, Globe, CreditCard, Heart, MoreHorizontal } from 'lucide-react'
import { useQueueStore } from '../store/QueueStore'
import { SERVICE_TYPE_LABELS } from '../types'

const serviceIcon: Record<string, { icon: typeof FileText; color: string; bg: string }> = {
  BIRTH_CERTIFICATE: { icon: FileText, color: 'text-purple-600', bg: 'bg-purple-100' },
  PASSPORT: { icon: Globe, color: 'text-blue-600', bg: 'bg-blue-100' },
  SMART_ID: { icon: CreditCard, color: 'text-indigo-600', bg: 'bg-indigo-100' },
  MARRIAGE_CERTIFICATE: { icon: Heart, color: 'text-pink-600', bg: 'bg-pink-100' },
  GENERAL_ENQUIRY: { icon: MoreHorizontal, color: 'text-slate-600', bg: 'bg-slate-100' },
}

function WaitBadge({ mins }: { mins: number }) {
  if (mins >= 30) {
    return <span className="font-semibold text-red-500 text-sm">{mins} mins</span>
  }
  if (mins >= 20) {
    return <span className="font-semibold text-amber-500 text-sm">{mins} mins</span>
  }
  return <span className="font-medium text-slate-500 text-sm">{mins} mins</span>
}

export default function QueueTable() {
  const { entries, loading } = useQueueStore()
  const [search, setSearch] = useState('')

  const waiting = entries.filter((e) => e.status === 'WAITING')
  const filtered = search.trim()
    ? waiting.filter(
        (e) =>
          e.ticketNumber.toLowerCase().includes(search.toLowerCase()) ||
          e.serviceType.toLowerCase().includes(search.toLowerCase()),
      )
    : waiting

  return (
    <div className="flex-1 bg-white rounded-2xl shadow-sm border border-slate-100 flex flex-col overflow-hidden animate-fade-in">
      <div className="p-5 border-b border-slate-100 flex items-center gap-3">
        <div className="flex-1 relative">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by ticket # or service..."
            className="input-field pl-10"
          />
        </div>
        <button className="flex items-center gap-1.5 text-sm text-slate-600 border border-slate-200 px-4 py-2.5 rounded-xl hover:bg-slate-50 transition-colors font-medium">
          All Services <ChevronDown size={14} />
        </button>
      </div>

      <div className="overflow-auto flex-1">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gradient-to-r from-slate-50 to-slate-50/50 border-b border-slate-200">
              <th className="text-left text-xs font-bold text-slate-600 uppercase tracking-wider px-5 py-3.5">
                Ticket #
              </th>
              <th className="text-left text-xs font-bold text-slate-600 uppercase tracking-wider px-5 py-3.5">
                Service Type
              </th>
              <th className="text-left text-xs font-bold text-slate-600 uppercase tracking-wider px-5 py-3.5">
                Position
              </th>
              <th className="text-right text-xs font-bold text-slate-600 uppercase tracking-wider px-5 py-3.5">
                Wait Time
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {!loading && filtered.length === 0 && (
              <tr>
                <td colSpan={4} className="px-5 py-12 text-center">
                  <div className="flex flex-col items-center gap-2">
                    <div className="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center">
                      <Search size={20} className="text-slate-400" />
                    </div>
                    <p className="text-sm text-slate-500 font-medium">No one waiting right now.</p>
                  </div>
                </td>
              </tr>
            )}
            {filtered.map((row, i) => {
              const svc = serviceIcon[row.serviceType] ?? serviceIcon.GENERAL_ENQUIRY
              const Icon = svc.icon
              return (
                <tr
                  key={row.id}
                  className={`hover:bg-blue-50/50 transition-all duration-150 group ${i % 2 === 0 ? '' : 'bg-slate-50/30'}`}
                >
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-3">
                      <div className={`${svc.bg} p-2 rounded-lg group-hover:scale-110 transition-transform`}>
                        <Icon size={15} className={svc.color} />
                      </div>
                      <span className="font-bold text-slate-800">{row.ticketNumber}</span>
                    </div>
                  </td>
                  <td className="px-5 py-4 text-slate-600 font-medium">
                    {SERVICE_TYPE_LABELS[row.serviceType] ?? row.serviceType}
                  </td>
                  <td className="px-5 py-4">
                    <span className="inline-flex items-center justify-center w-8 h-8 bg-slate-100 text-slate-700 rounded-lg font-semibold text-xs">
                      #{row.positionInQueue}
                    </span>
                  </td>
                  <td className="px-5 py-4 text-right">
                    <WaitBadge mins={row.estimatedWaitMinutes} />
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <div className="px-5 py-3.5 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500 bg-slate-50/30">
        <span className="font-medium">Showing {filtered.length} in queue</span>
        {filtered.length > 0 && (
          <span className="flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse"></span>
            Live updates
          </span>
        )}
      </div>
    </div>
  )
}
