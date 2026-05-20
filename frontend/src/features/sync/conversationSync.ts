import { upsertConversation } from '@/db/repositories/conversationsRepo'
import { set as setSyncState } from '@/db/repositories/syncStateRepo'
import { upsertUsers } from '@/db/repositories/usersRepo'
import { mapConversationSyncItem } from '@/features/conversations/conversationMapper'

import { syncApi } from './syncApi'

const CONVERSATION_BOOTSTRAP_LIMIT = 20

export const conversationSync = {
  async bootstrapFirstConversationPage() {
    const response = await syncApi.syncConversations(
      CONVERSATION_BOOTSTRAP_LIMIT,
    )
    const mappedItems = response.items.map((item) =>
      mapConversationSyncItem(item),
    )

    await upsertUsers(mappedItems.map((item) => item.peer))

    await Promise.all(
      mappedItems.map((item) => upsertConversation(item.conversation)),
    )

    await setSyncState('conversations_bootstrapped', 'true')
    await setSyncState('next_conversation_cursor', response.nextCursor)
    await setSyncState('has_more_conversations', String(response.hasMore))

    if (response.serverEventSeq !== null) {
      await setSyncState(
        'last_handled_event_seq',
        String(response.serverEventSeq),
      )
    }

    return response
  },
}
