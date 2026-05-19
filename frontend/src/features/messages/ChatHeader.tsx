import type { LocalUser } from '@/db/models'
import { Avatar } from '@/shared/components/Avatar'

type ChatHeaderProps = {
  peerUser: LocalUser | undefined
}

export function ChatHeader({ peerUser }: ChatHeaderProps) {
  const displayName = peerUser?.displayName ?? 'Unknown user'
  const email = peerUser?.email ?? ''

  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-zinc-800 px-5">
      <div className="relative shrink-0">
        <Avatar
          className="bg-zinc-800 text-zinc-100"
          name={displayName}
          src={peerUser?.avatarUrl ?? undefined}
        />
        <span className="absolute bottom-0 right-0 size-3 rounded-full border-2 border-zinc-950 bg-zinc-600" />
      </div>

      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-zinc-100">
          {displayName}
        </p>
        <p className="truncate text-xs text-zinc-500">
          {email ? `${email} · Offline` : 'Offline'}
        </p>
      </div>
    </header>
  )
}
