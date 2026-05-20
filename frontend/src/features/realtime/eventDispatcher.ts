import { getNumber as getSyncStateNumber } from '@/db/repositories/syncStateRepo'
import { eventApplier } from '@/features/sync/eventApplier'
import { eventSync } from '@/features/sync/eventSync'
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
      void dispatchPersistedEvent(envelope as SyncPersistedEventEnvelope)
      return
    }

    console.log('SynChat realtime event:', envelope)
  },
}

async function dispatchPersistedEvent(envelope: SyncPersistedEventEnvelope) {
  const lastHandledEventSeq = await getSyncStateNumber(
    'last_handled_event_seq',
    0,
  )
  const expectedNext = lastHandledEventSeq + 1

  if (envelope.eventSeq <= lastHandledEventSeq) {
    console.log('SynChat duplicate event ignored.', {
      eventSeq: envelope.eventSeq,
      lastHandledEventSeq,
    })
    return
  }

  if (envelope.eventSeq === expectedNext) {
    await eventApplier.applyPersistedEvent(envelope)
    return
  }

  console.warn('SynChat event gap detected.', {
    eventSeq: envelope.eventSeq,
    expectedNext,
    lastHandledEventSeq,
  })

  await eventSync.syncEventsFromLastHandledSeq()

  const updatedLastHandledEventSeq = await getSyncStateNumber(
    'last_handled_event_seq',
    0,
  )

  if (envelope.eventSeq <= updatedLastHandledEventSeq) {
    console.log('SynChat duplicate event ignored.', {
      eventSeq: envelope.eventSeq,
      lastHandledEventSeq: updatedLastHandledEventSeq,
    })
    return
  }

  if (envelope.eventSeq === updatedLastHandledEventSeq + 1) {
    await eventApplier.applyPersistedEvent(envelope)
    return
  }

  console.warn('SynChat event gap remains after sync recovery.', {
    eventSeq: envelope.eventSeq,
    expectedNext: updatedLastHandledEventSeq + 1,
    lastHandledEventSeq: updatedLastHandledEventSeq,
  })
}
