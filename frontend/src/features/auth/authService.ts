import { clearLocalDatabase } from '@/db/db'
import { getOrCreateDeviceId } from '@/features/device/deviceId'
import { deviceStateService } from '@/features/device/deviceStateService'
import { useComposerDraftStore } from '@/features/messages/composerDraftStore'
import { notificationService } from '@/features/notifications/notificationService'
import { useNotificationStore } from '@/features/notifications/notificationStore'
import { usePresenceStore } from '@/features/presence/presenceStore'
import { presenceService } from '@/features/presence/presenceService'
import { useWebSocketStore } from '@/features/realtime/websocketStore'
import { websocketManager } from '@/features/realtime/websocketManager'
import { useShellStore } from '@/features/shell/shellStore'
import { useSyncStore } from '@/features/sync/syncStore'
import { typingService } from '@/features/typing/typingService'
import { useTypingStore } from '@/features/typing/typingStore'

import { authApi } from './authApi'
import type { LoginRequest, RegisterRequest } from './authTypes'
import { useAuthStore } from './authStore'
import {
  clearStoredTokens,
  getStoredAuthSession,
  getStoredTokens,
  storeAuthSession,
  updateStoredTokens,
} from './tokenStorage'

const ACCESS_TOKEN_REFRESH_BUFFER_MS = 60_000

function isAccessTokenFreshEnough(accessTokenExpiresAt?: string) {
  if (!accessTokenExpiresAt) {
    return false
  }

  const expiresAt = new Date(accessTokenExpiresAt).getTime()

  return Number.isFinite(expiresAt)
    ? expiresAt - Date.now() > ACCESS_TOKEN_REFRESH_BUFFER_MS
    : false
}

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

  async ensureFreshAccessToken() {
    const tokens = getStoredTokens()

    if (
      tokens.accessToken &&
      isAccessTokenFreshEnough(tokens.accessTokenExpiresAt)
    ) {
      return tokens.accessToken
    }

    if (!tokens.refreshToken) {
      clearStoredTokens()
      useAuthStore.getState().clearAuth()
      window.location.assign('/login')
      throw new Error('Missing refresh token.')
    }

    try {
      const response = await authApi.refresh({
        deviceId: getOrCreateDeviceId(),
        refreshToken: tokens.refreshToken,
      })

      updateStoredTokens({
        accessToken: response.accessToken,
        accessTokenExpiresAt: response.accessTokenExpiresAt,
        refreshToken: response.refreshToken,
        refreshTokenExpiresAt: response.refreshTokenExpiresAt,
      })

      return response.accessToken
    } catch (error) {
      clearStoredTokens()
      useAuthStore.getState().clearAuth()
      window.location.assign('/login')
      throw error
    }
  },

  async logout(navigate?: (to: string, options?: { replace?: boolean }) => void) {
    const { refreshToken } = getStoredTokens()
    const websocketStore = useWebSocketStore.getState()

    websocketStore.setShuttingDownRealtime(true)

    notificationService.clearBackendRegistrationForLogout()
    presenceService.unsubscribeAllForLogout()
    typingService.unsubscribeAllForLogout()
    deviceStateService.markBackground()

    try {
      if (refreshToken) {
        await authApi.logout({
          deviceId: getOrCreateDeviceId(),
          refreshToken,
        })
      }
    } catch (error) {
      console.warn('Backend logout failed. Continuing local logout.', error)
    }

    websocketManager.close()
    useWebSocketStore.getState().reset()
    clearStoredTokens()
    useAuthStore.getState().clearAuth()
    useShellStore.getState().reset()
    usePresenceStore.getState().clearAll()
    useTypingStore.getState().clearAll()
    useComposerDraftStore.getState().clearAll()
    useSyncStore.getState().reset()
    useNotificationStore.getState().reset()
    notificationService.resetRuntime()

    try {
      await clearLocalDatabase()
    } catch (error) {
      console.warn('Failed to clear local SynChat cache on logout.', error)
    } finally {
      if (navigate) {
        navigate('/login', { replace: true })
      } else if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }
  },

  register: (payload: RegisterRequest) => authApi.register(payload),
}
