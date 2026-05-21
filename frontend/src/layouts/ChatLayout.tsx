import { Outlet } from 'react-router-dom'

export function ChatLayout() {
  return (
    <main className="h-svh overflow-hidden bg-white text-zinc-950">
      <Outlet />
    </main>
  )
}
