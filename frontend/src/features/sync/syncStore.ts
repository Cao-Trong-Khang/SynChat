import { create } from 'zustand'

type SyncState = {
  needsFullResync: boolean
  setNeedsFullResync: (needsFullResync: boolean) => void
}

export const useSyncStore = create<SyncState>((set) => ({
  needsFullResync: false,
  setNeedsFullResync: (needsFullResync) => set({ needsFullResync }),
}))
