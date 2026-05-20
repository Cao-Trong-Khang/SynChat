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
