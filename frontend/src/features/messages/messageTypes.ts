import type { LocalMessage, LocalUser } from '@/db/models'

export type SendMessageCommandPayload =
  | {
      body: string
      clientMessageId: string
      conversationId: string
    }
  | {
      body: string
      clientMessageId: string
      recipientId: string
    }

export type MessageGroupModel = {
  id: string
  messages: LocalMessage[]
  senderId: string
}

export type MessageRenderItem =
  | {
      id: string
      message: LocalMessage
      type: 'time-separator'
    }
  | {
      group: MessageGroupModel
      id: string
      type: 'group'
    }

export type MessagePeer = LocalUser | undefined
