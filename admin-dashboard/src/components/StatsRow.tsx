import { useEffect } from 'react'
import { Users, Clock, CheckCircle, Shield } from 'lucide-react'
import { useQueueStore } from '../store/QueueStore'

export default function StatsRow() {
  const { stats, refreshStats } = useQueueStore()

  useEffect(() => {
    const interval = setInterval(() => refreshStats(), 15000)
    return () => clearInterval(interval)
  }, [refreshStats])

  const tiles = [
    {
      label: 'In Queue',
      value: stats ? String(stats.inQueue) : '—',
      sub: 'Active waiting citizens',
      icon: Users,
      bg: 'bg-white',
      iconColor: 'text-blue-500',
      iconBg: 'bg-blue-50',
    },
    {
      label: 'Avg. Wait',
      value: stats ? `${stats.avgWaitMinutes}m` : '—',
      sub: 'Estimated for the current queue',
      icon: Clock,
      bg: 'bg-white',
      iconColor: 'text-blue-500',
      iconBg: 'bg-blue-50',
    },
    {
      label: 'Total Served',
      value: stats ? String(stats.totalServedToday) : '—',
      sub: 'Customers processed today',
      icon: CheckCircle,
      bg: 'bg-green-50',
      iconColor: 'text-green-600',
      iconBg: 'bg-green-100',
    },
    {
      label: 'Efficiency',
      value: stats ? `${stats.efficiencyPercent}%` : '—',
      sub: 'Completed vs. cancelled today',
      icon: Shield,
      bg: 'bg-white',
      iconColor: 'text-blue-500',
      iconBg: 'bg-blue-50',
    },
  ]

  return (
    <div className="grid grid-cols-4 gap-4 animate-fade-in">
      {tiles.map((s) => {
        const Icon = s.icon
        return (
          <div
            key={s.label}
            className={`${s.bg} rounded-2xl p-5 shadow-sm border border-slate-100 relative overflow-hidden group card-hover`}
          >
            <div className="absolute top-0 right-0 w-32 h-32 bg-gradient-to-br from-slate-50 to-transparent rounded-full -mr-16 -mt-16 opacity-50 group-hover:opacity-70 transition-opacity"></div>

            <div className="relative">
              <div className="flex items-start justify-between mb-4">
                <div className={`${s.iconBg} p-2.5 rounded-xl`}>
                  <Icon size={20} className={s.iconColor} />
                </div>
                <span className="text-[10px] font-bold text-slate-500 bg-slate-100 px-2.5 py-1 rounded-full flex items-center gap-1">
                  <span className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse"></span>
                  Live
                </span>
              </div>

              <p className="text-4xl font-bold text-slate-900 leading-none mb-2 tracking-tight">
                {s.value}
              </p>
              <p className="text-sm font-semibold text-slate-700 mb-0.5">{s.label}</p>
              <p className="text-xs text-slate-500">{s.sub}</p>
            </div>
          </div>
        )
      })}
    </div>
  )
}
