import Dexie, { type Table } from 'dexie'

import type {
  LocalConversation,
  LocalOutboxCommand,
  LocalSyncState,
  LocalUser,
} from './models'
import { dbSchema } from './schema'

class SynChatDatabase extends Dexie {
  conversations!: Table<LocalConversation, string>
  outboxCommands!: Table<LocalOutboxCommand, string>
  syncState!: Table<LocalSyncState, string>
  users!: Table<LocalUser, string>

  constructor() {
    super('synchat')
    this.version(1).stores(dbSchema)
  }
}

export const db = new SynChatDatabase()

export async function initializeDb() {
  if (!db.isOpen()) {
    await db.open()
  }
}
