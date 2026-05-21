import { sendCommand } from '@/features/realtime/commandSender'

function normalizeUserIds(userIds: string[]) {
  return Array.from(new Set(userIds.filter(Boolean)))
}

export const presenceService = {
  subscribe(userIds: string[]) {
    const normalizedUserIds = normalizeUserIds(userIds)

    if (normalizedUserIds.length === 0) {
      return
    }

    try {
      sendCommand('presence.subscribe', { userIds: normalizedUserIds })
    } catch (error) {
      console.warn('Failed to subscribe to presence.', error)
    }
  },

  unsubscribe(userIds: string[]) {
    const normalizedUserIds = normalizeUserIds(userIds)

    if (normalizedUserIds.length === 0) {
      return
    }

    try {
      sendCommand('presence.unsubscribe', { userIds: normalizedUserIds })
    } catch (error) {
      console.warn('Failed to unsubscribe from presence.', error)
    }
  },
}
