import { useCallback, useState } from 'react'

import { observeConversationList } from '@/db/repositories/conversationsRepo'
import { conversationSync } from '@/features/sync/conversationSync'

export function useConversationList() {
  const conversations = observeConversationList()
  const [loadingMore, setLoadingMore] = useState(false)

  const loadMoreConversations = useCallback(async () => {
    if (loadingMore) {
      return false
    }

    setLoadingMore(true)

    try {
      return await conversationSync.loadMoreConversations()
    } catch (error) {
      console.error('Failed to load more conversations.', error)
      return false
    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore])

  return {
    conversations,
    loadingMore,
    loadMoreConversations,
  }
}
