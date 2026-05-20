import { countConversations } from '@/db/repositories/conversationsRepo'
import { get as getSyncState } from '@/db/repositories/syncStateRepo'

import { conversationSync } from './conversationSync'
import { eventSync } from './eventSync'

export async function firstOpenSync() {
  const [conversationsBootstrapped, conversationCount] = await Promise.all([
    getSyncState('conversations_bootstrapped'),
    countConversations(),
  ])

  if (conversationsBootstrapped !== 'true' && conversationCount === 0) {
    await conversationSync.bootstrapFirstConversationPage()
  }

  await eventSync.syncEventsFromLastHandledSeq()
}
