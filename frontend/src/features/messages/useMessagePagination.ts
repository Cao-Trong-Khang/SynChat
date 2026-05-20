import { useCallback, useEffect, useRef, useState } from 'react'

import { observe as observeMessageSyncState } from '@/db/repositories/messageSyncStateRepo'
import { messageSync } from '@/features/sync/messageSync'

export function useMessagePagination(
  conversationLocalId: string,
  serverId: null | string | undefined,
) {
  const [isLoadingLatest, setIsLoadingLatest] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const messageSyncState = observeMessageSyncState(conversationLocalId)
  const requestedConversationIdsRef = useRef(new Set<string>())

  useEffect(() => {
    if (!serverId || requestedConversationIdsRef.current.has(conversationLocalId)) {
      return
    }

    requestedConversationIdsRef.current.add(conversationLocalId)

    let isMounted = true

    async function ensureLatestMessagesLoaded() {
      const shouldLoadLatest =
        await messageSync.shouldLoadLatestMessages(conversationLocalId)

      if (!shouldLoadLatest) {
        return
      }

      if (isMounted) {
        setIsLoadingLatest(true)
      }

      try {
        await messageSync.ensureLatestMessagesLoaded(conversationLocalId)
      } finally {
        if (isMounted) {
          setIsLoadingLatest(false)
        }
      }
    }

    void ensureLatestMessagesLoaded().catch((error: unknown) => {
      console.error('Failed to load latest messages.', error)

      if (isMounted) {
        setIsLoadingLatest(false)
      }
    })

    return () => {
      isMounted = false
    }
  }, [conversationLocalId, serverId])

  const loadOlder = useCallback(async () => {
    if (loadingOlder || !messageSyncState?.hasMoreBefore) {
      return false
    }

    setLoadingOlder(true)

    try {
      return await messageSync.loadOlderMessages(conversationLocalId)
    } catch (error) {
      console.error('Failed to load older messages.', error)
      return false
    } finally {
      setLoadingOlder(false)
    }
  }, [conversationLocalId, loadingOlder, messageSyncState?.hasMoreBefore])

  return {
    hasMoreBefore: messageSyncState?.hasMoreBefore ?? false,
    isLoadingLatest,
    loadOlder,
    loadingOlder,
  }
}
