import { env } from '@/config/env'

import { eventDispatcher } from './eventDispatcher'
import { useWebSocketStore } from './websocketStore'
import { WebSocketClient } from './websocketClient'
import type { ServerEnvelope } from './websocketTypes'

const client = new WebSocketClient()
let activeConnectionKey: null | string = null

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

export const websocketManager = {
  close() {
    activeConnectionKey = null
    client.close()
    setDisconnected()
  },

  connect(accessToken: string, deviceId: string) {
    const nextConnectionKey = `${deviceId}:${accessToken}`
    const store = useWebSocketStore.getState()

    if (
      activeConnectionKey === nextConnectionKey &&
      (store.connected || store.connecting)
    ) {
      return
    }

    activeConnectionKey = nextConnectionKey
    store.setConnecting(true)
    store.setConnected(false)
    store.setLastError(null)

    client.connect(connectionUrl(accessToken, deviceId), {
      onClose: () => {
        setDisconnected()
      },
      onError: () => {
        setDisconnected('WebSocket connection error.')
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
        currentStore.setConnecting(false)
        currentStore.setConnected(true)
        currentStore.setLastError(null)
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
