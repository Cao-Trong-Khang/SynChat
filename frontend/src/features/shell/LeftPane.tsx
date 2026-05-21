import { useState } from 'react'
import { LogOut } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { authService } from '@/features/auth/authService'
import { ConversationList } from '@/features/conversations/ConversationList'
import { UserSearchResultList } from '@/features/users/UserSearchResultList'
import { Button } from '@/shared/components/Button'

import { LeftHeader } from './LeftHeader'
import { useShellStore } from './shellStore'

export function LeftPane() {
  const navigate = useNavigate()
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const searchMode = useShellStore((state) => state.searchMode)

  async function handleLogout() {
    if (isLoggingOut) {
      return
    }

    setIsLoggingOut(true)
    await authService.logout(navigate)
  }

  return (
    <aside className="flex h-svh w-[360px] shrink-0 flex-col border-r border-zinc-200 bg-white">
      <LeftHeader />
      <div className="flex min-h-0 flex-1 flex-col">
        {searchMode ? <UserSearchResultList /> : <ConversationList />}
      </div>
      <footer className="shrink-0 border-t border-zinc-200 p-3">
        <Button
          className="h-10 w-full justify-start gap-2 bg-transparent px-3 text-zinc-500 hover:bg-zinc-100 hover:text-zinc-950 focus-visible:outline-zinc-400"
          disabled={isLoggingOut}
          onClick={() => void handleLogout()}
        >
          <LogOut aria-hidden="true" className="size-4" />
          {isLoggingOut ? 'Logging out...' : 'Log out'}
        </Button>
      </footer>
    </aside>
  )
}
