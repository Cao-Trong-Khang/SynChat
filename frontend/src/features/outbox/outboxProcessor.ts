import type { LocalOutboxCommand } from '@/db/models'
import { markFailedByClientMessageId } from '@/db/repositories/messagesRepo'
import { sendCommand } from '@/features/realtime/commandSender'
import { websocketManager } from '@/features/realtime/websocketManager'
import { useWebSocketStore } from '@/features/realtime/websocketStore'

import {
  incrementOutboxCommandRetry,
  listPendingOutboxCommands,
  markOutboxCommandFailed,
  markOutboxCommandProcessing,
  resetOutboxCommandToPending,
} from './outboxService'
import { MAX_OUTBOX_RETRY_COUNT } from './outboxTypes'

type MessageSendPayload = {
  body: string
  clientMessageId: string
  conversationId?: string
  recipientId?: string
}

export function sendOutboxCommand(command: LocalOutboxCommand) {
  sendCommand(command.commandType, command.payload, command.id)
}

function isMessageSendPayload(payload: unknown): payload is MessageSendPayload {
  if (!payload || typeof payload !== 'object') {
    return false
  }

  const candidate = payload as Partial<MessageSendPayload>

  return (
    typeof candidate.clientMessageId === 'string' &&
    typeof candidate.body === 'string' &&
    (typeof candidate.conversationId === 'string' ||
      typeof candidate.recipientId === 'string')
  )
}

async function handleSendFailure(command: LocalOutboxCommand) {
  const retryCount = await incrementOutboxCommandRetry(command.id)

  if (retryCount >= MAX_OUTBOX_RETRY_COUNT) {
    await markOutboxCommandFailed(command.id)

    if (isMessageSendPayload(command.payload)) {
      await markFailedByClientMessageId(command.payload.clientMessageId)
    }

    return
  }

  await resetOutboxCommandToPending(command.id)
}

export const outboxProcessor = {
  async processPending() {
    if (
      !websocketManager.isConnected() ||
      useWebSocketStore.getState().shuttingDownRealtime
    ) {
      return
    }

    const pendingCommands = await listPendingOutboxCommands()

    for (const command of pendingCommands) {
      if (useWebSocketStore.getState().shuttingDownRealtime) {
        return
      }

      if (command.commandType !== 'messages.send') {
        continue
      }

      if (!isMessageSendPayload(command.payload)) {
        await markOutboxCommandFailed(command.id)
        continue
      }

      try {
        await markOutboxCommandProcessing(command.id)
        sendOutboxCommand(command)
      } catch {
        await handleSendFailure(command)
      }
    }
  },
}
