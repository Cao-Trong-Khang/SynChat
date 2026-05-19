import { Avatar } from '@/shared/components/Avatar'
import { useAuthStore } from '@/features/auth/authStore'

import { SearchBox } from './SearchBox'

export function LeftHeader() {
  const currentUser = useAuthStore((state) => state.currentUser)

  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-zinc-800 px-4">
      <Avatar
        className="bg-zinc-700 text-zinc-100"
        name={currentUser?.displayName ?? 'SynChat User'}
        src={currentUser?.avatarUrl ?? undefined}
      />
      <SearchBox />
    </header>
  )
}
