import { countConversations } from '@/db/repositories/conversationsRepo'
import { get as getSyncState } from '@/db/repositories/syncStateRepo'
import { useWebSocketStore } from '@/features/realtime/websocketStore'

import { conversationSync } from './conversationSync'
import { eventSync } from './eventSync'

export async function firstOpenSync() {
  if (useWebSocketStore.getState().shuttingDownRealtime) {
    return
  }

  const [conversationsBootstrapped, conversationCount] = await Promise.all([
    getSyncState('conversations_bootstrapped'),
    countConversations(),
  ])

  if (useWebSocketStore.getState().shuttingDownRealtime) {
    return
  }

  if (conversationsBootstrapped !== 'true' && conversationCount === 0) {
    await conversationSync.bootstrapFirstConversationPage()
  }

  if (useWebSocketStore.getState().shuttingDownRealtime) {
    return
  }

  await eventSync.syncEventsFromLastHandledSeq()
}
