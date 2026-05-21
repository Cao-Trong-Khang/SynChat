import type { LocalMessage, LocalUser } from '@/db/models'
import { Avatar } from '@/shared/components/Avatar'

type MessageBubbleProps = {
  isMine: boolean
  message: LocalMessage
  peerUser: LocalUser | undefined
  showAvatar: boolean
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

export function MessageBubble({
  isMine,
  message,
  peerUser,
  showAvatar,
  showTime,
}: MessageBubbleProps) {
  const time = showTime ? formatMessageTime(message.createdAt) : ''

  return (
    <div className={isMine ? 'flex justify-end' : 'flex justify-start gap-2'}>
      {!isMine ? (
        <div className="w-8 shrink-0">
          {showAvatar ? (
            <Avatar
              className="size-8 bg-blue-100 text-xs text-blue-700"
              name={peerUser?.displayName ?? 'Unknown user'}
              src={peerUser?.avatarUrl ?? undefined}
            />
          ) : null}
        </div>
      ) : null}

      <div className={isMine ? 'max-w-[70%]' : 'max-w-[70%]'}>
        <div
          className={
            isMine
              ? message.localStatus === 'failed'
                ? 'rounded-2xl rounded-br-md bg-red-50 px-4 py-2 text-sm leading-6 text-red-700 ring-1 ring-red-200'
                : 'rounded-2xl rounded-br-md bg-blue-50 px-4 py-2 text-sm leading-6 text-blue-950 ring-1 ring-blue-100'
              : 'rounded-2xl rounded-bl-md bg-white px-4 py-2 text-sm leading-6 text-zinc-950 ring-1 ring-zinc-200'
          }
        >
          {message.body}
        </div>
        {time ? (
          <p
            className={
              isMine
                ? 'mt-1 text-right text-xs text-zinc-500'
                : 'mt-1 text-left text-xs text-zinc-500'
            }
          >
            {time}
          </p>
        ) : null}
      </div>
    </div>
  )
}
