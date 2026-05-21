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
}

export function MessageStatusView({
  isLastOwnMessage,
  message,
  peerDeliveredSeq,
  peerReadSeq,
  peerUser,
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

  if (status.kind === 'failed') {
    return (
      <div className="mt-1 flex justify-end gap-2 pr-1 text-xs">
        <span className="text-red-400">{status.label}</span>
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

  if (status.kind === 'read') {
    return (
      <div className="mt-1 flex justify-end pr-1">
        <Avatar
          alt={status.label}
          className="size-4 bg-zinc-800 text-[8px] text-zinc-100"
          name={peerUser?.displayName ?? 'Read'}
          src={peerUser?.avatarUrl ?? undefined}
        />
      </div>
    )
  }

  return (
    <p className="mt-1 flex items-center justify-end gap-1 pr-1 text-xs text-zinc-500">
      {status.kind === 'delivered' ? (
        <CheckCheck aria-hidden="true" className="size-3.5" />
      ) : status.kind === 'sent' ? (
        <Check aria-hidden="true" className="size-3.5" />
      ) : null}
      <span>{status.label}</span>
    </p>
  )
}
