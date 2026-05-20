import type { LocalMessage } from '@/db/models'
import type { MessageCreatedEventPayload } from '@/features/sync/syncTypes'

export function mapMessageCreatedToLocalMessage(
  conversationLocalId: string,
  payload: MessageCreatedEventPayload['message'],
  localId = `${conversationLocalId}:${payload.seq}`,
): LocalMessage {
  return {
    body: payload.body,
    clientMessageId: payload.clientMessageId,
    conversationLocalId,
    createdAt: payload.createdAtServer,
    localId,
    localStatus: 'sent',
    senderId: payload.senderId,
    seq: payload.seq,
  }
}
