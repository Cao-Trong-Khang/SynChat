import { getNumber as getSyncStateNumber } from '@/db/repositories/syncStateRepo'
import { usePresenceStore } from '@/features/presence/presenceStore'
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

    if (envelope.type === 'presence.snapshot') {
      applyPresenceSnapshot(envelope.payload)
      return
    }

    if (envelope.type === 'presence.changed') {
      applyPresenceChanged(envelope.payload)
      return
    }

    console.log('SynChat realtime event:', envelope)
  },
}

function applyPresenceSnapshot(payload: unknown) {
  const items = (payload as { items?: unknown }).items

  if (!Array.isArray(items)) {
    return
  }

  usePresenceStore.getState().setSnapshot(
    items
      .map((item) => {
        const candidate = item as {
          lastSeenAt?: null | string
          online?: boolean
          userId?: string
        }

        if (
          typeof candidate.userId !== 'string' ||
          typeof candidate.online !== 'boolean'
        ) {
          return null
        }

        return {
          lastSeenAt:
            typeof candidate.lastSeenAt === 'string'
              ? candidate.lastSeenAt
              : null,
          online: candidate.online,
          userId: candidate.userId,
        }
      })
      .filter((item) => item !== null),
  )
}

function applyPresenceChanged(payload: unknown) {
  const candidate = payload as {
    lastSeenAt?: null | string
    online?: boolean
    userId?: string
  }

  if (
    typeof candidate.userId !== 'string' ||
    typeof candidate.online !== 'boolean'
  ) {
    return
  }

  usePresenceStore.getState().setPresence(
    candidate.userId,
    candidate.online,
    typeof candidate.lastSeenAt === 'string' ? candidate.lastSeenAt : null,
  )
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
