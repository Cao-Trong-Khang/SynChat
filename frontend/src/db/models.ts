export type LocalUser = {
  avatarUrl: null | string
  displayName: string
  email: string
  id: string
}

export type LocalConversation = {
  lastMessageAt: null | string
  lastMessageSenderId: null | string
  lastMessageSeq: null | number
  lastMessageText: null | string
  localId: string
  peerDeliveredSeq: number
  peerReadSeq: number
  peerUserId: string
  serverId: null | string
  unreadCount: number
}

export type LocalSyncState = {
  key: string
  value: null | string
}

export type LocalOutboxCommandStatus =
  | 'done'
  | 'failed'
  | 'pending'
  | 'processing'

export type LocalOutboxCommand = {
  commandType: string
  createdAt: string
  id: string
  payload: unknown
  retryCount: number
  status: LocalOutboxCommandStatus
}
