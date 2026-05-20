import type { SendMessageCommandPayload } from '@/features/messages/messageTypes'

export type OutboxCommandType = 'messages.send'

export const MAX_OUTBOX_RETRY_COUNT = 3

export type OutboxCommandPayloadByType = {
  'messages.send': SendMessageCommandPayload
}
