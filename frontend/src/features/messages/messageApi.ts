import { httpClient } from '@/shared/api/httpClient'

export type ServerMessage = {
  body: string
  clientMessageId: null | string
  createdAtServer: string
  senderId: string
  seq: number
}

export type MessagePageResponse = {
  conversationId: string
  hasMoreBefore: boolean
  messages: ServerMessage[]
  newestSeq: null | number
  oldestSeq: null | number
}

export const messageApi = {
  loadLatestMessages(conversationId: string, limit = 20) {
    const params = new URLSearchParams({ limit: String(limit) })

    return httpClient.get<MessagePageResponse>(
      `/conversations/${conversationId}/messages?${params.toString()}`,
    )
  },
}
