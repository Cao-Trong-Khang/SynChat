import { useLiveQuery } from 'dexie-react-hooks'

import { db } from '@/db/db'
import type { LocalUser } from '@/db/models'

export async function upsertUser(user: LocalUser) {
  await db.users.put(user)
}

export async function upsertUsers(users: LocalUser[]) {
  await db.users.bulkPut(users)
}

export function getUser(id: string) {
  return db.users.get(id)
}

function useObserveUser(id: string) {
  return useLiveQuery(() => db.users.get(id), [id])
}

export { useObserveUser as observeUser }
