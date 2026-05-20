import Dexie, { type Table } from 'dexie'

import type {
  LocalConversation,
  LocalMessage,
  LocalMessageSyncState,
  LocalOutboxCommand,
  LocalSyncState,
  LocalUser,
} from './models'
import { dbSchema, dbSchemaV1 } from './schema'

class SynChatDatabase extends Dexie {
  conversations!: Table<LocalConversation, string>
  messageSyncState!: Table<LocalMessageSyncState, string>
  messages!: Table<LocalMessage, string>
  outboxCommands!: Table<LocalOutboxCommand, string>
  syncState!: Table<LocalSyncState, string>
  users!: Table<LocalUser, string>

  constructor() {
    super('synchat')
    this.version(1).stores(dbSchemaV1)
    this.version(2).stores(dbSchema)
  }
}

export const db = new SynChatDatabase()

export async function initializeDb() {
  if (!db.isOpen()) {
    await db.open()
  }
}
