import { env } from '@/config/env'
import { authService } from '@/features/auth/authService'
import { useAuthStore } from '@/features/auth/authStore'
import { getOrCreateDeviceId } from '@/features/device/deviceId'
import { outboxProcessor } from '@/features/outbox/outboxProcessor'
import { eventSync } from '@/features/sync/eventSync'

import { eventDispatcher } from './eventDispatcher'
import { reconnectPolicy } from './reconnectPolicy'
import { useWebSocketStore } from './websocketStore'
import { WebSocketClient } from './websocketClient'
import type { ServerEnvelope } from './websocketTypes'

const client = new WebSocketClient()
let activeConnectionKey: null | string = null
let reconnectTimerId: number | null = null
let intentionalClose = false
let hasConnectedAtLeastOnce = false

function connectionUrl(accessToken: string, deviceId: string) {
  const accessTokenParam = encodeURIComponent(accessToken)
  const deviceIdParam = encodeURIComponent(deviceId)

  return `${env.wsUrl}?accessToken=${accessTokenParam}&deviceId=${deviceIdParam}`
}

function setDisconnected(lastError?: string) {
  const store = useWebSocketStore.getState()
  store.setConnected(false)
  store.setConnecting(false)

  if (lastError) {
    store.setLastError(lastError)
  }
}

function clearReconnectTimer() {
  if (reconnectTimerId !== null) {
    window.clearTimeout(reconnectTimerId)
    reconnectTimerId = null
  }
}

function scheduleReconnect() {
  const authState = useAuthStore.getState()

  if (
    !authState.isAuthenticated ||
    intentionalClose ||
    reconnectTimerId ||
    useWebSocketStore.getState().shuttingDownRealtime
  ) {
    return
  }

  const delay = reconnectPolicy.nextDelay()
  const store = useWebSocketStore.getState()
  store.setReconnecting(true)
  store.setReconnectAttempt(reconnectPolicy.currentAttempt())

  reconnectTimerId = window.setTimeout(() => {
    reconnectTimerId = null
    void reconnect()
  }, delay)
}

async function reconnect() {
  const authState = useAuthStore.getState()

  if (
    !authState.isAuthenticated ||
    intentionalClose ||
    useWebSocketStore.getState().shuttingDownRealtime
  ) {
    return
  }

  try {
    const accessToken = await authService.ensureFreshAccessToken()
    websocketManager.connect(accessToken, getOrCreateDeviceId())
  } catch (error) {
    useWebSocketStore
      .getState()
      .setLastError(error instanceof Error ? error.message : 'Reconnect failed.')
  }
}

export const websocketManager = {
  close() {
    intentionalClose = true
    activeConnectionKey = null
    clearReconnectTimer()
    client.close()
    reconnectPolicy.reset()
    setDisconnected()
    const store = useWebSocketStore.getState()
    store.setReconnecting(false)
    store.setReconnectAttempt(0)
  },

  connect(accessToken: string, deviceId: string) {
    const nextConnectionKey = `${deviceId}:${accessToken}`
    const store = useWebSocketStore.getState()
    const authState = useAuthStore.getState()

    if (!authState.isAuthenticated || store.shuttingDownRealtime) {
      return
    }

    if (
      activeConnectionKey === nextConnectionKey &&
      (store.connected || store.connecting)
    ) {
      return
    }

    intentionalClose = false
    clearReconnectTimer()
    activeConnectionKey = nextConnectionKey
    store.setConnecting(true)
    store.setConnected(false)
    store.setLastError(null)

    client.connect(connectionUrl(accessToken, deviceId), {
      onClose: () => {
        setDisconnected()
        if (!intentionalClose) {
          scheduleReconnect()
        }
      },
      onError: () => {
        setDisconnected('WebSocket connection error.')
        if (!intentionalClose) {
          scheduleReconnect()
        }
      },
      onMessage: (event) => {
        try {
          eventDispatcher.dispatch(JSON.parse(event.data) as ServerEnvelope)
        } catch (error) {
          console.error('Failed to parse SynChat WebSocket message.', error)
        }
      },
      onOpen: () => {
        const currentStore = useWebSocketStore.getState()

        if (
          currentStore.shuttingDownRealtime ||
          !useAuthStore.getState().isAuthenticated
        ) {
          client.close()
          return
        }

        const wasReconnect = hasConnectedAtLeastOnce || currentStore.reconnecting
        hasConnectedAtLeastOnce = true
        reconnectPolicy.reset()
        currentStore.setConnecting(false)
        currentStore.setConnected(true)
        currentStore.setLastError(null)
        currentStore.setReconnectAttempt(0)
        currentStore.setReconnecting(false)

        void outboxProcessor.processPending().catch((error: unknown) => {
          console.error('Failed to process pending outbox commands.', error)
        })

        if (wasReconnect) {
          void eventSync.syncEventsFromLastHandledSeq().catch((error: unknown) => {
            console.error('Failed to sync events after reconnect.', error)
          })
        }
      },
    })
  },

  isConnected() {
    return client.isConnected()
  },

  sendJson(object: unknown) {
    client.sendJson(object)
  },
}
