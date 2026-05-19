import { env } from '@/config/env'
import { getStoredTokens } from '@/features/auth/tokenStorage'

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

async function request<TResponse>(
  path: string,
  { body, headers, method = 'GET' }: RequestOptions = {},
): Promise<TResponse> {
  const tokens = getStoredTokens()
  const shouldAttachAuth = tokens.accessToken && !isPublicAuthPath(path)

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    body: body === undefined ? undefined : JSON.stringify(body),
    headers: {
      'Content-Type': 'application/json',
      ...(shouldAttachAuth
        ? { Authorization: `Bearer ${tokens.accessToken}` }
        : {}),
      ...headers,
    },
    method,
  })

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
