import { countConversations } from '@/db/repositories/conversationsRepo'
import { get as getSyncState } from '@/db/repositories/syncStateRepo'

import { conversationSync } from './conversationSync'

export async function firstOpenSync() {
  const [conversationsBootstrapped, conversationCount] = await Promise.all([
    getSyncState('conversations_bootstrapped'),
    countConversations(),
  ])

  if (conversationsBootstrapped !== 'true' && conversationCount === 0) {
    await conversationSync.bootstrapFirstConversationPage()
    return
  }

  // Phase 4: call syncEvents from last_handled_event_seq.
}
