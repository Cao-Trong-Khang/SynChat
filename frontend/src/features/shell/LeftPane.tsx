import { ConversationList } from '@/features/conversations/ConversationList'
import { UserSearchResultList } from '@/features/users/UserSearchResultList'

import { LeftHeader } from './LeftHeader'
import { useShellStore } from './shellStore'

export function LeftPane() {
  const searchMode = useShellStore((state) => state.searchMode)

  return (
    <aside className="flex h-svh w-[360px] shrink-0 flex-col border-r border-zinc-800 bg-zinc-950">
      <LeftHeader />
      {searchMode ? <UserSearchResultList /> : <ConversationList />}
    </aside>
  )
}
