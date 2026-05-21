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
  peerDeliveredSeq?: number
  peerReadSeq?: number
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
  peerDeliveredSeq = 0,
  peerReadSeq = 0,
  peerUser,
}: MessageListProps) {
  const currentUser = useAuthStore((state) => state.currentUser)
  const messages = useMessages(conversationLocalId)
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const previousConversationLocalIdRef = useRef<string | undefined>(undefined)
  const previousMessageCountRef = useRef(0)
  const previousLastMessageLocalIdRef = useRef<string | undefined>(undefined)
  const previousIsLoadingLatestRef = useRef(false)
  const didInitialScrollRef = useRef(false)
  const wasNearBottomRef = useRef(true)
  const pendingPrependScrollRef = useRef<{
    previousScrollHeight: number
    previousScrollTop: number
  } | null>(null)

  function scrollToBottom(behavior: ScrollBehavior = 'auto') {
    const container = scrollRef.current

    if (!container) {
      return
    }

    if (behavior === 'smooth') {
      container.scrollTo({ behavior, top: container.scrollHeight })
      return
    }

    container.scrollTop = container.scrollHeight
  }

  async function handleScroll() {
    const container = scrollRef.current

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
      previousMessageCountRef.current = 0
      previousLastMessageLocalIdRef.current = undefined
      didInitialScrollRef.current = false
      wasNearBottomRef.current = true
      pendingPrependScrollRef.current = null
    }
  }, [conversationLocalId])

  useLayoutEffect(() => {
    const container = scrollRef.current

    if (!container || messages.length === 0) {
      previousIsLoadingLatestRef.current = isLoadingLatest
      previousMessageCountRef.current = messages.length
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
      previousMessageCountRef.current = messages.length
      previousLastMessageLocalIdRef.current =
        messages[messages.length - 1]?.localId
      previousIsLoadingLatestRef.current = isLoadingLatest
      return
    }

    const lastMessage = messages[messages.length - 1]
    const previousMessageCount = previousMessageCountRef.current
    const previousLastMessageLocalId = previousLastMessageLocalIdRef.current
    const lastMessageChanged =
      previousLastMessageLocalId !== undefined &&
      previousLastMessageLocalId !== lastMessage.localId
    const wasEmptyAndNowHasMessages =
      previousMessageCount === 0 && messages.length > 0
    const latestHydrationCompleted =
      previousIsLoadingLatestRef.current && !isLoadingLatest
    const isOwnNewMessage =
      lastMessageChanged && lastMessage.senderId === currentUser?.id

    if (
      !didInitialScrollRef.current ||
      wasEmptyAndNowHasMessages ||
      latestHydrationCompleted ||
      isOwnNewMessage ||
      (lastMessageChanged && wasNearBottomRef.current)
    ) {
      requestAnimationFrame(() => scrollToBottom('auto'))
      didInitialScrollRef.current = true
    }

    previousMessageCountRef.current = messages.length
    previousLastMessageLocalIdRef.current = lastMessage.localId
    previousIsLoadingLatestRef.current = isLoadingLatest
  }, [currentUser?.id, isLoadingLatest, messages])

  if (!conversationLocalId || messages.length === 0) {
    return (
      <div
        className="scrollbar-hidden flex min-h-0 flex-1 items-center justify-center overflow-y-auto px-6 text-center"
        ref={scrollRef}
      >
        {isLoadingLatest ? (
          <p className="text-sm text-zinc-500">Loading messages...</p>
        ) : (
          <div className="flex flex-col items-center">
            <div className="mb-4 flex size-14 items-center justify-center rounded-full bg-blue-50 text-3xl text-[#005ae0]">
              <span aria-hidden="true" className="animate-wave-hand origin-[70%_70%]">
                👋
              </span>
            </div>
            <p className="text-sm font-medium text-zinc-900">
              Say hello to each other
            </p>
          </div>
        )}
      </div>
    )
  }

  const lastOwnMessageLocalId = [...messages]
    .reverse()
    .find((message) => message.senderId === currentUser?.id)?.localId
  const renderItems = groupMessages(messages)

  return (
    <div
      className="scrollbar-hidden min-h-0 flex-1 overflow-y-auto px-5 py-4"
      onScroll={() => void handleScroll()}
      ref={scrollRef}
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
              peerDeliveredSeq={peerDeliveredSeq}
              peerReadSeq={peerReadSeq}
              peerUser={peerUser}
            />
          ),
        )}
      </div>
    </div>
  )
}
