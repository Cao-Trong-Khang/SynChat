import type { LocalConversation, LocalUser } from '@/db/models'

export type ConversationListEntry = LocalConversation

export type ConversationPeer = LocalUser | undefined

export type DirectConversationMetadata = {
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

export type DirectConversationResponse = {
  conversation: DirectConversationMetadata | null
}
