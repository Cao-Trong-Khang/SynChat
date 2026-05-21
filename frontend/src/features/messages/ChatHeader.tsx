import { useEffect } from 'react'

import type { LocalUser } from '@/db/models'
import { OnlineStatus } from '@/features/presence/OnlineStatus'
import { presenceService } from '@/features/presence/presenceService'
import { usePresenceStore } from '@/features/presence/presenceStore'
import { Avatar } from '@/shared/components/Avatar'

type ChatHeaderProps = {
  peerUser: LocalUser | undefined
}

export function ChatHeader({ peerUser }: ChatHeaderProps) {
  const displayName = peerUser?.displayName ?? 'Unknown user'
  const email = peerUser?.email ?? ''
  const presence = usePresenceStore((state) =>
    peerUser ? state.byUserId[peerUser.id] : undefined,
  )
  const presenceText = getPresenceText(presence)

  useEffect(() => {
    if (!peerUser) {
      return
    }

    presenceService.subscribe([peerUser.id])

    return () => {
      presenceService.unsubscribe([peerUser.id])
    }
  }, [peerUser])

  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-zinc-200 bg-white px-5">
      <div className="relative shrink-0">
        <Avatar
          className="bg-blue-100 text-blue-700"
          name={displayName}
          src={peerUser?.avatarUrl ?? undefined}
        />
        <OnlineStatus
          className="absolute bottom-0 right-0 size-3 border-2 border-white"
          online={presence?.online}
        />
      </div>

      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-zinc-950">
          {displayName}
        </p>
        <p className="truncate text-xs text-zinc-500">
          {email ? `${email} · ${presenceText}` : presenceText}
        </p>
      </div>
    </header>
  )
}

function getPresenceText(
  presence: { lastSeenAt: null | string; online: boolean } | undefined,
) {
  if (presence?.online) {
    return 'now'
  }

  if (presence?.lastSeenAt) {
    return `Last seen ${formatLastSeen(presence.lastSeenAt)}`
  }

  return 'Offline'
}

function formatLastSeen(lastSeenAt: string) {
  const date = new Date(lastSeenAt)

  if (Number.isNaN(date.getTime())) {
    return lastSeenAt
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}
