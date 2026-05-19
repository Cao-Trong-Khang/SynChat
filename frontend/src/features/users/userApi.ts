import { httpClient } from '@/shared/api/httpClient'

import type { UserSearchResponse, UserSearchResult } from './userTypes'

export const userApi = {
  async searchUsers(q: string): Promise<UserSearchResult[]> {
    const params = new URLSearchParams({ q })
    const response = await httpClient.get<UserSearchResponse>(
      `/users/search?${params.toString()}`,
    )

    return response.items
  },
}
