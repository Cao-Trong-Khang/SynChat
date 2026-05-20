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

  useEffect(() => {
    if (!isAuthenticated || !currentUser) {
      websocketManager.close()
      return
    }

    if (syncedUserIds.has(currentUser.id)) {
      return
    }

    syncedUserIds.add(currentUser.id)

    void firstOpenSync().catch((error: unknown) => {
      console.error('Failed to run first-open sync.', error)
      syncedUserIds.delete(currentUser.id)
    })
  }, [currentUser, isAuthenticated])

  useEffect(() => {
    if (!isAuthenticated || !currentUser) {
      return
    }

    if (connectedUserIds.has(currentUser.id)) {
      return
    }

    connectedUserIds.add(currentUser.id)

    void authService
      .ensureFreshAccessToken()
      .then((accessToken) => {
        websocketManager.connect(accessToken, getOrCreateDeviceId())
      })
      .catch((error: unknown) => {
        console.error('Failed to connect SynChat WebSocket.', error)
        connectedUserIds.delete(currentUser.id)
      })

    return () => {
      connectedUserIds.delete(currentUser.id)
      websocketManager.close()
    }
  }, [currentUser, isAuthenticated])

  return null
}
