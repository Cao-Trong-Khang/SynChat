import type { AuthTokens } from './authTypes'

const TOKEN_STORAGE_KEY = 'synchat.authTokens'

export function getStoredTokens(): Partial<AuthTokens> {
  const rawTokens = localStorage.getItem(TOKEN_STORAGE_KEY)

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

export function storeTokens(tokens: AuthTokens) {
  localStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(tokens))
}

export function clearStoredTokens() {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}
