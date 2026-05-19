import { useEffect, useState } from 'react'

import { upsertUsers } from '@/db/repositories/usersRepo'
import { openDirectConversation } from '@/features/conversations/conversationService'

import type { UserSearchResult } from './userTypes'

export function useDebounce<TValue>(value: TValue, delayMs = 300) {
  const [debouncedValue, setDebouncedValue] = useState(value)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setDebouncedValue(value), delayMs)

    return () => window.clearTimeout(timeoutId)
  }, [delayMs, value])

  return debouncedValue
}

export function cacheUserSearchResults(results: UserSearchResult[]) {
  return upsertUsers(results)
}

export function openUserSearchResult(user: UserSearchResult) {
  return openDirectConversation(user)
}
