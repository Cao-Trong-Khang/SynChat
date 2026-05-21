import { sendCommand } from '@/features/realtime/commandSender'
import { websocketManager } from '@/features/realtime/websocketManager'
import { useWebSocketStore } from '@/features/realtime/websocketStore'

const subscribedConversationIds = new Set<string>()

function sendTypingCommand(type: string, conversationId: string) {
  if (!conversationId) {
    return
  }

  if (
    !websocketManager.isConnected() ||
    useWebSocketStore.getState().shuttingDownRealtime
  ) {
    return
  }

  try {
    sendCommand(type, { conversationId })
  } catch (error) {
    console.warn(`Failed to send ${type}.`, error)
  }
}

export const typingService = {
  start(conversationId: string) {
    sendTypingCommand('typing.start', conversationId)
  },

  stop(conversationId: string) {
    sendTypingCommand('typing.stop', conversationId)
  },

  subscribe(conversationId: string) {
    if (!conversationId) {
      return
    }

    subscribedConversationIds.add(conversationId)

    sendTypingCommand('typing.subscribe', conversationId)
  },

  unsubscribe(conversationId: string) {
    if (!conversationId) {
      return
    }

    subscribedConversationIds.delete(conversationId)

    sendTypingCommand('typing.unsubscribe', conversationId)
  },

  unsubscribeAllForLogout() {
    const conversationIds = Array.from(subscribedConversationIds)
    subscribedConversationIds.clear()

    if (!websocketManager.isConnected()) {
      return
    }

    for (const conversationId of conversationIds) {
      try {
        sendCommand('typing.unsubscribe', { conversationId })
      } catch {
        // Logout should never be blocked or noisy because realtime is closing.
      }
    }
  },
}
