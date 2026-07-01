import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, Share2, XCircle, MapPin, Shield } from 'lucide-react'
import toast from 'react-hot-toast'
import { useQueueStore } from '../store/QueueStore'
import { useAuthStore } from '../store/AuthStore'
import axios from '../api/Axios'

export default function TicketPage() {
  const navigate = useNavigate()
  // ✅ Fixed: activeTicket → myTicket
  const ticket   = useQueueStore((s) => s.myTicket)
  const user     = useAuthStore((s) => s.user)

  // Redirect to live status when called
  useEffect(() => {
    if (ticket?.status === 'CALLED') navigate('/queue-status')
  }, [ticket?.status])

  if (!ticket) {
    return (
      <div className="flex flex-col min-h-screen items-center justify-center gap-4 px-8 text-center">
        <p className="text-gray-500">No active ticket. Join a queue first.</p>
        <button
          onClick={() => navigate('/branches')}
          className="bg-green-500 text-white font-bold rounded-2xl py-3 px-8 hover:bg-green-600"
        >
          Find a Branch
        </button>
      </div>
    )
  }

  const handleCancel = async () => {
    try {
      await axios.delete(`/api/queue/${ticket.id}/cancel`)
      // ✅ Fixed: setTicket → setMyTicket
      useQueueStore.getState().setMyTicket(null)
      toast.success('Ticket cancelled')
      navigate('/home')
    } catch {
      toast.error('Failed to cancel. Please try again.')
    }
  }

  const issuedTime = new Date(ticket.joinedAt).toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit' })
  const issuedDate = new Date(ticket.joinedAt).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
  const seats = Array.from({ length: Math.min(ticket.totalInQueue ?? 5, 5) }, (_, i) => i + 1)

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white flex items-center justify-between px-5 py-4 border-b border-gray-100">
        <div className="w-9 h-9 bg-green-500 rounded-xl flex items-center justify-center">
          <Shield className="w-5 h-5 text-white" />
        </div>
        <span className="font-semibold text-gray-900">Active Ticket</span>
        <div className="w-9" />
      </div>

      <div className="px-5 py-4 flex flex-col gap-4 pb-24">
        {/* Title */}
        <div>
          <h3 className="font-bold text-gray-900">{ticket.serviceType?.replace(/_/g, ' ')}</h3>
          <div className="flex items-center gap-1 mt-0.5">
            <MapPin className="w-3.5 h-3.5 text-green-500" />
            <span className="text-sm text-gray-500">{ticket.branchName}</span>
          </div>
        </div>

        {/* Ticket card */}
        <div className="bg-white border-2 border-green-500 rounded-2xl overflow-hidden">
          <div className="h-1.5 bg-green-500" />
          <div className="p-5">
            <p className="text-xs font-bold text-gray-400 tracking-widest text-center uppercase">Your Ticket Number</p>
            <h1 className="text-6xl font-black text-gray-900 text-center my-2">{ticket.ticketNumber}</h1>
            <div className="flex items-center justify-center gap-6 border-t border-gray-100 pt-3">
              <div className="text-center">
                <p className="text-xs text-gray-400 uppercase font-bold">Issued At</p>
                <p className="text-sm font-bold text-gray-800">{issuedTime}</p>
              </div>
              <div className="w-px h-6 bg-gray-200" />
              <div className="text-center">
                <p className="text-xs text-gray-400 uppercase font-bold">Date</p>
                <p className="text-sm font-bold text-gray-800">{issuedDate}</p>
              </div>
            </div>

            {/* QR code placeholder */}
            <div className="mt-4 mx-auto w-28 h-28 bg-gray-100 rounded-xl flex items-center justify-center border border-gray-200">
              <div className="grid grid-cols-3 gap-1 p-2">
                {Array.from({ length: 9 }).map((_, i) => (
                  <div key={i} className={`w-4 h-4 rounded-sm ${[0,2,4,6,8].includes(i) ? 'bg-gray-800' : 'bg-white'}`} />
                ))}
              </div>
            </div>
            <p className="text-xs text-gray-400 text-center mt-1">{ticket.qrCode ?? 'DHA-ST-99234812'}</p>
          </div>
        </div>

        {/* Position + wait */}
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-white border border-gray-200 rounded-2xl p-4 flex flex-col gap-1">
            <div className="flex items-center gap-2 text-gray-500">
              <span className="text-xs font-bold uppercase tracking-wider">Position</span>
            </div>
            <p className="text-2xl font-black text-gray-900">
              {ticket.positionInQueue} <span className="text-base font-normal text-gray-400">of {ticket.totalInQueue}</span>
            </p>
          </div>
          <div className="bg-green-50 border border-green-100 rounded-2xl p-4 flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-green-600">Est. Wait</span>
            </div>
            <p className="text-2xl font-black text-green-600">
              {ticket.estimatedWaitMinutes} <span className="text-base font-normal">min</span>
            </p>
          </div>
        </div>

        {/* Queue simulation */}
        <div className="bg-white border border-gray-200 rounded-2xl p-4">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-bold text-gray-400 tracking-widest uppercase">Queue Simulation</span>
            <span className="text-xs font-bold text-green-500 border border-green-200 bg-green-50 rounded-full px-2 py-0.5">Live View</span>
          </div>
          <div className="flex gap-3 overflow-x-auto pb-1">
            {seats.map((n) => (
              <div key={n} className="flex flex-col items-center gap-1 shrink-0">
                <div className={`w-9 h-9 rounded-lg flex items-center justify-center text-lg
                  ${n === ticket.positionInQueue ? 'bg-green-500' : 'bg-gray-100'}`}>
                  🪑
                </div>
                <span className="text-xs text-gray-400">{n}</span>
              </div>
            ))}
          </div>
          <p className="text-xs text-gray-400 mt-2 italic">Scroll to see the full row. Counter is at the far left.</p>
        </div>

        {/* Notify */}
        <button className="w-full flex items-center gap-3 border border-gray-200 bg-white rounded-2xl px-4 py-3 hover:bg-gray-50">
          <Bell className="w-5 h-5 text-gray-600" />
          <span className="text-sm font-semibold text-gray-800">Notify me when 5 before</span>
        </button>

        {/* Share + Cancel */}
        <div className="grid grid-cols-2 gap-3">
          <button className="flex items-center justify-center gap-2 border border-gray-200 bg-white rounded-2xl py-3.5 text-sm font-semibold text-gray-700 hover:bg-gray-50">
            <Share2 className="w-4 h-4" /> Share
          </button>
          <button
            onClick={handleCancel}
            className="flex items-center justify-center gap-2 border border-red-200 rounded-2xl py-3.5 text-sm font-semibold text-red-500 hover:bg-red-50"
          >
            <XCircle className="w-4 h-4" /> Cancel
          </button>
        </div>

        <p className="text-xs text-gray-400 text-center">
          Please remain within 500m of the branch to keep your position. You will receive a push notification when you are 3rd in line.
        </p>
      </div>
    </div>
  )
}