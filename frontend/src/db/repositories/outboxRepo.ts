import { db } from '@/db/db'
import type { LocalOutboxCommand } from '@/db/models'

export async function add(command: LocalOutboxCommand) {
  await db.outboxCommands.add(command)
}

export async function markDone(id: string) {
  await db.outboxCommands.update(id, { status: 'done' })
}

export async function markProcessing(id: string) {
  await db.outboxCommands.update(id, { status: 'processing' })
}

export async function markDoneByClientMessageId(clientMessageId: string) {
  const matchingCommands = await db.outboxCommands
    .where('commandType')
    .equals('messages.send')
    .filter((command) => {
      const payload = command.payload as { clientMessageId?: string }
      return payload.clientMessageId === clientMessageId
    })
    .toArray()

  await Promise.all(
    matchingCommands.map((command) =>
      db.outboxCommands.update(command.id, { status: 'done' }),
    ),
  )
}

export async function markFailed(id: string) {
  const command = await db.outboxCommands.get(id)

  await db.outboxCommands.update(id, {
    retryCount: (command?.retryCount ?? 0) + 1,
    status: 'failed',
  })
}

export function listPending() {
  return db.outboxCommands
    .where('status')
    .equals('pending')
    .sortBy('createdAt')
}
