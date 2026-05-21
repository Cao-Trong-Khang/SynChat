import { sendCommand } from '@/features/realtime/commandSender'
import { websocketManager } from '@/features/realtime/websocketManager'
import { useWebSocketStore } from '@/features/realtime/websocketStore'

const subscribedUserIds = new Set<string>()

function normalizeUserIds(userIds: string[]) {
  return Array.from(new Set(userIds.filter(Boolean)))
}

function sendPresenceCommand(type: string, userIds: string[]) {
  sendCommand(type, { userIds })
}

export const presenceService = {
  subscribe(userIds: string[]) {
    const normalizedUserIds = normalizeUserIds(userIds)

    if (normalizedUserIds.length === 0) {
      return
    }

    for (const userId of normalizedUserIds) {
      subscribedUserIds.add(userId)
    }

    try {
      if (websocketManager.isConnected()) {
        sendPresenceCommand('presence.subscribe', normalizedUserIds)
      }
    } catch (error) {
      console.warn('Failed to subscribe to presence.', error)
    }
  },

  unsubscribe(userIds: string[]) {
    const normalizedUserIds = normalizeUserIds(userIds)

    if (normalizedUserIds.length === 0) {
      return
    }

    for (const userId of normalizedUserIds) {
      subscribedUserIds.delete(userId)
    }

    if (
      !websocketManager.isConnected() ||
      useWebSocketStore.getState().shuttingDownRealtime
    ) {
      return
    }

    try {
      sendPresenceCommand('presence.unsubscribe', normalizedUserIds)
    } catch (error) {
      console.warn('Failed to unsubscribe from presence.', error)
    }
  },

  unsubscribeAllForLogout() {
    const userIds = Array.from(subscribedUserIds)
    subscribedUserIds.clear()

    if (userIds.length === 0 || !websocketManager.isConnected()) {
      return
    }

    try {
      sendPresenceCommand('presence.unsubscribe', userIds)
    } catch {
      // Logout should never be blocked or noisy because realtime is closing.
    }
  },
}
