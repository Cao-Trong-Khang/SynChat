import { create } from 'zustand'

type WebSocketState = {
  connected: boolean
  connecting: boolean
  lastError: null | string
  reconnectAttempt: number
  reconnecting: boolean
  shuttingDownRealtime: boolean
  reset: () => void
  setConnected: (connected: boolean) => void
  setConnecting: (connecting: boolean) => void
  setLastError: (lastError: null | string) => void
  setReconnectAttempt: (reconnectAttempt: number) => void
  setReconnecting: (reconnecting: boolean) => void
  setShuttingDownRealtime: (shuttingDownRealtime: boolean) => void
}

export const useWebSocketStore = create<WebSocketState>((set) => ({
  connected: false,
  connecting: false,
  lastError: null,
  reconnectAttempt: 0,
  reconnecting: false,
  shuttingDownRealtime: false,
  reset: () =>
    set({
      connected: false,
      connecting: false,
      lastError: null,
      reconnectAttempt: 0,
      reconnecting: false,
      shuttingDownRealtime: false,
    }),
  setConnected: (connected) => set({ connected }),
  setConnecting: (connecting) => set({ connecting }),
  setLastError: (lastError) => set({ lastError }),
  setReconnectAttempt: (reconnectAttempt) => set({ reconnectAttempt }),
  setReconnecting: (reconnecting) => set({ reconnecting }),
  setShuttingDownRealtime: (shuttingDownRealtime) =>
    set({ shuttingDownRealtime }),
}))
