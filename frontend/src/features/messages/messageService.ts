import type { LocalConversation, LocalMessage } from '@/db/models'
import { ensure as ensureMessageSyncState } from '@/db/repositories/messageSyncStateRepo'
import {
  addMessage,
  getByLocalId as getMessageByLocalId,
  markFailedByLocalId,
  markSendingByLocalId,
} from '@/db/repositories/messagesRepo'
import {
  getByLocalId,
  updateConversationPreview,
  upsertConversation,
} from '@/db/repositories/conversationsRepo'
import { useAuthStore } from '@/features/auth/authStore'
import { createDraftDirectConversation } from '@/features/conversations/conversationService'
import {
  findMessageSendOutboxCommand,
  markOutboxCommandProcessing,
  resetOutboxCommandToPending,
  saveOutboxCommand,
} from '@/features/outbox/outboxService'
import { sendCommand } from '@/features/realtime/commandSender'
import type { SelectedChat } from '@/features/shell/shellStore'
import { useShellStore } from '@/features/shell/shellStore'

import type { SendMessageCommandPayload } from './messageTypes'

function commandPayloadForConversation(
  conversation: LocalConversation,
  body: string,
  clientMessageId: string,
): SendMessageCommandPayload {
  if (conversation.serverId) {
    return {
      body,
      clientMessageId,
      conversationId: conversation.serverId,
    }
  }

  return {
    body,
    clientMessageId,
    recipientId: conversation.peerUserId,
  }
}

async function getMessageSendOutboxCommand(
  payload: SendMessageCommandPayload,
  createdAt: string,
) {
  const existingCommand = await findMessageSendOutboxCommand(
    payload.clientMessageId,
  )

  if (existingCommand) {
    await resetOutboxCommandToPending(existingCommand.id)
    return existingCommand
  }

  return saveOutboxCommand('messages.send', payload, createdAt)
}

export const messageService = {
  async sendMessage(selectedChat: SelectedChat, body: string) {
    const trimmedBody = body.trim()

    if (!trimmedBody || selectedChat.kind === 'none') {
      return false
    }

    const currentUser = useAuthStore.getState().currentUser

    if (!currentUser) {
      throw new Error('Cannot send a message without an authenticated user.')
    }

    const clientMessageId = crypto.randomUUID()
    const localMessageId = crypto.randomUUID()
    const now = new Date().toISOString()
    let conversationLocalId: string
    let payload: SendMessageCommandPayload

    if (selectedChat.kind === 'draft') {
      conversationLocalId = `direct:${selectedChat.peerUserId}`
      const conversation = createDraftDirectConversation(
        selectedChat.peerUserId,
        currentUser.id,
        trimmedBody,
        now,
      )

      await upsertConversation(conversation)
      await ensureMessageSyncState(conversationLocalId)
      useShellStore.getState().selectConversation(conversationLocalId)

      payload = {
        body: trimmedBody,
        clientMessageId,
        recipientId: selectedChat.peerUserId,
      }
    } else {
      conversationLocalId = selectedChat.conversationLocalId
      const conversation = await getByLocalId(conversationLocalId)

      if (!conversation) {
        throw new Error('Cannot send a message without a local conversation.')
      }

      await updateConversationPreview(conversationLocalId, {
        lastMessageAt: now,
        lastMessageSenderId: currentUser.id,
        lastMessageText: trimmedBody,
      })

      payload = commandPayloadForConversation(
        conversation,
        trimmedBody,
        clientMessageId,
      )
    }

    const message: LocalMessage = {
      body: trimmedBody,
      clientMessageId,
      conversationLocalId,
      createdAt: now,
      localId: localMessageId,
      localStatus: 'sending',
      senderId: currentUser.id,
      seq: null,
    }

    await addMessage(message)

    const outboxCommand = await saveOutboxCommand(
      'messages.send',
      payload,
      now,
    )

    try {
      sendCommand('messages.send', payload, outboxCommand.id)
      await markOutboxCommandProcessing(outboxCommand.id)
    } catch {
      // Pending outbox commands are retried by the reconnect processor.
    }

    return true
  },

  async retryMessage(localMessageId: string) {
    const message = await getMessageByLocalId(localMessageId)

    if (!message?.clientMessageId) {
      throw new Error('Cannot retry a message without a clientMessageId.')
    }

    const conversation = await getByLocalId(message.conversationLocalId)

    if (!conversation) {
      throw new Error('Cannot retry a message without a local conversation.')
    }

    const payload = commandPayloadForConversation(
      conversation,
      message.body,
      message.clientMessageId,
    )
    const outboxCommand = await getMessageSendOutboxCommand(
      payload,
      message.createdAt,
    )

    await markSendingByLocalId(localMessageId)

    try {
      sendCommand('messages.send', payload, outboxCommand.id)
      await markOutboxCommandProcessing(outboxCommand.id)
    } catch {
      await markFailedByLocalId(localMessageId)
    }
  },
}
