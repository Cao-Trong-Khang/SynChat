export type AuthUser = {
  avatarUrl: null | string
  displayName: string
  email: string
  id: string
}

export type RegisterRequest = {
  displayName: string
  email: string
  password: string
}

export type LoginRequest = {
  deviceId: string
  email: string
  password: string
  platform: 'web'
}

export type RefreshRequest = {
  deviceId: string
  refreshToken: string
}

export type LogoutRequest = RefreshRequest

export type AuthTokens = {
  accessToken: string
  accessTokenExpiresAt?: string
  refreshToken: string
  refreshTokenExpiresAt?: string
}

export type LoginResponse = AuthTokens & {
  user: AuthUser
}

export type RefreshResponse = AuthTokens

export type SuccessResponse = {
  success: boolean
}
