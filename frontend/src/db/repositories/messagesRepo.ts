import { useLiveQuery } from 'dexie-react-hooks'

import { db } from '@/db/db'
import type { LocalMessage } from '@/db/models'

type SentMessagePatch = Partial<
  Pick<
    LocalMessage,
    | 'body'
    | 'conversationLocalId'
    | 'createdAt'
    | 'localId'
    | 'senderId'
    | 'seq'
  >
>

export async function addMessage(message: LocalMessage) {
  await db.messages.add(message)
}

export async function addMessages(messages: LocalMessage[]) {
  await db.messages.bulkAdd(messages)
}

export async function upsertMessage(message: LocalMessage) {
  await db.messages.put(message)
}

export function getByLocalId(localId: string) {
  return db.messages.get(localId)
}

export function getByClientMessageId(clientMessageId: string) {
  return db.messages.where('clientMessageId').equals(clientMessageId).first()
}

export function getByConversationLocalIdAndSeq(
  conversationLocalId: string,
  seq: number,
) {
  return db.messages
    .where('conversationLocalId')
    .equals(conversationLocalId)
    .filter((message) => message.seq === seq)
    .first()
}

function sortMessages(messages: LocalMessage[]) {
  return [...messages].sort((left, right) => {
    const createdAtComparison = left.createdAt.localeCompare(right.createdAt)

    if (createdAtComparison !== 0) {
      return createdAtComparison
    }

    if (left.seq === right.seq) {
      return 0
    }

    if (left.seq === null) {
      return 1
    }

    if (right.seq === null) {
      return -1
    }

    return left.seq - right.seq
  })
}

export async function listByConversationLocalId(conversationLocalId: string) {
  const messages = await db.messages
    .where('conversationLocalId')
    .equals(conversationLocalId)
    .toArray()

  return sortMessages(messages)
}

function useObserveByConversationLocalId(conversationLocalId: string) {
  return useLiveQuery(
    () => listByConversationLocalId(conversationLocalId),
    [conversationLocalId],
    [],
  )
}

export async function markSentByClientMessageId(
  clientMessageId: string,
  patch: SentMessagePatch,
) {
  await db.messages
    .where('clientMessageId')
    .equals(clientMessageId)
    .modify({
      ...patch,
      localStatus: 'sent',
    })
}

export async function replaceByClientMessageId(
  clientMessageId: string,
  patch: SentMessagePatch,
) {
  await markSentByClientMessageId(clientMessageId, patch)
}

export async function upsertByConversationLocalIdAndSeq(
  message: LocalMessage,
) {
  const existingMessage =
    message.seq === null
      ? undefined
      : await getByConversationLocalIdAndSeq(
          message.conversationLocalId,
          message.seq,
        )

  await upsertMessage({
    ...message,
    localId: existingMessage?.localId ?? message.localId,
  })
}

export async function markFailedByClientMessageId(clientMessageId: string) {
  await db.messages
    .where('clientMessageId')
    .equals(clientMessageId)
    .modify({ localStatus: 'failed' })
}

export function countByConversationLocalId(conversationLocalId: string) {
  return db.messages
    .where('conversationLocalId')
    .equals(conversationLocalId)
    .count()
}

export { useObserveByConversationLocalId as observeByConversationLocalId }
