import { ApiError } from '@/shared/api/apiError'
import { httpClient } from '@/shared/api/httpClient'

import type { DirectConversationResponse } from './conversationTypes'

export const conversationApi = {
  async getDirectConversation(peerUserId: string) {
    const params = new URLSearchParams({ peerUserId })

    try {
      const response = await httpClient.get<DirectConversationResponse>(
        `/conversations/direct?${params.toString()}`,
      )

      return response.conversation
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        return null
      }

      throw error
    }
  },
}
