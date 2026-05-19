import type { LocalConversation, LocalUser } from '@/db/models'

export type ConversationListEntry = LocalConversation

export type ConversationPeer = LocalUser | undefined
