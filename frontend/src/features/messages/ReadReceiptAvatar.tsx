import type { LocalUser } from '@/db/models'

type ReadReceiptAvatarProps = {
  alt: string
  peerUser: LocalUser | undefined
}

export function ReadReceiptAvatar({ alt, peerUser }: ReadReceiptAvatarProps) {
  const name = peerUser?.displayName ?? 'Read'
  const initials = name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  return (
    <span className="flex size-5 shrink-0 items-center justify-center overflow-hidden rounded-full bg-blue-100 text-[6px] font-medium leading-none text-[#005ae0]">
      {peerUser?.avatarUrl ? (
        <img
          alt={alt}
          className="size-full object-cover"
          src={peerUser.avatarUrl}
        />
      ) : (
        <span>{initials || 'R'}</span>
      )}
    </span>
  )
}
