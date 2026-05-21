import { useState } from 'react'

import { OnlineStatus } from '@/features/presence/OnlineStatus'
import { usePresenceStore } from '@/features/presence/presenceStore'
import { Avatar } from '@/shared/components/Avatar'

import { openUserSearchResult } from './userSearchService'
import type { UserSearchResult } from './userTypes'

type UserSearchResultItemProps = {
  user: UserSearchResult
}

export function UserSearchResultItem({ user }: UserSearchResultItemProps) {
  const [isOpening, setIsOpening] = useState(false)
  const presence = usePresenceStore((state) => state.byUserId[user.id])

  async function handleOpenUser() {
    if (isOpening) {
      return
    }

    setIsOpening(true)

    try {
      await openUserSearchResult(user)
    } finally {
      setIsOpening(false)
    }
  }

  return (
    <button
      className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-zinc-100 focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-zinc-400 disabled:cursor-wait disabled:opacity-70"
      disabled={isOpening}
      onClick={handleOpenUser}
      type="button"
    >
      <div className="relative shrink-0">
        <Avatar
          className="bg-blue-100 text-[#0a66c2]"
          name={user.displayName}
          src={user.avatarUrl ?? undefined}
        />
        <OnlineStatus
          className="absolute bottom-0 right-0 size-3 border-2 border-white"
          online={presence?.online}
        />
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-zinc-950">
          {user.displayName}
        </p>
        <p className="mt-1 truncate text-sm text-zinc-500">{user.email}</p>
      </div>
      {isOpening ? (
        <span className="shrink-0 text-xs text-zinc-500">Opening...</span>
      ) : null}
    </button>
  )
}
