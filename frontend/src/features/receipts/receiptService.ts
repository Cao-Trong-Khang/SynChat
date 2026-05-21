import {
  getByLocalId,
  updateConversationUnreadCount,
} from '@/db/repositories/conversationsRepo'
import { sendCommand } from '@/features/realtime/commandSender'

import type { DeliveredUpToPayload, ReadUpToPayload } from './receiptTypes'

export const receiptService = {
  async markDeliveredUpTo(conversationLocalId: string, seq: null | number) {
    if (!seq) {
      return
    }

    const conversation = await getByLocalId(conversationLocalId)

    if (!conversation?.serverId) {
      return
    }

    const payload: DeliveredUpToPayload = {
      conversationId: conversation.serverId,
      deliveredSeq: seq,
    }

    try {
      sendCommand('delivered.up_to', payload)
    } catch (error) {
      console.warn('Failed to mark messages delivered.', error)
    }
  },

  async markReadUpTo(conversationLocalId: string, seq: null | number) {
    if (!seq) {
      return
    }

    const conversation = await getByLocalId(conversationLocalId)

    if (!conversation?.serverId) {
      return
    }

    const payload: ReadUpToPayload = {
      conversationId: conversation.serverId,
      readSeq: seq,
    }

    try {
      sendCommand('read.up_to', payload)
      await updateConversationUnreadCount(conversationLocalId, 0)
    } catch (error) {
      console.warn('Failed to mark messages read.', error)
    }
  },
}
