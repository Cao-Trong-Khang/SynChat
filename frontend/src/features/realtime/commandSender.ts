import { websocketManager } from './websocketManager'
import type { ClientCommandEnvelope } from './websocketTypes'

export function sendCommand(
  type: string,
  payload: unknown,
  requestId: string = crypto.randomUUID(),
) {
  if (!websocketManager.isConnected()) {
    throw new Error('Cannot send command because WebSocket is not connected.')
  }

  const envelope: ClientCommandEnvelope = {
    payload,
    requestId,
    type,
  }

  websocketManager.sendJson(envelope)

  return envelope
}
