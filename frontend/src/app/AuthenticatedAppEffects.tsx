import { useEffect } from 'react'

import { useAuthStore } from '@/features/auth/authStore'
import { firstOpenSync } from '@/features/sync/firstOpenSync'

const syncedUserIds = new Set<string>()

export function AuthenticatedAppEffects() {
  const currentUser = useAuthStore((state) => state.currentUser)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  useEffect(() => {
    if (!isAuthenticated || !currentUser) {
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

  return null
}
