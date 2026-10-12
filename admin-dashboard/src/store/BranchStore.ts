import { create } from 'zustand'
import type { Branch } from '../types'
import { branchService } from '../api/Api'

interface BranchState {
  branches: Branch[]
  selectedBranchId: number | null
  loading: boolean
  loadBranches: (preferredBranchId?: number | null) => Promise<void>
  selectBranch: (id: number) => void
}

export const useBranchStore = create<BranchState>((set, get) => ({
  branches: [],
  selectedBranchId: null,
  loading: false,

  loadBranches: async (preferredBranchId) => {
    if (get().branches.length > 0) return
    set({ loading: true })
    try {
      const res = await branchService.getAll()
      const branches = res.data.data
      const preferred = preferredBranchId != null
        ? branches.find((b) => b.id === preferredBranchId)
        : undefined
      set({
        branches,
        selectedBranchId: preferred?.id ?? branches[0]?.id ?? null,
      })
    } finally {
      set({ loading: false })
    }
  },

  selectBranch: (id) => set({ selectedBranchId: id }),
}))
