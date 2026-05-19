export const dbSchema = {
  conversations: 'localId, serverId, peerUserId, lastMessageAt',
  outboxCommands: 'id, commandType, status, createdAt',
  syncState: 'key',
  users: 'id, email, displayName',
} as const
