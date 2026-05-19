import { httpClient } from '@/shared/api/httpClient'

import type {
  LoginRequest,
  LoginResponse,
  LogoutRequest,
  RefreshRequest,
  RefreshResponse,
  RegisterRequest,
  SuccessResponse,
} from './authTypes'

export const authApi = {
  login: (payload: LoginRequest) =>
    httpClient.post<LoginResponse, LoginRequest>('/auth/login', payload),
  logout: (payload: LogoutRequest) =>
    httpClient.post<SuccessResponse, LogoutRequest>('/auth/logout', payload),
  refresh: (payload: RefreshRequest) =>
    httpClient.post<RefreshResponse, RefreshRequest>('/auth/refresh', payload),
  register: (payload: RegisterRequest) =>
    httpClient.post<SuccessResponse, RegisterRequest>('/auth/register', payload),
}
