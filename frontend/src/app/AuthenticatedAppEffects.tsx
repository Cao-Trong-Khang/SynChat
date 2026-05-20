import { useEffect } from 'react'

import { useAuthStore } from '@/features/auth/authStore'
import { getStoredTokens } from '@/features/auth/tokenStorage'
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

    const { accessToken } = getStoredTokens()

    if (!accessToken) {
      return
    }

    connectedUserIds.add(currentUser.id)
    websocketManager.connect(accessToken, getOrCreateDeviceId())

    return () => {
      connectedUserIds.delete(currentUser.id)
      websocketManager.close()
    }
  }, [currentUser, isAuthenticated])

  return null
}
