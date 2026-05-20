import type { SendMessageCommandPayload } from '@/features/messages/messageTypes'

export type OutboxCommandType = 'messages.send'

export type OutboxCommandPayloadByType = {
  'messages.send': SendMessageCommandPayload
}
