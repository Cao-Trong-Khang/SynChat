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
  const [isConfirmingLogout, setIsConfirmingLogout] = useState(false)
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
          onClick={() => setIsConfirmingLogout(true)}
        >
          <LogOut aria-hidden="true" className="size-4" />
          {isLoggingOut ? 'Logging out...' : 'Log out'}
        </Button>
      </footer>
      {isConfirmingLogout ? (
        <div
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/30 p-4"
          role="dialog"
        >
          <div className="w-full max-w-sm rounded-lg border border-zinc-200 bg-white p-5 shadow-xl">
            <h2 className="text-base font-semibold text-zinc-950">
              Log out?
            </h2>
            <p className="mt-2 text-sm leading-6 text-zinc-500">
              You will return to the login screen and local chat cache will be
              cleared on this device.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <Button
                className="bg-transparent text-zinc-600 hover:bg-zinc-100 hover:text-zinc-950 focus-visible:outline-zinc-400"
                disabled={isLoggingOut}
                onClick={() => setIsConfirmingLogout(false)}
              >
                Cancel
              </Button>
              <Button
                className="gap-2"
                disabled={isLoggingOut}
                onClick={() => void handleLogout()}
              >
                <LogOut aria-hidden="true" className="size-4" />
                {isLoggingOut ? 'Logging out...' : 'Log out'}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </aside>
  )
}
