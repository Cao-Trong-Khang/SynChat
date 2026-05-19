import { RouterProvider } from 'react-router-dom'

import { router } from './router'

export function AppBootstrap() {
  return <RouterProvider router={router} />
}
