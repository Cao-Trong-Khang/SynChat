import type { LocalMessage } from '@/db/models'

import { getLocalMessageStatusView } from './messageStatus'

type MessageStatusViewProps = {
  isLastOwnMessage: boolean
  message: LocalMessage
}

export function MessageStatusView({
  isLastOwnMessage,
  message,
}: MessageStatusViewProps) {
  const status = getLocalMessageStatusView(message, isLastOwnMessage)

  if (!status) {
    return null
  }

  return (
    <p className="mt-1 pr-1 text-right text-xs text-zinc-500">{status}</p>
  )
}
