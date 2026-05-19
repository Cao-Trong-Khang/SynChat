import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'

import { getErrorMessage } from '@/shared/api/apiError'
import { useShellStore } from '@/features/shell/shellStore'

import { userApi } from './userApi'
import { cacheUserSearchResults, useDebounce } from './userSearchService'
import { UserSearchResultItem } from './UserSearchResultItem'

type UserSearchResultListProps = {
  keyword?: string
}

export function UserSearchResultList({ keyword }: UserSearchResultListProps) {
  const storeKeyword = useShellStore((state) => state.searchKeyword)
  const rawKeyword = keyword ?? storeKeyword
  const debouncedKeyword = useDebounce(rawKeyword.trim(), 300)
  const canSearch = debouncedKeyword.length >= 2

  const searchQuery = useQuery({
    enabled: canSearch,
    queryFn: () => userApi.searchUsers(debouncedKeyword),
    queryKey: ['users', 'search', debouncedKeyword],
  })

  useEffect(() => {
    if (searchQuery.data) {
      void cacheUserSearchResults(searchQuery.data)
    }
  }, [searchQuery.data])

  if (!canSearch) {
    return (
      <section className="flex min-h-0 flex-1 items-center justify-center px-6 text-center text-sm text-zinc-500">
        Type at least 2 characters to search.
      </section>
    )
  }

  if (searchQuery.isLoading) {
    return (
      <section className="flex min-h-0 flex-1 items-center justify-center px-6 text-sm text-zinc-500">
        Searching...
      </section>
    )
  }

  if (searchQuery.isError) {
    return (
      <section className="flex min-h-0 flex-1 items-center justify-center px-6 text-center text-sm text-red-400">
        {getErrorMessage(searchQuery.error)}
      </section>
    )
  }

  if (!searchQuery.data || searchQuery.data.length === 0) {
    return (
      <section className="flex min-h-0 flex-1 items-center justify-center px-6 text-center text-sm text-zinc-500">
        No people found.
      </section>
    )
  }

  return (
    <section className="min-h-0 flex-1 overflow-y-auto py-2">
      {searchQuery.data.map((user) => (
        <UserSearchResultItem key={user.id} user={user} />
      ))}
    </section>
  )
}
