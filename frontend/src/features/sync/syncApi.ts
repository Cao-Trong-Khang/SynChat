import { httpClient } from '@/shared/api/httpClient'

import type { ConversationSyncResponse, SyncEventsResponse } from './syncTypes'

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

  syncEvents(afterSeq: number, limit: number) {
    const params = new URLSearchParams({
      afterSeq: String(afterSeq),
      limit: String(limit),
    })

    return httpClient.get<SyncEventsResponse>(
      `/sync/events?${params.toString()}`,
    )
  },
}
