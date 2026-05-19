import { observeConversationList } from '@/db/repositories/conversationsRepo'

export function useConversationList() {
  return observeConversationList()
}
