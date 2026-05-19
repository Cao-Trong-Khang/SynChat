import { getOrCreateDeviceId } from '@/features/device/deviceId'

import { authApi } from './authApi'
import type { LoginRequest, RegisterRequest } from './authTypes'
import { useAuthStore } from './authStore'
import {
  clearStoredTokens,
  getStoredAuthSession,
  getStoredTokens,
  storeAuthSession,
} from './tokenStorage'

export const authService = {
  bootstrapAuth() {
    const session = getStoredAuthSession()

    if (!session?.accessToken) {
      useAuthStore.getState().clearAuth()
      return
    }

    useAuthStore.getState().restoreAuth(session.currentUser)
  },

  async login(payload: Omit<LoginRequest, 'deviceId' | 'platform'>) {
    const response = await authApi.login({
      ...payload,
      deviceId: getOrCreateDeviceId(),
      platform: 'web',
    })

    storeAuthSession({
      accessToken: response.accessToken,
      accessTokenExpiresAt: response.accessTokenExpiresAt,
      currentUser: response.user,
      refreshToken: response.refreshToken,
      refreshTokenExpiresAt: response.refreshTokenExpiresAt,
    })
    useAuthStore.getState().setAuth(response.user)

    return response
  },

  async logout(navigate?: (to: string, options?: { replace?: boolean }) => void) {
    const { refreshToken } = getStoredTokens()

    try {
      if (refreshToken) {
        await authApi.logout({
          deviceId: getOrCreateDeviceId(),
          refreshToken,
        })
      }
    } finally {
      clearStoredTokens()
      useAuthStore.getState().clearAuth()

      if (navigate) {
        navigate('/login', { replace: true })
      } else if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }
  },

  register: (payload: RegisterRequest) => authApi.register(payload),
}
