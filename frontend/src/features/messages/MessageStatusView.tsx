import type { LocalMessage } from '@/db/models'

import { messageService } from './messageService'
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

  if (message.localStatus === 'failed') {
    return (
      <div className="mt-1 flex justify-end gap-2 pr-1 text-xs">
        <span className="text-red-400">{status}</span>
        <button
          className="font-medium text-zinc-300 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-500"
          onClick={() => void messageService.retryMessage(message.localId)}
          type="button"
        >
          Retry
        </button>
      </div>
    )
  }

  return <p className="mt-1 pr-1 text-right text-xs text-zinc-500">{status}</p>
}
