import { useEffect } from 'react'
import StatsRow from '../components/StatsRow'
import NowServing from '../components/NowServing'
import QueueTable from '../components/QueueTable'
import { useBranchStore } from '../store/BranchStore'
import { useQueueStore } from '../store/QueueStore'
import { disconnectBranchQueue } from '../api/WebSocket'

export default function DashboardPage() {
  const selectedBranchId = useBranchStore((s) => s.selectedBranchId)
  const connect = useQueueStore((s) => s.connect)

  useEffect(() => {
    return () => disconnectBranchQueue()
  }, [])

  useEffect(() => {
    if (selectedBranchId != null) connect(selectedBranchId)
  }, [selectedBranchId, connect])

  return (
    <main className="flex-1 p-6 flex flex-col gap-5">
      <StatsRow />
      <div className="flex gap-5 flex-1">
        <NowServing />
        <QueueTable />
      </div>
    </main>
  )
}
