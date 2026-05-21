import { useAuthStore } from '@/features/auth/authStore'

import { useTypingStore } from './typingStore'

type TypingIndicatorProps = {
  conversationId?: null | string
}

export function TypingIndicator({ conversationId }: TypingIndicatorProps) {
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

  return (
    <div className="px-5 pb-2 text-left text-xs text-zinc-500">
      <span className="inline-flex items-center gap-1 rounded-full bg-zinc-100 px-3 py-1">
        <span>Typing</span>
        <span className="flex items-center gap-0.5" aria-hidden="true">
          <span className="size-1 animate-bounce rounded-full bg-zinc-400 [animation-delay:-0.2s]" />
          <span className="size-1 animate-bounce rounded-full bg-zinc-400 [animation-delay:-0.1s]" />
          <span className="size-1 animate-bounce rounded-full bg-zinc-400" />
        </span>
      </span>
    </div>
  )
}
