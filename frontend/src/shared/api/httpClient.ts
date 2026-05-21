import { env } from '@/config/env'

import { ApiError, type ApiErrorPayload } from './apiError'

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

type RequestOptions = {
  body?: unknown
  headers?: HeadersInit
  method?: HttpMethod
}

type ErrorResponse = {
  error?: ApiErrorPayload
}

const PUBLIC_AUTH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh']

function isPublicAuthPath(path: string) {
  return PUBLIC_AUTH_PATHS.some((publicPath) => path === publicPath)
}

async function getAuthService() {
  return (await import('@/features/auth/authService')).authService
}

async function getAccessTokenForRequest(path: string) {
  if (isPublicAuthPath(path)) {
    return null
  }

  const authService = await getAuthService()

  return authService.ensureFreshAccessToken()
}

async function executeRequest(
  path: string,
  { body, headers, method = 'GET' }: RequestOptions = {},
  accessToken: null | string,
) {
  return fetch(`${env.apiBaseUrl}${path}`, {
    body: body === undefined ? undefined : JSON.stringify(body),
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...headers,
    },
    method,
  })
}

async function request<TResponse>(
  path: string,
  { body, headers, method = 'GET' }: RequestOptions = {},
): Promise<TResponse> {
  const isProtectedRequest = !isPublicAuthPath(path)
  const accessToken = await getAccessTokenForRequest(path)

  let response = await executeRequest(
    path,
    { body, headers, method },
    accessToken,
  )

  if (isProtectedRequest && response.status === 401) {
    try {
      const authService = await getAuthService()
      const refreshedAccessToken = await authService.ensureFreshAccessToken({
        forceRefresh: true,
      })

      response = await executeRequest(
        path,
        { body, headers, method },
        refreshedAccessToken,
      )

      if (response.status === 401) {
        await authService.forceLogout('Unauthorized after refreshing token.')
      }
    } catch (error) {
      const authService = await getAuthService()
      await authService.forceLogout('Failed to refresh token for request.')
      throw error
    }
  }

  if (!response.ok) {
    throw await createApiError(response)
  }

  if (response.status === 204) {
    return undefined as TResponse
  }

  return response.json() as Promise<TResponse>
}

async function createApiError(response: Response): Promise<ApiError> {
  const fallbackMessage = `Request failed with status ${response.status}.`

  try {
    const payload = (await response.json()) as ErrorResponse
    return new ApiError(
      payload.error?.message ?? fallbackMessage,
      response.status,
      payload.error?.code,
    )
  } catch {
    return new ApiError(fallbackMessage, response.status)
  }
}

export const httpClient = {
  get: <TResponse>(path: string) => request<TResponse>(path),
  post: <TResponse, TBody = unknown>(path: string, body?: TBody) =>
    request<TResponse>(path, { body, method: 'POST' }),
}
