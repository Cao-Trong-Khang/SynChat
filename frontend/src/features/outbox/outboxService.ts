import type { LocalOutboxCommand } from '@/db/models'
import { add, markProcessing } from '@/db/repositories/outboxRepo'

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
