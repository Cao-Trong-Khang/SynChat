import { db } from '@/db/db'

export async function get(key: string) {
  const syncState = await db.syncState.get(key)
  return syncState?.value ?? null
}

export async function getNumber(key: string, fallback = 0) {
  const value = await get(key)
  const numberValue = value === null ? Number.NaN : Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
}

export async function getBoolean(key: string, fallback = false) {
  const value = await get(key)

  if (value === 'true') {
    return true
  }

  if (value === 'false') {
    return false
  }

  return fallback
}

export async function set(key: string, value: null | string) {
  await db.syncState.put({ key, value })
}
