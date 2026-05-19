import { Search } from 'lucide-react'

import { useShellStore } from './shellStore'

export function SearchBox() {
  const searchKeyword = useShellStore((state) => state.searchKeyword)
  const setSearchKeyword = useShellStore((state) => state.setSearchKeyword)

  return (
    <label className="relative block flex-1">
      <Search
        aria-hidden="true"
        className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-500"
      />
      <input
        className="h-10 w-full rounded-md border border-zinc-800 bg-zinc-900/80 pl-9 pr-3 text-sm text-zinc-100 outline-none transition-colors placeholder:text-zinc-500 focus:border-zinc-600 focus:ring-2 focus:ring-zinc-800"
        onChange={(event) => setSearchKeyword(event.target.value)}
        placeholder="Search"
        type="search"
        value={searchKeyword}
      />
    </label>
  )
}
