import { clsx } from 'clsx'

type OnlineStatusProps = {
  className?: string
  online?: boolean
}

export function OnlineStatus({ className, online = false }: OnlineStatusProps) {
  return (
    <span
      aria-label={online ? 'Online' : 'Offline'}
      className={clsx(
        'inline-block rounded-full',
        online ? 'bg-emerald-500' : 'bg-zinc-300',
        className,
      )}
    />
  )
}
