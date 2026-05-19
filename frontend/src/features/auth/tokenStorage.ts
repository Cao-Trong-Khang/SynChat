import type { AuthTokens, AuthUser } from './authTypes'

const AUTH_SESSION_STORAGE_KEY = 'synchat.authSession'

export type StoredAuthSession = AuthTokens & {
  currentUser: AuthUser
}

const LEGACY_TOKEN_STORAGE_KEY = 'synchat.authTokens'

export function getStoredTokens(): Partial<AuthTokens> {
  const session = getStoredAuthSession()

  if (session) {
    return {
      accessToken: session.accessToken,
      accessTokenExpiresAt: session.accessTokenExpiresAt,
      refreshToken: session.refreshToken,
      refreshTokenExpiresAt: session.refreshTokenExpiresAt,
    }
  }

  const rawTokens = localStorage.getItem(LEGACY_TOKEN_STORAGE_KEY)

  if (!rawTokens) {
    return {}
  }

  try {
    return JSON.parse(rawTokens) as Partial<AuthTokens>
  } catch {
    clearStoredTokens()
    return {}
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
  localStorage.removeItem(LEGACY_TOKEN_STORAGE_KEY)
}

export function storeTokens(tokens: AuthTokens) {
  localStorage.setItem(LEGACY_TOKEN_STORAGE_KEY, JSON.stringify(tokens))
}

export function clearStoredTokens() {
  localStorage.removeItem(AUTH_SESSION_STORAGE_KEY)
  localStorage.removeItem(LEGACY_TOKEN_STORAGE_KEY)
}
