export const dbSchema = {
  conversations: 'localId, serverId, peerUserId, lastMessageAt',
  messageSyncState: 'conversationLocalId',
  messages:
    'localId, conversationLocalId, clientMessageId, seq, createdAt, localStatus',
  outboxCommands: 'id, commandType, status, createdAt',
  syncState: 'key',
  users: 'id, email, displayName',
} as const

export const dbSchemaV1 = {
  conversations: dbSchema.conversations,
  outboxCommands: dbSchema.outboxCommands,
  syncState: dbSchema.syncState,
  users: dbSchema.users,
} as const
