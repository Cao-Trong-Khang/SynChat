import { create } from 'zustand'

import type { AuthUser } from './authTypes'

type AuthState = {
  clearAuth: () => void
  currentUser: AuthUser | null
  isAuthenticated: boolean
  restoreAuth: (currentUser: AuthUser) => void
  sessionId: number
  setAuth: (currentUser: AuthUser) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  clearAuth: () => set({ currentUser: null, isAuthenticated: false }),
  currentUser: null,
  isAuthenticated: false,
  restoreAuth: (currentUser) =>
    set((state) => ({
      currentUser,
      isAuthenticated: true,
      sessionId: state.sessionId + 1,
    })),
  sessionId: 0,
  setAuth: (currentUser) =>
    set((state) => ({
      currentUser,
      isAuthenticated: true,
      sessionId: state.sessionId + 1,
    })),
}))
