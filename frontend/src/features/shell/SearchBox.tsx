import { Search, X } from 'lucide-react'

import { useShellStore } from './shellStore'

export function SearchBox() {
  const searchKeyword = useShellStore((state) => state.searchKeyword)
  const searchMode = useShellStore((state) => state.searchMode)
  const setSearchKeyword = useShellStore((state) => state.setSearchKeyword)
  const setSearchMode = useShellStore((state) => state.setSearchMode)

  function handleKeywordChange(keyword: string) {
    setSearchKeyword(keyword)
    setSearchMode(keyword.trim().length > 0)
  }

  function handleCloseSearch() {
    setSearchKeyword('')
    setSearchMode(false)
  }

  return (
    <label className="relative block flex-1">
      <Search
        aria-hidden="true"
        className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-500"
      />
      <input
        className="h-10 w-full rounded-md border border-zinc-200 bg-white pl-9 pr-9 text-sm text-zinc-950 outline-none transition-colors placeholder:text-zinc-400 focus:border-zinc-400 focus:ring-2 focus:ring-zinc-200"
        onChange={(event) => handleKeywordChange(event.target.value)}
        placeholder="Search"
        type="text"
        value={searchKeyword}
      />
      {searchMode ? (
        <button
          aria-label="Close search"
          className="absolute right-2 top-1/2 flex size-6 -translate-y-1/2 items-center justify-center rounded-md text-zinc-500 transition-colors hover:bg-zinc-100 hover:text-zinc-900 focus-visible:outline-2 focus-visible:outline-zinc-400"
          onClick={handleCloseSearch}
          type="button"
        >
          <X aria-hidden="true" className="size-4" />
        </button>
      ) : null}
    </label>
  )
}
