import { getByLocalId } from '@/db/repositories/conversationsRepo'
import {
  get as getMessageSyncState,
  updateRange,
} from '@/db/repositories/messageSyncStateRepo'
import { upsertMessages } from '@/db/repositories/messagesRepo'
import { mapServerMessageToLocalMessage } from '@/features/messages/messageMapper'
import { messageApi } from '@/features/messages/messageApi'

const LATEST_MESSAGES_LIMIT = 20
const OLDER_MESSAGES_LIMIT = 20

export const messageSync = {
  async loadLatestMessages(conversationLocalId: string) {
    const conversation = await getByLocalId(conversationLocalId)

    if (!conversation?.serverId) {
      return
    }

    const response = await messageApi.loadLatestMessages(
      conversation.serverId,
      LATEST_MESSAGES_LIMIT,
    )

    await upsertMessages(
      response.messages.map((message) =>
        mapServerMessageToLocalMessage(conversationLocalId, message),
      ),
    )

    await updateRange(conversationLocalId, {
      hasMoreBefore: response.hasMoreBefore,
      newestLoadedSeq: response.newestSeq,
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

    await upsertMessages(
      response.messages.map((message) =>
        mapServerMessageToLocalMessage(conversationLocalId, message),
      ),
    )

    await updateRange(conversationLocalId, {
      hasMoreBefore: response.hasMoreBefore,
      newestLoadedSeq: syncState.newestLoadedSeq ?? response.newestSeq,
      oldestLoadedSeq: response.oldestSeq,
    })

    return response.messages.length > 0
  },
}
