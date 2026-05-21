import { clsx } from 'clsx'

import { observeUser } from '@/db/repositories/usersRepo'
import { useAuthStore } from '@/features/auth/authStore'
import { OnlineStatus } from '@/features/presence/OnlineStatus'
import { usePresenceStore } from '@/features/presence/presenceStore'
import { useShellStore } from '@/features/shell/shellStore'
import { Avatar } from '@/shared/components/Avatar'

import {
  formatConversationTime,
  getConversationPreview,
  getConversationTitle,
} from './conversationService'
import type { ConversationListEntry } from './conversationTypes'

type ConversationListItemProps = {
  conversation: ConversationListEntry
}

export function ConversationListItem({ conversation }: ConversationListItemProps) {
  const currentUser = useAuthStore((state) => state.currentUser)
  const selectedChat = useShellStore((state) => state.selectedChat)
  const selectConversation = useShellStore((state) => state.selectConversation)
  const presence = usePresenceStore(
    (state) => state.byUserId[conversation.peerUserId],
  )
  const peerUser = observeUser(conversation.peerUserId)
  const isSelected =
    selectedChat.kind === 'conversation' &&
    selectedChat.conversationLocalId === conversation.localId
  const title = getConversationTitle(peerUser)
  const preview = getConversationPreview(conversation, currentUser?.id)
  const lastMessageTime = formatConversationTime(conversation.lastMessageAt)

  return (
    <button
      className={clsx(
        'flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-zinc-100 focus-visible:outline-2 focus-visible:outline-inset focus-visible:outline-zinc-400',
        isSelected && 'bg-blue-50',
      )}
      onClick={() => selectConversation(conversation.localId)}
      type="button"
    >
      <div className="relative shrink-0">
        <Avatar
          className="bg-blue-100 text-blue-700"
          name={title}
          src={peerUser?.avatarUrl ?? undefined}
        />
        <OnlineStatus
          className="absolute bottom-0 right-0 size-3 border-2 border-white"
          online={presence?.online}
        />
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-3">
          <p className="min-w-0 flex-1 truncate text-sm font-medium text-zinc-950">
            {title}
          </p>
          {lastMessageTime ? (
            <span className="shrink-0 text-xs text-zinc-500">
              {lastMessageTime}
            </span>
          ) : null}
        </div>
        <div className="mt-1 flex items-center gap-3">
          <p className="min-w-0 flex-1 truncate text-sm text-zinc-500">
            {preview}
          </p>
          {conversation.unreadCount > 0 ? (
            <span className="flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-blue-100 px-1.5 text-xs font-semibold text-blue-700">
              {conversation.unreadCount}
            </span>
          ) : null}
        </div>
      </div>
    </button>
  )
}
