import type { ServerEnvelope } from './websocketTypes'

export const eventDispatcher = {
  dispatch(envelope: ServerEnvelope) {
    console.log('SynChat realtime event:', envelope)
  },
}
