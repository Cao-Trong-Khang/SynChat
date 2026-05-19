import { Outlet } from 'react-router-dom'

export function ChatLayout() {
  return (
    <main className="flex min-h-svh bg-zinc-950 text-zinc-50">
      <Outlet />
    </main>
  )
}
