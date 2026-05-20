import type { LocalUser } from '@/db/models'
import { useAuthStore } from '@/features/auth/authStore'

import { groupMessages } from './messageGrouping'
import { MessageGroup } from './MessageGroup'
import { TimeSeparator } from './TimeSeparator'
import { useMessages } from './useMessages'

type MessageListProps = {
  conversationLocalId?: string
  isLoadingLatest?: boolean
  peerUser: LocalUser | undefined
}

export function MessageList({
  conversationLocalId,
  isLoadingLatest = false,
  peerUser,
}: MessageListProps) {
  const currentUser = useAuthStore((state) => state.currentUser)
  const messages = useMessages(conversationLocalId)

  if (!conversationLocalId || messages.length === 0) {
    return (
      <section className="flex min-h-0 flex-1 items-center justify-center px-6 text-center">
        <p className="text-sm text-zinc-500">
          {isLoadingLatest ? 'Loading messages...' : 'No messages yet'}
        </p>
      </section>
    )
  }

  const lastOwnMessageLocalId = [...messages]
    .reverse()
    .find((message) => message.senderId === currentUser?.id)?.localId
  const renderItems = groupMessages(messages)

  return (
    <section className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
      <div className="space-y-3">
        {renderItems.map((item) =>
          item.type === 'time-separator' ? (
            <TimeSeparator key={item.id} message={item.message} />
          ) : (
            <MessageGroup
              currentUserId={currentUser?.id}
              group={item.group}
              key={item.id}
              lastOwnMessageLocalId={lastOwnMessageLocalId}
              peerUser={peerUser}
            />
          ),
        )}
      </div>
    </section>
  )
}
