import { httpClient } from '@/shared/api/httpClient'

import type { ConversationSyncResponse } from './syncTypes'

export const syncApi = {
  syncConversations(limit: number, cursor?: string) {
    const params = new URLSearchParams({ limit: String(limit) })

    if (cursor) {
      params.set('cursor', cursor)
    }

    return httpClient.get<ConversationSyncResponse>(
      `/sync/conversations?${params.toString()}`,
    )
  },
}
