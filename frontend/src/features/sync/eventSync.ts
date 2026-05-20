import { getNumber as getSyncStateNumber } from '@/db/repositories/syncStateRepo'
import { ApiError } from '@/shared/api/apiError'

import { eventApplier } from './eventApplier'
import { syncApi } from './syncApi'
import { useSyncStore } from './syncStore'

const EVENT_SYNC_LIMIT = 500
let isSyncRunning = false

export const eventSync = {
  async syncEventsFromLastHandledSeq() {
    if (isSyncRunning) {
      return
    }

    isSyncRunning = true

    try {
      let afterSeq = await getSyncStateNumber('last_handled_event_seq', 0)
      let hasMore = true

      while (hasMore) {
        const response = await syncApi.syncEvents(afterSeq, EVENT_SYNC_LIMIT)
        const events = [...response.events].sort(
          (left, right) => left.eventSeq - right.eventSeq,
        )

        for (const event of events) {
          await eventApplier.applyPersistedEvent(event)
        }

        hasMore = response.hasMore
        afterSeq = response.nextAfterSeq
      }
    } catch (error) {
      if (error instanceof ApiError && error.code === 'EVENT_CURSOR_EXPIRED') {
        useSyncStore.getState().setNeedsFullResync(true)
        console.warn(
          'SynChat event cursor expired. Full resync is required, but local DB was left intact.',
        )
        return
      }

      throw error
    } finally {
      isSyncRunning = false
    }
  },
}
