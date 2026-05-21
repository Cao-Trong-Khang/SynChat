import { db } from '@/db/db'
import {
  getByServerId,
  updateConversationUnreadCount,
  updatePeerDeliveredSeqIfNewer,
  updatePeerReadSeqIfNewer,
  upsertConversation,
} from '@/db/repositories/conversationsRepo'
import { expandRangeForMessageSeq } from '@/db/repositories/messageSyncStateRepo'
import { upsertServerMessage } from '@/db/repositories/messagesRepo'
import {
  getNumber as getSyncStateNumber,
  set as setSyncState,
} from '@/db/repositories/syncStateRepo'
import { upsertUser } from '@/db/repositories/usersRepo'
import { useAuthStore } from '@/features/auth/authStore'
import { mapConversationMetadataToLocal } from '@/features/conversations/conversationMapper'
import { mapMessageCreatedToServerUpsertInput } from '@/features/messages/messageMapper'
import { markMessageSendOutboxDone } from '@/features/outbox/outboxService'
import { receiptService } from '@/features/receipts/receiptService'
import { useShellStore } from '@/features/shell/shellStore'

import type {
  MessageCreatedEventEnvelope,
  MessageDeliveredEventEnvelope,
  MessageReadEventEnvelope,
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

    if (envelope.eventType === 'message.delivered') {
      await this.applyMessageDelivered(
        envelope as MessageDeliveredEventEnvelope,
      )
      return
    }

    if (envelope.eventType === 'message.read') {
      await this.applyMessageRead(envelope as MessageReadEventEnvelope)
      return
    }

    await setSyncState('last_handled_event_seq', String(envelope.eventSeq))
  },

  async applyMessageCreated(envelope: MessageCreatedEventEnvelope) {
    let appliedConversationLocalId: string | null = null

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
        appliedConversationLocalId = conversationLocalId
        const mappedConversation = mapConversationMetadataToLocal(
          conversation,
          conversationLocalId,
        )

        await upsertUser(mappedConversation.peer)
        await upsertConversation(mappedConversation.conversation)

        await upsertServerMessage(
          mapMessageCreatedToServerUpsertInput(conversationLocalId, message),
        )

        if (message.clientMessageId) {
          await markMessageSendOutboxDone(message.clientMessageId)
        }

        await expandRangeForMessageSeq(conversationLocalId, message.seq)

        await setSyncState(
          'last_handled_event_seq',
          String(envelope.eventSeq),
        )
      },
    )

    const selectedChat = useShellStore.getState().selectedChat

    if (
      appliedConversationLocalId &&
      selectedChat.kind === 'draft' &&
      selectedChat.peerUserId === envelope.payload.conversation.peer.id
    ) {
      useShellStore.getState().selectConversation(appliedConversationLocalId)
    }

    if (!appliedConversationLocalId) {
      return
    }

    const currentUserId = useAuthStore.getState().currentUser?.id

    if (envelope.payload.message.senderId === currentUserId) {
      return
    }

    await receiptService.markDeliveredUpTo(
      appliedConversationLocalId,
      envelope.payload.message.seq,
    )

    const updatedSelectedChat = useShellStore.getState().selectedChat

    if (
      updatedSelectedChat.kind === 'conversation' &&
      updatedSelectedChat.conversationLocalId === appliedConversationLocalId
    ) {
      await receiptService.markReadUpTo(
        appliedConversationLocalId,
        envelope.payload.message.seq,
      )
    }
  },

  async applyMessageDelivered(envelope: MessageDeliveredEventEnvelope) {
    await db.transaction('rw', [db.conversations, db.syncState], async () => {
      const conversation = await getByServerId(envelope.payload.conversationId)

      if (conversation) {
        if (envelope.payload.userId === conversation.peerUserId) {
          await updatePeerDeliveredSeqIfNewer(
            conversation.localId,
            envelope.payload.deliveredSeq,
          )
        }
      }

      await setSyncState('last_handled_event_seq', String(envelope.eventSeq))
    })
  },

  async applyMessageRead(envelope: MessageReadEventEnvelope) {
    await db.transaction('rw', [db.conversations, db.syncState], async () => {
      const conversation = await getByServerId(envelope.payload.conversationId)

      if (conversation) {
        const currentUserId = useAuthStore.getState().currentUser?.id

        if (envelope.payload.userId === conversation.peerUserId) {
          await updatePeerReadSeqIfNewer(
            conversation.localId,
            envelope.payload.readSeq,
          )
        } else if (envelope.payload.userId === currentUserId) {
          await updateConversationUnreadCount(conversation.localId, 0)
        }
      }

      await setSyncState('last_handled_event_seq', String(envelope.eventSeq))
    })
  },
}
