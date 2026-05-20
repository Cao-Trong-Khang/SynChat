import { create } from 'zustand'

type WebSocketState = {
  connected: boolean
  connecting: boolean
  lastError: null | string
  setConnected: (connected: boolean) => void
  setConnecting: (connecting: boolean) => void
  setLastError: (lastError: null | string) => void
}

export const useWebSocketStore = create<WebSocketState>((set) => ({
  connected: false,
  connecting: false,
  lastError: null,
  setConnected: (connected) => set({ connected }),
  setConnecting: (connecting) => set({ connecting }),
  setLastError: (lastError) => set({ lastError }),
}))
