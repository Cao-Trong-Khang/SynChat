import type { LocalMessage } from '@/db/models'

export function getLocalMessageStatusView(
  message: LocalMessage,
  isLastOwnMessage: boolean,
) {
  if (!isLastOwnMessage) {
    return null
  }

  if (message.localStatus === 'sending') {
    return 'Sending...'
  }

  if (message.localStatus === 'failed') {
    return 'Failed'
  }

  return 'Sent'
}
