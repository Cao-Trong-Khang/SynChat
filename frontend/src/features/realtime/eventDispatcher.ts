import { eventApplier } from '@/features/sync/eventApplier'
import type { MessageCreatedEventEnvelope } from '@/features/sync/syncTypes'

import type { PersistedEventEnvelope, ServerEnvelope } from './websocketTypes'

function isPersistedEvent(
  envelope: ServerEnvelope,
): envelope is PersistedEventEnvelope {
  return envelope.type === 'event' && 'eventType' in envelope
}

export const eventDispatcher = {
  dispatch(envelope: ServerEnvelope) {
    if (isPersistedEvent(envelope) && envelope.eventType === 'message.created') {
      void eventApplier
        .applyMessageCreated(envelope as MessageCreatedEventEnvelope)
        .catch((error: unknown) => {
          console.error('Failed to apply message.created event.', error)
        })
      return
    }

    console.log('SynChat realtime event:', envelope)
  },
}
