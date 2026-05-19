import { db } from '@/db/db'

export async function get(key: string) {
  const syncState = await db.syncState.get(key)
  return syncState?.value ?? null
}

export async function set(key: string, value: null | string) {
  await db.syncState.put({ key, value })
}
