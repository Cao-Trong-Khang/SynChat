import { create } from 'zustand'

import type { AuthUser } from './authTypes'

type AuthState = {
  clearAuth: () => void
  currentUser: AuthUser | null
  isAuthenticated: boolean
  restoreAuth: (currentUser: AuthUser) => void
  setAuth: (currentUser: AuthUser) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  clearAuth: () => set({ currentUser: null, isAuthenticated: false }),
  currentUser: null,
  isAuthenticated: false,
  restoreAuth: (currentUser) => set({ currentUser, isAuthenticated: true }),
  setAuth: (currentUser) => set({ currentUser, isAuthenticated: true }),
}))
