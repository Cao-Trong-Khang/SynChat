import { create } from 'zustand'

import type { NotificationRegistrationState } from './notificationTypes'

type NotificationStore = NotificationRegistrationState & {
  markUnsupported: (lastError?: null | string) => void
  reset: () => void
  setLastError: (lastError: null | string) => void
  setPermission: (permission: NotificationPermission | 'unsupported') => void
  setRegistering: (registering: boolean) => void
  setToken: (token: null | string) => void
}

function getInitialPermission(): NotificationPermission | 'unsupported' {
  return typeof Notification === 'undefined'
    ? 'unsupported'
    : Notification.permission
}

export const useNotificationStore = create<NotificationStore>((set) => ({
  lastError: null,
  markUnsupported: (lastError = null) =>
    set({
      lastError,
      permission: 'unsupported',
      registering: false,
      token: null,
    }),
  permission: getInitialPermission(),
  registering: false,
  reset: () =>
    set({
      lastError: null,
      permission: getInitialPermission(),
      registering: false,
      token: null,
    }),
  setLastError: (lastError) => set({ lastError }),
  setPermission: (permission) => set({ permission }),
  setRegistering: (registering) => set({ registering }),
  setToken: (token) => set({ token }),
  token: null,
}))
