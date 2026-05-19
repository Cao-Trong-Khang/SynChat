import { useEffect, useState } from 'react'

import { initializeDb } from '@/db/db'

import { LeftPane } from './LeftPane'
import { RightPane } from './RightPane'

export function ChatShell() {
  const [isOpeningDb, setIsOpeningDb] = useState(true)

  useEffect(() => {
    let isMounted = true

    initializeDb()
      .catch((error: unknown) => {
        console.error('Failed to open SynChat IndexedDB.', error)
      })
      .finally(() => {
        if (isMounted) {
          setIsOpeningDb(false)
        }
      })

    return () => {
      isMounted = false
    }
  }, [])

  if (isOpeningDb) {
    return (
      <div className="flex h-svh w-full items-center justify-center bg-zinc-950 text-sm font-medium text-zinc-400">
        Opening local database...
      </div>
    )
  }

  return (
    <div className="flex h-svh w-full overflow-hidden bg-zinc-950 text-zinc-50">
      <LeftPane />
      <RightPane />
    </div>
  )
}
