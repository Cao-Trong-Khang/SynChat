import type { LocalConversation, LocalUser } from '@/db/models'
import type { ConversationSyncItem } from '@/features/sync/syncTypes'

type MappedConversation = {
  conversation: LocalConversation
  peer: LocalUser
}

export function mapConversationSyncItem(
  item: ConversationSyncItem,
): MappedConversation {
  return {
    conversation: {
      lastMessageAt: item.lastMessageAt,
      lastMessageSenderId: item.lastMessageSenderId,
      lastMessageSeq: item.lastMessageSeq,
      lastMessageText: item.lastMessageText,
      localId: `direct:${item.peer.id}`,
      peerDeliveredSeq: item.peerDeliveredSeq,
      peerReadSeq: item.peerReadSeq,
      peerUserId: item.peer.id,
      serverId: item.conversationId,
      unreadCount: item.unreadCount,
    },
    peer: item.peer,
  }
}
