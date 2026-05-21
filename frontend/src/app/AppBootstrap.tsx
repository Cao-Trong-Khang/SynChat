import { useEffect, useState } from 'react'
import { RouterProvider } from 'react-router-dom'

import { authService } from '@/features/auth/authService'

import { router } from './router'

export function AppBootstrap() {
  const [isBootstrapping, setIsBootstrapping] = useState(true)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      authService.bootstrapAuth()
      setIsBootstrapping(false)
    }, 0)

    return () => window.clearTimeout(timeoutId)
  }, [])

  if (isBootstrapping) {
    return (
      <main className="flex min-h-svh items-center justify-center bg-white text-sm font-medium text-zinc-700">
        Loading SynChat...
      </main>
    )
  }

  return <RouterProvider router={router} />
}
