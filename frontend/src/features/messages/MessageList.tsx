import { useLayoutEffect, useRef } from 'react'

import type { LocalUser } from '@/db/models'
import { useAuthStore } from '@/features/auth/authStore'

import { groupMessages } from './messageGrouping'
import { MessageGroup } from './MessageGroup'
import { TimeSeparator } from './TimeSeparator'
import { useMessages } from './useMessages'

type MessageListProps = {
  conversationLocalId?: string
  hasMoreBefore?: boolean
  isLoadingLatest?: boolean
  loadOlder?: () => Promise<boolean>
  loadingOlder?: boolean
  peerUser: LocalUser | undefined
}

function isNearBottom(container: HTMLElement) {
  return (
    container.scrollHeight - container.scrollTop - container.clientHeight < 120
  )
}

export function MessageList({
  conversationLocalId,
  hasMoreBefore = false,
  isLoadingLatest = false,
  loadOlder,
  loadingOlder = false,
  peerUser,
}: MessageListProps) {
  const currentUser = useAuthStore((state) => state.currentUser)
  const messages = useMessages(conversationLocalId)
  const containerRef = useRef<HTMLElement | null>(null)
  const previousConversationLocalIdRef = useRef<string | undefined>(undefined)
  const previousLastMessageLocalIdRef = useRef<string | undefined>(undefined)
  const didInitialScrollRef = useRef(false)
  const wasNearBottomRef = useRef(true)
  const pendingPrependScrollRef = useRef<{
    previousScrollHeight: number
    previousScrollTop: number
  } | null>(null)

  async function handleScroll() {
    const container = containerRef.current

    if (!container) {
      return
    }

    wasNearBottomRef.current = isNearBottom(container)

    if (
      container.scrollTop >= 120 ||
      loadingOlder ||
      !hasMoreBefore ||
      !loadOlder ||
      pendingPrependScrollRef.current
    ) {
      return
    }

    pendingPrependScrollRef.current = {
      previousScrollHeight: container.scrollHeight,
      previousScrollTop: container.scrollTop,
    }

    const didLoad = await loadOlder()

    if (!didLoad) {
      pendingPrependScrollRef.current = null
    }
  }

  useLayoutEffect(() => {
    if (previousConversationLocalIdRef.current !== conversationLocalId) {
      previousConversationLocalIdRef.current = conversationLocalId
      previousLastMessageLocalIdRef.current = undefined
      didInitialScrollRef.current = false
      wasNearBottomRef.current = true
      pendingPrependScrollRef.current = null
    }
  }, [conversationLocalId])

  useLayoutEffect(() => {
    const container = containerRef.current

    if (!container || messages.length === 0) {
      return
    }

    const pendingPrependScroll = pendingPrependScrollRef.current

    if (pendingPrependScroll) {
      const newScrollHeight = container.scrollHeight
      container.scrollTop =
        newScrollHeight -
        pendingPrependScroll.previousScrollHeight +
        pendingPrependScroll.previousScrollTop
      pendingPrependScrollRef.current = null
      return
    }

    const lastMessage = messages[messages.length - 1]
    const previousLastMessageLocalId = previousLastMessageLocalIdRef.current
    const lastMessageChanged =
      previousLastMessageLocalId !== undefined &&
      previousLastMessageLocalId !== lastMessage.localId
    const isOwnNewMessage = lastMessage.senderId === currentUser?.id

    if (
      !didInitialScrollRef.current ||
      isOwnNewMessage ||
      (lastMessageChanged && wasNearBottomRef.current)
    ) {
      container.scrollTop = container.scrollHeight
      didInitialScrollRef.current = true
    }

    previousLastMessageLocalIdRef.current = lastMessage.localId
  }, [currentUser?.id, messages])

  if (!conversationLocalId || messages.length === 0) {
    return (
      <section
        className="flex min-h-0 flex-1 items-center justify-center overflow-y-auto px-6 text-center"
        ref={containerRef}
      >
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
    <section
      className="min-h-0 flex-1 overflow-y-auto px-5 py-4"
      onScroll={() => void handleScroll()}
      ref={containerRef}
    >
      {loadingOlder ? (
        <div className="pb-3 text-center text-xs text-zinc-500">
          Loading older messages...
        </div>
      ) : null}
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
