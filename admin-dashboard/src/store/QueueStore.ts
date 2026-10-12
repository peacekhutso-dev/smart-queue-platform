import { create } from 'zustand'
import type { BranchStats, QueueBroadcast, QueueEntry, QueueStatus } from '../types'
import { queueService } from '../api/Api'
import { connectBranchQueue, disconnectBranchQueue } from '../api/WebSocket'

interface QueueState {
  branchId: number | null
  entries: QueueEntry[]
  stats: BranchStats | null
  loading: boolean

  connect: (branchId: number) => Promise<void>
  refreshStats: () => Promise<void>
  advance: (entryId: number, status: QueueStatus, counter?: string) => Promise<void>
}

export const useQueueStore = create<QueueState>((set, get) => ({
  branchId: null,
  entries: [],
  stats: null,
  loading: false,

  connect: async (branchId) => {
    if (get().branchId === branchId) return
    disconnectBranchQueue()
    set({ branchId, entries: [], stats: null, loading: true })

    try {
      const [queueRes, statsRes] = await Promise.all([
        queueService.getBranchQueue(branchId),
        queueService.getBranchStats(branchId),
      ])
      set({ entries: queueRes.data.data, stats: statsRes.data.data })
    } finally {
      set({ loading: false })
    }

    connectBranchQueue(branchId, (broadcast: QueueBroadcast) => {
      if (get().branchId !== branchId) return
      set({ entries: broadcast.topEntries })
    })
  },

  refreshStats: async () => {
    const { branchId } = get()
    if (branchId == null) return
    const res = await queueService.getBranchStats(branchId)
    set({ stats: res.data.data })
  },

  advance: async (entryId, status, counter) => {
    await queueService.advance(entryId, status, counter)
    await get().refreshStats()
  },
}))
