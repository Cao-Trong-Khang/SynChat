import type { LocalMessage, LocalUser } from '@/db/models'

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
