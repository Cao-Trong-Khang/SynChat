import { clsx } from 'clsx'

type AvatarProps = {
  alt?: string
  className?: string
  name?: string
  src?: string
}

export function Avatar({ alt, className, name = 'SynChat', src }: AvatarProps) {
  const initials = name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  return (
    <div
      className={clsx(
        'flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-full bg-zinc-200 text-sm font-medium text-zinc-700',
        className,
      )}
    >
      {src ? (
        <img alt={alt ?? name} className="size-full object-cover" src={src} />
      ) : (
        <span>{initials || 'SC'}</span>
      )}
    </div>
  )
}
