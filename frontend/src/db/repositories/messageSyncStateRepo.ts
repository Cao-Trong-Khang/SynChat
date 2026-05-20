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

function useObserve(conversationLocalId: string) {
  return useLiveQuery(
    () => db.messageSyncState.get(conversationLocalId),
    [conversationLocalId],
  )
}

export { useObserve as observe }
