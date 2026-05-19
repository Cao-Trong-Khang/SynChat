import { Outlet } from 'react-router-dom'

export function AuthLayout() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-zinc-50 px-6 py-12">
      <Outlet />
    </main>
  )
}
