import { getOrCreateDeviceId } from '@/features/device/deviceId'

import { authApi } from './authApi'
import type { LoginRequest, RegisterRequest } from './authTypes'
import { useAuthStore } from './authStore'
import { clearStoredTokens, getStoredTokens, storeTokens } from './tokenStorage'

export const authService = {
  async login(payload: Omit<LoginRequest, 'deviceId' | 'platform'>) {
    const response = await authApi.login({
      ...payload,
      deviceId: getOrCreateDeviceId(),
      platform: 'web',
    })

    storeTokens({
      accessToken: response.accessToken,
      accessTokenExpiresAt: response.accessTokenExpiresAt,
      refreshToken: response.refreshToken,
      refreshTokenExpiresAt: response.refreshTokenExpiresAt,
    })
    useAuthStore.getState().setAuth(response.user)

    return response
  },

  async logout() {
    const { refreshToken } = getStoredTokens()

    if (refreshToken) {
      await authApi.logout({
        deviceId: getOrCreateDeviceId(),
        refreshToken,
      })
    }

    clearStoredTokens()
    useAuthStore.getState().clearAuth()
  },

  register: (payload: RegisterRequest) => authApi.register(payload),
}
