import { create } from 'zustand'

type SyncState = {
  needsFullResync: boolean
  reset: () => void
  setNeedsFullResync: (needsFullResync: boolean) => void
}

export const useSyncStore = create<SyncState>((set) => ({
  needsFullResync: false,
  reset: () => set({ needsFullResync: false }),
  setNeedsFullResync: (needsFullResync) => set({ needsFullResync }),
}))
