import type { LocalUser } from '@/db/models'

export type ConversationSyncItem = {
  conversationId: string
  lastMessageAt: null | string
  lastMessageSenderId: null | string
  lastMessageSeq: null | number
  lastMessageText: null | string
  peer: LocalUser
  peerDeliveredSeq: number
  peerReadSeq: number
  unreadCount: number
}

export type ConversationSyncResponse = {
  hasMore: boolean
  items: ConversationSyncItem[]
  nextCursor: null | string
  serverEventSeq: null | number
}

export type MessageCreatedEventPayload = {
  conversation: ConversationSyncItem
  message: {
    body: string
    clientMessageId: null | string
    createdAtServer: string
    senderId: string
    seq: number
  }
}

export type MessageCreatedEventEnvelope = {
  eventSeq: number
  eventType: 'message.created'
  payload: MessageCreatedEventPayload
  type: 'event'
}

export type MessageDeliveredEventPayload = {
  conversationId: string
  deliveredSeq: number
  userId: string
}

export type MessageDeliveredEventEnvelope = {
  eventSeq: number
  eventType: 'message.delivered'
  payload: MessageDeliveredEventPayload
  type: 'event'
}

export type MessageReadEventPayload = {
  conversationId: string
  readSeq: number
  userId: string
}

export type MessageReadEventEnvelope = {
  eventSeq: number
  eventType: 'message.read'
  payload: MessageReadEventPayload
  type: 'event'
}

export type PersistedEventEnvelope = {
  eventSeq: number
  eventType: string
  payload: unknown
  type: 'event'
}

export type SyncEventsResponse = {
  events: PersistedEventEnvelope[]
  hasMore: boolean
  nextAfterSeq: number
}
