import { Outlet } from 'react-router-dom'

export function ChatLayout() {
  return (
    <main className="h-svh overflow-hidden bg-zinc-950 text-zinc-50">
      <Outlet />
    </main>
  )
}
