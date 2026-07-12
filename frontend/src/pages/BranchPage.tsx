import { useNavigate, useSearchParams } from 'react-router-dom'
import BranchPicker from '../components/BranchPicker'
import { useQueueStore } from '../store/QueueStore'
import type { ServiceCode } from '../types'

export default function BranchPage() {
  const navigate       = useNavigate()
  const [searchParams] = useSearchParams()
  // Falls back to a generic "no specific service" marker for direct branch-map visits (e.g. bottom-nav "Map" tab) that aren't tied to a catalog service.
  const serviceType: ServiceCode = searchParams.get('serviceType') || 'GENERAL_ENQUIRY'
  const setMyTicket    = useQueueStore((s) => s.setMyTicket)

  return (
    <BranchPicker
      serviceType={serviceType}
      onBack={() => navigate(-1)}
      onBooked={(ticket) => { setMyTicket(ticket); navigate('/ticket') }}
    />
  )
}
