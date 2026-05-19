import type { AuthTokens, AuthUser } from './authTypes'

const AUTH_SESSION_STORAGE_KEY = 'synchat.authSession'

export type StoredAuthSession = AuthTokens & {
  currentUser: AuthUser
}

export function getStoredTokens(): Partial<AuthTokens> {
  const session = getStoredAuthSession()

  if (!session) {
    return {}
  }

  return {
    accessToken: session.accessToken,
    accessTokenExpiresAt: session.accessTokenExpiresAt,
    refreshToken: session.refreshToken,
    refreshTokenExpiresAt: session.refreshTokenExpiresAt,
  }
}

export function getStoredAuthSession(): StoredAuthSession | null {
  const rawSession = localStorage.getItem(AUTH_SESSION_STORAGE_KEY)

  if (!rawSession) {
    return null
  }

  try {
    const session = JSON.parse(rawSession) as StoredAuthSession

    if (!session.accessToken || !session.refreshToken || !session.currentUser) {
      clearStoredTokens()
      return null
    }

    return session
  } catch {
    clearStoredTokens()
    return null
  }
}

export function storeAuthSession(session: StoredAuthSession) {
  localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredTokens() {
  localStorage.removeItem(AUTH_SESSION_STORAGE_KEY)
}
