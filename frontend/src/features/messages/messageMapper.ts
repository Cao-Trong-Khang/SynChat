import type { LocalMessage } from '@/db/models'
import type { ServerMessageUpsertInput } from '@/db/repositories/messagesRepo'
import type { MessageCreatedEventPayload } from '@/features/sync/syncTypes'

import type { ServerMessage } from './messageApi'

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

export function mapServerMessageToLocalMessage(
  conversationLocalId: string,
  message: ServerMessage,
): LocalMessage {
  return {
    body: message.body,
    clientMessageId: message.clientMessageId,
    conversationLocalId,
    createdAt: message.createdAtServer,
    localId: `${conversationLocalId}:${message.seq}`,
    localStatus: 'sent',
    senderId: message.senderId,
    seq: message.seq,
  }
}

export function mapMessageCreatedToServerUpsertInput(
  conversationLocalId: string,
  payload: MessageCreatedEventPayload['message'],
): ServerMessageUpsertInput {
  return {
    body: payload.body,
    clientMessageId: payload.clientMessageId,
    conversationLocalId,
    createdAt: payload.createdAtServer,
    localStatus: 'sent',
    senderId: payload.senderId,
    seq: payload.seq,
  }
}

export function mapServerMessageToUpsertInput(
  conversationLocalId: string,
  message: ServerMessage,
): ServerMessageUpsertInput {
  return {
    body: message.body,
    clientMessageId: message.clientMessageId,
    conversationLocalId,
    createdAt: message.createdAtServer,
    localStatus: 'sent',
    senderId: message.senderId,
    seq: message.seq,
  }
}
