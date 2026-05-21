import { useEffect } from 'react'

import { authService } from '@/features/auth/authService'
import { useAuthStore } from '@/features/auth/authStore'
import { getOrCreateDeviceId } from '@/features/device/deviceId'
import { websocketManager } from '@/features/realtime/websocketManager'
import { firstOpenSync } from '@/features/sync/firstOpenSync'

const syncedUserIds = new Set<string>()
const connectedUserIds = new Set<string>()

export function AuthenticatedAppEffects() {
  const currentUser = useAuthStore((state) => state.currentUser)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const sessionId = useAuthStore((state) => state.sessionId)

  useEffect(() => {
    if (!isAuthenticated || !currentUser) {
      websocketManager.close()
      return
    }

    const sessionUserId = `${sessionId}:${currentUser.id}`

    if (syncedUserIds.has(sessionUserId)) {
      return
    }

    syncedUserIds.add(sessionUserId)

    void firstOpenSync().catch((error: unknown) => {
      console.error('Failed to run first-open sync.', error)
      syncedUserIds.delete(sessionUserId)
    })
  }, [currentUser, isAuthenticated, sessionId])

  useEffect(() => {
    if (!isAuthenticated || !currentUser) {
      return
    }

    const sessionUserId = `${sessionId}:${currentUser.id}`

    if (connectedUserIds.has(sessionUserId)) {
      return
    }

    connectedUserIds.add(sessionUserId)

    void authService
      .ensureFreshAccessToken()
      .then((accessToken) => {
        websocketManager.connect(accessToken, getOrCreateDeviceId())
      })
      .catch((error: unknown) => {
        console.error('Failed to connect SynChat WebSocket.', error)
        connectedUserIds.delete(sessionUserId)
      })

    return () => {
      connectedUserIds.delete(sessionUserId)
      websocketManager.close()
    }
  }, [currentUser, isAuthenticated, sessionId])

  return null
}
