import { Bell } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { MapPin, Clock, Users, Info, Map, MessageSquare, Shield } from 'lucide-react'
import { useQueueStore } from '../store/QueueStore'

const STEPS = ['WAITING', 'CALLED', 'SERVICE', 'COMPLETE']

export default function QueueStatusPage() {
  const navigate = useNavigate()
  // ✅ Fixed: activeTicket → myTicket
  const ticket   = useQueueStore((s) => s.myTicket)

  if (!ticket) {
    return (
      <div className="flex flex-col min-h-screen items-center justify-center px-8 text-center gap-4">
        <p className="text-gray-500">No active queue session.</p>
        <button
          onClick={() => navigate('/home')}
          className="bg-green-500 text-white font-bold rounded-2xl py-3 px-8 hover:bg-green-600"
        >
          Go Home
        </button>
      </div>
    )
  }

  const stepIndex = STEPS.indexOf(
    ticket.status === 'SERVING'  ? 'SERVICE'
    : ticket.status === 'COMPLETE' ? 'COMPLETE'
    : ticket.status === 'CALLED'   ? 'CALLED'
    : 'WAITING'
  )

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white flex items-center justify-between px-5 py-4 border-b border-gray-100">
        <div className="w-9 h-9 bg-green-500 rounded-xl flex items-center justify-center">
          <Shield className="w-5 h-5 text-white" />
        </div>
        <span className="font-semibold text-gray-900">Live Queue Status</span>
        <div className="w-9" />
      </div>

      {/* Step progress */}
      <div className="bg-white px-5 py-4 border-b border-gray-100 flex items-center">
        {STEPS.map((step, i) => (
          <div key={step} className="flex items-center flex-1">
            <div className="flex flex-col items-center gap-1">
              <div className={`
                w-8 h-8 rounded-full border-2 flex items-center justify-center transition-all
                ${i < stepIndex  ? 'bg-green-500 border-green-500' : ''}
                ${i === stepIndex ? 'border-green-500 bg-white ring-4 ring-green-100' : ''}
                ${i > stepIndex  ? 'border-gray-300 bg-white' : ''}
              `}>
                {i < stepIndex
                  ? <span className="text-white text-xs font-bold">✓</span>
                  : i === stepIndex
                    ? <div className="w-3 h-3 rounded-full bg-green-500" />
                    : null
                }
              </div>
              <span className={`text-[10px] font-bold ${i === stepIndex ? 'text-green-500' : 'text-gray-400'}`}>
                {step}
              </span>
            </div>
            {i < STEPS.length - 1 && (
              <div className={`h-0.5 flex-1 mb-4 mx-1 ${i < stepIndex ? 'bg-green-500' : 'bg-gray-200'}`} />
            )}
          </div>
        ))}
      </div>

      <div className="px-5 py-4 flex flex-col gap-4 pb-24">
        {/* Now calling banner */}
        <div className="flex items-center gap-3 bg-orange-50 border border-orange-200 rounded-2xl px-4 py-3">
          <div className="w-8 h-8 bg-orange-400 rounded-full flex items-center justify-center shrink-0">
            <Bell className="w-4 h-4 text-white" />
          </div>
          <div className="flex-1">
            <p className="text-xs font-bold text-orange-700">Now Calling</p>
            <p className="text-sm text-orange-800">
              Ticket <strong>A118</strong> to <strong>Counter 4</strong>
            </p>
          </div>
          <span className="text-orange-400 text-lg">›</span>
        </div>

        {/* Main status card */}
        <div className="bg-green-50 border border-green-100 rounded-2xl p-5 text-center">
          <span className="text-xs font-bold text-green-600 bg-white border border-green-200 rounded-full px-3 py-1">
            Current Status
          </span>
          <h2 className="text-2xl font-black text-gray-900 mt-3 mb-1">
            {ticket.status === 'CALLED' ? 'You are next!' : 'You are waiting...'}
          </h2>
          <p className="text-gray-500 text-sm mb-4">Please move toward the service area.</p>
          <p className="text-7xl font-black text-green-500">{ticket.ticketNumber}</p>
          <p className="text-xs font-bold text-green-400 tracking-widest uppercase mt-1">Your Ticket Number</p>

          <div className="grid grid-cols-2 gap-4 mt-5 border-t border-green-100 pt-4">
            <div>
              <p className="text-xs font-bold text-gray-400 uppercase">Est. Wait</p>
              <p className="text-2xl font-black text-gray-900">{ticket.estimatedWaitMinutes} min</p>
            </div>
            <div>
              <p className="text-xs font-bold text-gray-400 uppercase">Ahead of You</p>
              <p className="text-2xl font-black text-gray-900">
                {Math.max((ticket.positionInQueue ?? 1) - 1, 0)}{' '}
                <span className="text-base font-normal">
                  {(ticket.positionInQueue ?? 1) - 1 === 1 ? 'Person' : 'People'}
                </span>
              </p>
            </div>
          </div>
        </div>

        {/* Info grid */}
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-1">
            <div className="flex items-center gap-1.5 text-gray-400">
              <MapPin className="w-3.5 h-3.5" />
              <span className="text-xs font-bold uppercase tracking-wider">Location</span>
            </div>
            <p className="text-sm font-bold text-gray-800">{ticket.branchName}</p>
            <p className="text-xs text-gray-400">{ticket.branchAddress}</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-1">
            <div className="flex items-center gap-1.5 text-gray-400">
              <Clock className="w-3.5 h-3.5" />
              <span className="text-xs font-bold uppercase tracking-wider">Wait Time</span>
            </div>
            <p className="text-sm font-bold text-gray-800">~ {ticket.estimatedWaitMinutes} Minutes</p>
            <p className="text-xs text-gray-400">Live estimate</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-1">
            <div className="flex items-center gap-1.5 text-gray-400">
              <Users className="w-3.5 h-3.5" />
              <span className="text-xs font-bold uppercase tracking-wider">Queue Size</span>
            </div>
            <p className="text-sm font-bold text-gray-800">{ticket.totalInQueue} people</p>
            <p className="text-xs text-gray-400">In queue ahead</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-1">
            <div className="flex items-center gap-1.5 text-gray-400">
              <Info className="w-3.5 h-3.5" />
              <span className="text-xs font-bold uppercase tracking-wider">Counter</span>
            </div>
            <p className="text-sm font-bold text-gray-800">{ticket.counterNumber ?? 'TBA'}</p>
            <p className="text-xs text-gray-400">Standard Service</p>
          </div>
        </div>

        <button
          onClick={() => navigate('/branches')}
          className="w-full bg-green-500 text-white font-bold py-4 rounded-2xl flex items-center justify-center gap-2 hover:bg-green-600"
        >
          <Map className="w-4 h-4" /> Back to Branch Map
        </button>

        <button
          onClick={() => navigate('/assistant')}
          className="w-full flex items-center justify-center gap-2 border border-gray-200 bg-white rounded-2xl py-4 text-sm font-semibold text-gray-700 hover:bg-gray-50"
        >
          <MessageSquare className="w-4 h-4" /> Need Help? Ask Assistant
        </button>

        <p className="text-xs text-gray-400 text-center">
          Your session is active. If you leave the branch premises, your position in the queue may be forfeit.
        </p>
      </div>
    </div>
  )
}

