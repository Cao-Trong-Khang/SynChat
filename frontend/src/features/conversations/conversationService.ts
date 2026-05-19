import type { LocalConversation, LocalUser } from '@/db/models'

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
