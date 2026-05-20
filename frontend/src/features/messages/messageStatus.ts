import type { LocalMessage } from '@/db/models'

export function getLocalMessageStatusView(
  message: LocalMessage,
  isLastOwnMessage: boolean,
) {
  if (message.localStatus === 'failed') {
    return 'Failed'
  }

  if (!isLastOwnMessage) {
    return null
  }

  if (message.localStatus === 'sending') {
    return 'Sending...'
  }

  return 'Sent'
}
