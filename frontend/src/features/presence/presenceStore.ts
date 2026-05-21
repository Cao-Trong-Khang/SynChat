import { create } from 'zustand'

export type PresenceState = {
  lastSeenAt: null | string
  online: boolean
}

export type PresenceSnapshotItem = {
  lastSeenAt: null | string
  online: boolean
  userId: string
}

type PresenceStore = {
  byUserId: Record<string, PresenceState>
  clearAll: () => void
  getPresence: (userId: string) => PresenceState | undefined
  removeUsers: (userIds: string[]) => void
  setPresence: (
    userId: string,
    online: boolean,
    lastSeenAt: null | string,
  ) => void
  setSnapshot: (items: PresenceSnapshotItem[]) => void
}

export const usePresenceStore = create<PresenceStore>((set, get) => ({
  byUserId: {},
  clearAll: () => set({ byUserId: {} }),
  getPresence: (userId) => get().byUserId[userId],
  removeUsers: (userIds) =>
    set((state) => {
      const byUserId = { ...state.byUserId }

      for (const userId of userIds) {
        delete byUserId[userId]
      }

      return { byUserId }
    }),
  setPresence: (userId, online, lastSeenAt) =>
    set((state) => ({
      byUserId: {
        ...state.byUserId,
        [userId]: {
          lastSeenAt,
          online,
        },
      },
    })),
  setSnapshot: (items) =>
    set((state) => {
      const byUserId = { ...state.byUserId }

      for (const item of items) {
        byUserId[item.userId] = {
          lastSeenAt: item.lastSeenAt,
          online: item.online,
        }
      }

      return { byUserId }
    }),
}))
