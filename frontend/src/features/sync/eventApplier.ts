import { db } from '@/db/db'
import { getByServerId, upsertConversation } from '@/db/repositories/conversationsRepo'
import { ensure as ensureMessageSyncState } from '@/db/repositories/messageSyncStateRepo'
import {
  getByClientMessageId,
  replaceByClientMessageId,
  upsertByConversationLocalIdAndSeq,
} from '@/db/repositories/messagesRepo'
import { set as setSyncState } from '@/db/repositories/syncStateRepo'
import { upsertUser } from '@/db/repositories/usersRepo'
import { mapConversationMetadataToLocal } from '@/features/conversations/conversationMapper'
import { mapMessageCreatedToLocalMessage } from '@/features/messages/messageMapper'
import { markMessageSendOutboxDone } from '@/features/outbox/outboxService'

import type { MessageCreatedEventEnvelope } from './syncTypes'

export const eventApplier = {
  async applyMessageCreated(envelope: MessageCreatedEventEnvelope) {
    await db.transaction(
      'rw',
      [
        db.conversations,
        db.messageSyncState,
        db.messages,
        db.outboxCommands,
        db.syncState,
        db.users,
      ],
      async () => {
        const { conversation, message } = envelope.payload
        const existingConversation = await getByServerId(
          conversation.conversationId,
        )
        const conversationLocalId =
          existingConversation?.localId ?? `direct:${conversation.peer.id}`
        const mappedConversation = mapConversationMetadataToLocal(
          conversation,
          conversationLocalId,
        )

        await upsertUser(mappedConversation.peer)
        await upsertConversation(mappedConversation.conversation)
        await ensureMessageSyncState(conversationLocalId)

        if (message.clientMessageId) {
          const existingMessage = await getByClientMessageId(
            message.clientMessageId,
          )

          if (existingMessage) {
            await replaceByClientMessageId(message.clientMessageId, {
              body: message.body,
              conversationLocalId,
              createdAt: message.createdAtServer,
              senderId: message.senderId,
              seq: message.seq,
            })
          } else {
            await upsertByConversationLocalIdAndSeq(
              mapMessageCreatedToLocalMessage(conversationLocalId, message),
            )
          }

          await markMessageSendOutboxDone(message.clientMessageId)
        } else {
          await upsertByConversationLocalIdAndSeq(
            mapMessageCreatedToLocalMessage(conversationLocalId, message),
          )
        }

        await setSyncState(
          'last_handled_event_seq',
          String(envelope.eventSeq),
        )
      },
    )
  },
}
