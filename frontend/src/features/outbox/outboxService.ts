import type { LocalOutboxCommand } from '@/db/models'
import {
  add,
  getByClientMessageId,
  incrementRetry,
  listPending,
  markDoneByClientMessageId,
  markFailed,
  markProcessing,
  resetToPending,
} from '@/db/repositories/outboxRepo'

import type { OutboxCommandPayloadByType, OutboxCommandType } from './outboxTypes'

export async function saveOutboxCommand<TCommandType extends OutboxCommandType>(
  commandType: TCommandType,
  payload: OutboxCommandPayloadByType[TCommandType],
  createdAt: string,
) {
  const command: LocalOutboxCommand = {
    commandType,
    createdAt,
    id: crypto.randomUUID(),
    payload,
    retryCount: 0,
    status: 'pending',
  }

  await add(command)

  return command
}

export function markOutboxCommandProcessing(id: string) {
  return markProcessing(id)
}

export function markMessageSendOutboxDone(clientMessageId: string) {
  return markDoneByClientMessageId(clientMessageId)
}

export function findMessageSendOutboxCommand(clientMessageId: string) {
  return getByClientMessageId(clientMessageId)
}

export function resetOutboxCommandToPending(id: string) {
  return resetToPending(id)
}

export function listPendingOutboxCommands() {
  return listPending()
}

export function incrementOutboxCommandRetry(id: string) {
  return incrementRetry(id)
}

export function markOutboxCommandFailed(id: string) {
  return markFailed(id)
}
