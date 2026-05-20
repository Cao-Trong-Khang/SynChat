import { db } from '@/db/db'
import { getByServerId, upsertConversation } from '@/db/repositories/conversationsRepo'
import { expandRangeForMessageSeq } from '@/db/repositories/messageSyncStateRepo'
import {
  getByClientMessageId,
  replaceByClientMessageId,
  upsertByConversationLocalIdAndSeq,
} from '@/db/repositories/messagesRepo'
import {
  getNumber as getSyncStateNumber,
  set as setSyncState,
} from '@/db/repositories/syncStateRepo'
import { upsertUser } from '@/db/repositories/usersRepo'
import { mapConversationMetadataToLocal } from '@/features/conversations/conversationMapper'
import { mapMessageCreatedToLocalMessage } from '@/features/messages/messageMapper'
import { markMessageSendOutboxDone } from '@/features/outbox/outboxService'

import type {
  MessageCreatedEventEnvelope,
  PersistedEventEnvelope,
} from './syncTypes'

export const eventApplier = {
  async applyPersistedEvent(envelope: PersistedEventEnvelope) {
    const lastHandledEventSeq = await getSyncStateNumber(
      'last_handled_event_seq',
      0,
    )

    if (envelope.eventSeq <= lastHandledEventSeq) {
      return
    }

    if (envelope.eventType === 'message.created') {
      await this.applyMessageCreated(envelope as MessageCreatedEventEnvelope)
      return
    }

    if (
      envelope.eventType === 'message.delivered' ||
      envelope.eventType === 'message.read'
    ) {
      // Phase 5: apply delivered/read receipts.
      await setSyncState('last_handled_event_seq', String(envelope.eventSeq))
      return
    }

    await setSyncState('last_handled_event_seq', String(envelope.eventSeq))
  },

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

        await expandRangeForMessageSeq(conversationLocalId, message.seq)

        await setSyncState(
          'last_handled_event_seq',
          String(envelope.eventSeq),
        )
      },
    )
  },
}
