import type { LocalMessage } from '@/db/models'

type TimeSeparatorProps = {
  message: LocalMessage
}

function isSameDay(left: Date, right: Date) {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  )
}

function formatSeparatorTime(createdAt: string) {
  const date = new Date(createdAt)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const now = new Date()
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)

  const time = `${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`

  if (isSameDay(date, now)) {
    return `${time} Today`
  }

  if (isSameDay(date, yesterday)) {
    return `${time} Yesterday`
  }

  const day = [
    String(date.getDate()).padStart(2, '0'),
    String(date.getMonth() + 1).padStart(2, '0'),
    date.getFullYear(),
  ].join('/')

  return `${time} ${day}`
}

export function TimeSeparator({ message }: TimeSeparatorProps) {
  const label = formatSeparatorTime(message.createdAt)

  if (!label) {
    return null
  }

  return (
    <div className="flex justify-center py-4">
      <span className="rounded-full bg-zinc-900 px-3 py-1 text-xs font-medium text-zinc-500">
        {label}
      </span>
    </div>
  )
}
