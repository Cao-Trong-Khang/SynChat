import type { LocalMessage } from '@/db/models'

export type MessageStatusKind =
  | 'delivered'
  | 'failed'
  | 'read'
  | 'sending'
  | 'sent'

export type MessageStatusViewModel = {
  label: string
  kind: MessageStatusKind
}

export function getLocalMessageStatusView(
  message: LocalMessage,
  isLastOwnMessage: boolean,
  peerDeliveredSeq: number,
  peerReadSeq: number,
): MessageStatusViewModel | null {
  if (!isLastOwnMessage) {
    return null
  }

  if (message.localStatus === 'failed') {
    return { kind: 'failed', label: 'Failed' }
  }

  if (message.localStatus === 'sending') {
    return { kind: 'sending', label: 'Sending...' }
  }

  if (message.seq !== null && peerReadSeq >= message.seq) {
    return { kind: 'read', label: 'Read' }
  }

  if (message.seq !== null && peerDeliveredSeq >= message.seq) {
    return { kind: 'delivered', label: 'Delivered' }
  }

  return { kind: 'sent', label: 'Sent' }
}
