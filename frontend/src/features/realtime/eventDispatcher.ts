import { eventApplier } from '@/features/sync/eventApplier'
import type {
  PersistedEventEnvelope as SyncPersistedEventEnvelope,
} from '@/features/sync/syncTypes'

import type { PersistedEventEnvelope, ServerEnvelope } from './websocketTypes'

function isPersistedEvent(
  envelope: ServerEnvelope,
): envelope is PersistedEventEnvelope {
  return envelope.type === 'event' && 'eventType' in envelope
}

export const eventDispatcher = {
  dispatch(envelope: ServerEnvelope) {
    if (isPersistedEvent(envelope)) {
      void eventApplier
        .applyPersistedEvent(envelope as SyncPersistedEventEnvelope)
        .catch((error: unknown) => {
          console.error('Failed to apply persisted event.', error)
        })
      return
    }

    console.log('SynChat realtime event:', envelope)
  },
}
