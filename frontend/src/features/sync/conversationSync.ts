import {
  getByLocalId,
  upsertConversation,
} from '@/db/repositories/conversationsRepo'
import {
  get as getSyncState,
  getBoolean as getSyncStateBoolean,
  set as setSyncState,
} from '@/db/repositories/syncStateRepo'
import { upsertUsers } from '@/db/repositories/usersRepo'
import { mapConversationSyncItem } from '@/features/conversations/conversationMapper'
import type { ConversationSyncResponse } from '@/features/sync/syncTypes'

import { syncApi } from './syncApi'

const CONVERSATION_PAGE_LIMIT = 20

async function applyConversationPage(response: ConversationSyncResponse) {
  const mappedItems = response.items.map((item) => mapConversationSyncItem(item))

  await upsertUsers(mappedItems.map((item) => item.peer))

  await Promise.all(
    mappedItems.map(async (item) => {
      const existingConversation = await getByLocalId(item.conversation.localId)

      await upsertConversation({
        ...item.conversation,
        lastMessageAt:
          item.conversation.lastMessageAt ??
          existingConversation?.lastMessageAt ??
          null,
        lastMessageSenderId:
          item.conversation.lastMessageSenderId ??
          existingConversation?.lastMessageSenderId ??
          null,
        lastMessageText:
          item.conversation.lastMessageText ??
          existingConversation?.lastMessageText ??
          null,
      })
    }),
  )

  await setSyncState('next_conversation_cursor', response.nextCursor)
  await setSyncState('has_more_conversations', String(response.hasMore))
}

export const conversationSync = {
  async bootstrapFirstConversationPage() {
    const response = await syncApi.syncConversations(CONVERSATION_PAGE_LIMIT)

    await applyConversationPage(response)
    await setSyncState('conversations_bootstrapped', 'true')

    if (response.serverEventSeq !== null) {
      await setSyncState(
        'last_handled_event_seq',
        String(response.serverEventSeq),
      )
    }

    return response
  },

  async loadMoreConversations() {
    const [hasMore, cursor] = await Promise.all([
      getSyncStateBoolean('has_more_conversations'),
      getSyncState('next_conversation_cursor'),
    ])

    if (!hasMore || !cursor) {
      return false
    }

    const response = await syncApi.syncConversations(
      CONVERSATION_PAGE_LIMIT,
      cursor,
    )

    await applyConversationPage(response)

    return true
  },
}
