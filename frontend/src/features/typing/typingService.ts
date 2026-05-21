import { sendCommand } from '@/features/realtime/commandSender'

function sendTypingCommand(type: string, conversationId: string) {
  if (!conversationId) {
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
    sendTypingCommand('typing.subscribe', conversationId)
  },

  unsubscribe(conversationId: string) {
    sendTypingCommand('typing.unsubscribe', conversationId)
  },
}
