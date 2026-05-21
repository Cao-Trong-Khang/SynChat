import { useLiveQuery } from 'dexie-react-hooks'

import { db } from '@/db/db'
import type { LocalConversation } from '@/db/models'

export async function upsertConversation(conversation: LocalConversation) {
  await db.conversations.put(conversation)
}

export async function updateConversationPreview(
  localId: string,
  patch: Pick<
    LocalConversation,
    'lastMessageAt' | 'lastMessageSenderId' | 'lastMessageText'
  >,
) {
  await db.conversations.update(localId, patch)
}

export async function updateConversationUnreadCount(
  localId: string,
  unreadCount: number,
) {
  await db.conversations.update(localId, { unreadCount })
}

export function getByLocalId(localId: string) {
  return db.conversations.get(localId)
}

export function getByServerId(serverId: string) {
  return db.conversations.where('serverId').equals(serverId).first()
}

export function getByPeerUserId(peerUserId: string) {
  return db.conversations.where('peerUserId').equals(peerUserId).first()
}

export function countConversations() {
  return db.conversations.count()
}

export async function hasAnyConversation() {
  return (await countConversations()) > 0
}

function sortConversationList(
  conversations: LocalConversation[],
): LocalConversation[] {
  return [...conversations].sort((left, right) => {
    if (left.lastMessageAt === right.lastMessageAt) {
      return 0
    }

    if (left.lastMessageAt === null) {
      return 1
    }

    if (right.lastMessageAt === null) {
      return -1
    }

    return right.lastMessageAt.localeCompare(left.lastMessageAt)
  })
}

function useObserveConversation(localId: string) {
  return useLiveQuery(() => db.conversations.get(localId), [localId])
}

function useObserveConversationList() {
  return useLiveQuery(
    async () => sortConversationList(await db.conversations.toArray()),
    [],
    [],
  )
}

export {
  useObserveConversation as observeConversation,
  useObserveConversationList as observeConversationList,
}
