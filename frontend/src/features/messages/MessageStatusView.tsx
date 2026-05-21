import { Check, CheckCheck } from 'lucide-react'

import type { LocalMessage, LocalUser } from '@/db/models'
import { Avatar } from '@/shared/components/Avatar'

import { messageService } from './messageService'
import { getLocalMessageStatusView } from './messageStatus'

type MessageStatusViewProps = {
  isLastOwnMessage: boolean
  message: LocalMessage
  peerDeliveredSeq: number
  peerReadSeq: number
  peerUser: LocalUser | undefined
  showTime: boolean
}

function formatMessageTime(createdAt: string) {
  const date = new Date(createdAt)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return new Intl.DateTimeFormat(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  }).format(date)
}

export function MessageStatusView({
  isLastOwnMessage,
  message,
  peerDeliveredSeq,
  peerReadSeq,
  peerUser,
  showTime,
}: MessageStatusViewProps) {
  const status = getLocalMessageStatusView(
    message,
    isLastOwnMessage,
    peerDeliveredSeq,
    peerReadSeq,
  )

  if (!status) {
    return null
  }

  const time = showTime ? formatMessageTime(message.createdAt) : ''

  if (status.kind === 'failed') {
    return (
      <div className="mt-1 flex items-center justify-end gap-2 pr-1 text-xs">
        {time ? <span className="text-zinc-500">{time}</span> : null}
        <span className="text-red-400">{status.label}</span>
        <button
          className="font-medium text-zinc-700 hover:text-zinc-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-zinc-400"
          onClick={() => void messageService.retryMessage(message.localId)}
          type="button"
        >
          Retry
        </button>
      </div>
    )
  }

  if (status.kind === 'read') {
    return (
      <div className="mt-1 flex items-center justify-end gap-2 pr-1 text-xs text-zinc-500">
        {time ? <span>{time}</span> : null}
        <Avatar
          alt={status.label}
          className="size-3.5 bg-blue-100 text-[7px] text-blue-700"
          name={peerUser?.displayName ?? 'Read'}
          src={peerUser?.avatarUrl ?? undefined}
        />
      </div>
    )
  }

  return (
    <p className="mt-1 flex items-center justify-end gap-2 pr-1 text-xs text-zinc-500">
      {time ? <span>{time}</span> : null}
      <span className="flex items-center gap-1">
      {status.kind === 'delivered' ? (
        <CheckCheck aria-hidden="true" className="size-3.5" />
      ) : status.kind === 'sent' ? (
        <Check aria-hidden="true" className="size-3.5" />
      ) : null}
      <span>{status.label}</span>
      </span>
    </p>
  )
}
