import { getByLocalId } from '@/db/repositories/conversationsRepo'
import {
  get as getMessageSyncState,
  updateRange,
} from '@/db/repositories/messageSyncStateRepo'
import {
  countByConversationLocalId,
  upsertServerMessage,
} from '@/db/repositories/messagesRepo'
import { messageApi } from '@/features/messages/messageApi'
import { mapServerMessageToUpsertInput } from '@/features/messages/messageMapper'

const LATEST_MESSAGES_LIMIT = 20
const OLDER_MESSAGES_LIMIT = 50

async function upsertServerMessages(
  conversationLocalId: string,
  messages: Awaited<
    ReturnType<typeof messageApi.loadLatestMessages>
  >['messages'],
) {
  await Promise.all(
    messages.map((message) =>
      upsertServerMessage(
        mapServerMessageToUpsertInput(conversationLocalId, message),
      ),
    ),
  )
}

export const messageSync = {
  async shouldLoadLatestMessages(conversationLocalId: string) {
    const conversation = await getByLocalId(conversationLocalId)

    if (!conversation?.serverId) {
      return false
    }

    const [localMessageCount, syncState] = await Promise.all([
      countByConversationLocalId(conversationLocalId),
      getMessageSyncState(conversationLocalId),
    ])

    return (
      localMessageCount === 0 ||
      !syncState ||
      (localMessageCount < LATEST_MESSAGES_LIMIT && syncState.hasMoreBefore)
    )
  },

  async ensureLatestMessagesLoaded(conversationLocalId: string) {
    if (!(await this.shouldLoadLatestMessages(conversationLocalId))) {
      return false
    }

    await this.loadLatestMessages(conversationLocalId)

    return true
  },

  async loadLatestMessages(conversationLocalId: string) {
    const conversation = await getByLocalId(conversationLocalId)

    if (!conversation?.serverId) {
      return
    }

    const response = await messageApi.loadLatestMessages(
      conversation.serverId,
      LATEST_MESSAGES_LIMIT,
    )

    await upsertServerMessages(conversationLocalId, response.messages)

    const existingState = await getMessageSyncState(conversationLocalId)
    const newestLoadedSeq =
      response.newestSeq === null
        ? existingState?.newestLoadedSeq ?? null
        : Math.max(
            existingState?.newestLoadedSeq ?? response.newestSeq,
            response.newestSeq,
          )

    await updateRange(conversationLocalId, {
      hasMoreBefore: response.hasMoreBefore,
      newestLoadedSeq,
      oldestLoadedSeq: response.oldestSeq,
    })
  },

  async loadOlderMessages(conversationLocalId: string) {
    const conversation = await getByLocalId(conversationLocalId)

    if (!conversation?.serverId) {
      return false
    }

    const syncState = await getMessageSyncState(conversationLocalId)

    if (!syncState?.hasMoreBefore || syncState.oldestLoadedSeq === null) {
      return false
    }

    const response = await messageApi.loadOlderMessages(
      conversation.serverId,
      syncState.oldestLoadedSeq,
      OLDER_MESSAGES_LIMIT,
    )

    await upsertServerMessages(conversationLocalId, response.messages)

    await updateRange(conversationLocalId, {
      hasMoreBefore: response.hasMoreBefore,
      newestLoadedSeq: syncState.newestLoadedSeq ?? response.newestSeq,
      oldestLoadedSeq: response.oldestSeq,
    })

    return response.messages.length > 0
  },
}
