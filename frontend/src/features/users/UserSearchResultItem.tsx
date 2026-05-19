import { Avatar } from '@/shared/components/Avatar'

import type { UserSearchResult } from './userTypes'

type UserSearchResultItemProps = {
  user: UserSearchResult
}

export function UserSearchResultItem({ user }: UserSearchResultItemProps) {
  return (
    <button
      className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-zinc-900 focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-zinc-600"
      type="button"
    >
      <div className="relative shrink-0">
        <Avatar
          className="bg-zinc-800 text-zinc-100"
          name={user.displayName}
          src={user.avatarUrl ?? undefined}
        />
        <span className="absolute bottom-0 right-0 size-3 rounded-full border-2 border-zinc-950 bg-zinc-600" />
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-zinc-100">
          {user.displayName}
        </p>
        <p className="mt-1 truncate text-sm text-zinc-500">{user.email}</p>
      </div>
    </button>
  )
}
