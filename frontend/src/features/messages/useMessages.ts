import { observeByConversationLocalId } from '@/db/repositories/messagesRepo'

export function useMessages(conversationLocalId: string | undefined) {
  return observeByConversationLocalId(conversationLocalId ?? '')
}
