import { createBrowserRouter, Navigate } from 'react-router-dom'

import { AuthLayout } from '@/layouts/AuthLayout'
import { ChatLayout } from '@/layouts/ChatLayout'
import { ChatPage } from '@/pages/ChatPage'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'

import { RedirectAuthenticated, RequireAuth } from './routeGuards'

export const router = createBrowserRouter([
  {
    element: <RedirectAuthenticated />,
    children: [
      {
        path: '/login',
        element: <AuthLayout />,
        children: [{ index: true, element: <LoginPage /> }],
      },
    ],
  },
  {
    element: <RedirectAuthenticated />,
    children: [
      {
      path: '/register',
      element: <AuthLayout />,
      children: [{ index: true, element: <RegisterPage /> }],
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/chat',
        element: <ChatLayout />,
        children: [{ index: true, element: <ChatPage /> }],
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/login" replace />,
  },
])
