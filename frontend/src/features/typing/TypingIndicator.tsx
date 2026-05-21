import type { LocalUser } from '@/db/models'
import { useAuthStore } from '@/features/auth/authStore'

import { useTypingStore } from './typingStore'

type TypingIndicatorProps = {
  conversationId?: null | string
  peerUser?: LocalUser
}

export function TypingIndicator({
  conversationId,
  peerUser,
}: TypingIndicatorProps) {
  const currentUserId = useAuthStore((state) => state.currentUser?.id)
  const isPeerTyping = useTypingStore((state) => {
    if (!conversationId) {
      return false
    }

    const typingUsers = state.byConversationId[conversationId]

    if (!typingUsers) {
      return false
    }

    return typingUsers.some((userId) => userId !== currentUserId)
  })

  if (!conversationId || !isPeerTyping) {
    return null
  }

  const displayName = peerUser?.displayName ?? 'Typing'
  const initials = displayName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  return (
    <div className="px-5 pb-2 text-left text-xs text-zinc-500">
      <span className="inline-flex items-center gap-2 rounded-full bg-zinc-100 px-2.5 py-1">
        <span className="flex size-4 shrink-0 items-center justify-center overflow-hidden rounded-full bg-blue-100 text-[8px] font-medium leading-none text-blue-700">
          {peerUser?.avatarUrl ? (
            <img
              alt={displayName}
              className="size-full object-cover"
              src={peerUser.avatarUrl}
            />
          ) : (
            <span>{initials || 'T'}</span>
          )}
        </span>
        <span className="flex items-center gap-0.5" aria-hidden="true">
          <span className="size-1 animate-bounce rounded-full bg-zinc-400 [animation-delay:-0.2s]" />
          <span className="size-1 animate-bounce rounded-full bg-zinc-400 [animation-delay:-0.1s]" />
          <span className="size-1 animate-bounce rounded-full bg-zinc-400" />
        </span>
      </span>
    </div>
  )
}
