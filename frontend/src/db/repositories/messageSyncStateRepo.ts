import { useLiveQuery } from 'dexie-react-hooks'

import { db } from '@/db/db'
import type { LocalMessageSyncState } from '@/db/models'

type MessageSyncStatePatch = Partial<
  Pick<
    LocalMessageSyncState,
    'hasMoreBefore' | 'newestLoadedSeq' | 'oldestLoadedSeq'
  >
>

export function get(conversationLocalId: string) {
  return db.messageSyncState.get(conversationLocalId)
}

export async function ensure(conversationLocalId: string) {
  const existingState = await get(conversationLocalId)

  if (existingState) {
    return existingState
  }

  const state: LocalMessageSyncState = {
    conversationLocalId,
    hasMoreBefore: true,
    newestLoadedSeq: null,
    oldestLoadedSeq: null,
  }

  await db.messageSyncState.add(state)

  return state
}

export async function updateRange(
  conversationLocalId: string,
  patch: MessageSyncStatePatch,
) {
  await ensure(conversationLocalId)
  await db.messageSyncState.update(conversationLocalId, patch)
}

export async function expandRangeForMessageSeq(
  conversationLocalId: string,
  seq: number,
) {
  const existingState = await get(conversationLocalId)

  if (!existingState) {
    await db.messageSyncState.add({
      conversationLocalId,
      hasMoreBefore: seq > 1,
      newestLoadedSeq: seq,
      oldestLoadedSeq: seq,
    })
    return
  }

  const oldestLoadedSeq =
    existingState.oldestLoadedSeq === null
      ? seq
      : Math.min(existingState.oldestLoadedSeq, seq)

  await db.messageSyncState.update(conversationLocalId, {
    hasMoreBefore:
      oldestLoadedSeq <= 1 ? false : existingState.hasMoreBefore,
    newestLoadedSeq: Math.max(existingState.newestLoadedSeq ?? seq, seq),
    oldestLoadedSeq,
  })
}

function useObserve(conversationLocalId: string) {
  return useLiveQuery(
    () => db.messageSyncState.get(conversationLocalId),
    [conversationLocalId],
  )
}

export { useObserve as observe }
