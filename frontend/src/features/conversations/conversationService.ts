import type { LocalConversation, LocalUser } from '@/db/models'
import { upsertConversation } from '@/db/repositories/conversationsRepo'
import { getByPeerUserId } from '@/db/repositories/conversationsRepo'
import { upsertUser } from '@/db/repositories/usersRepo'
import { useShellStore } from '@/features/shell/shellStore'

import { conversationApi } from './conversationApi'
import type { DirectConversationMetadata } from './conversationTypes'

export function getConversationPreview(
  conversation: LocalConversation,
  currentUserId: null | string | undefined,
) {
  if (!conversation.lastMessageText) {
    return 'No messages yet'
  }

  if (conversation.lastMessageSenderId === currentUserId) {
    return `You: ${conversation.lastMessageText}`
  }

  return conversation.lastMessageText
}

export function getConversationTitle(peerUser: LocalUser | undefined) {
  return peerUser?.displayName ?? 'Unknown user'
}

export function formatConversationTime(lastMessageAt: null | string) {
  if (!lastMessageAt) {
    return ''
  }

  const date = new Date(lastMessageAt)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return new Intl.DateTimeFormat(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

function mapDirectConversation(
  peerUserId: string,
  metadata: DirectConversationMetadata,
): LocalConversation {
  return {
    lastMessageAt: metadata.lastMessageAt,
    lastMessageSenderId: metadata.lastMessageSenderId,
    lastMessageSeq: metadata.lastMessageSeq,
    lastMessageText: metadata.lastMessageText,
    localId: `direct:${peerUserId}`,
    peerDeliveredSeq: metadata.peerDeliveredSeq,
    peerReadSeq: metadata.peerReadSeq,
    peerUserId,
    serverId: metadata.conversationId,
    unreadCount: metadata.unreadCount,
  }
}

export async function openDirectConversation(peerUser: LocalUser) {
  await upsertUser(peerUser)

  const localConversation = await getByPeerUserId(peerUser.id)

  if (localConversation) {
    useShellStore.getState().selectConversation(localConversation.localId)
    return
  }

  const serverConversation = await conversationApi.getDirectConversation(
    peerUser.id,
  )

  if (serverConversation) {
    await upsertUser(serverConversation.peer)

    const mappedConversation = mapDirectConversation(
      peerUser.id,
      serverConversation,
    )

    await upsertConversation(mappedConversation)
    useShellStore.getState().selectConversation(mappedConversation.localId)
    return
  }

  useShellStore.getState().selectDraft(peerUser.id)
}
