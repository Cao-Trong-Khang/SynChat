import { useEffect, useMemo, useRef, type UIEvent } from 'react'

import { presenceService } from '@/features/presence/presenceService'

import { ConversationEmptyState } from './ConversationEmptyState'
import { ConversationListItem } from './ConversationListItem'
import { useConversationList } from './useConversationList'

export function ConversationList() {
  const { conversations, loadingMore, loadMoreConversations } =
    useConversationList()
  const isLoadingMoreRef = useRef(false)
  const peerUserIds = useMemo(
    () => conversations?.map((conversation) => conversation.peerUserId) ?? [],
    [conversations],
  )
  const peerUserIdsKey = peerUserIds.join('\n')

  useEffect(() => {
    presenceService.subscribe(peerUserIds)

    return () => {
      presenceService.unsubscribe(peerUserIds)
    }
  }, [peerUserIds, peerUserIdsKey])

  async function handleScroll(event: UIEvent<HTMLElement>) {
    const container = event.currentTarget
    const distanceFromBottom =
      container.scrollHeight - container.scrollTop - container.clientHeight

    if (distanceFromBottom >= 160 || isLoadingMoreRef.current || loadingMore) {
      return
    }

    isLoadingMoreRef.current = true

    try {
      await loadMoreConversations()
    } finally {
      isLoadingMoreRef.current = false
    }
  }

  if (conversations === undefined) {
    return (
      <section className="flex min-h-0 flex-1 flex-col">
        <div className="flex flex-1 items-center justify-center px-6 text-sm text-zinc-500">
          Loading conversations...
        </div>
      </section>
    )
  }

  if (conversations.length === 0) {
    return (
      <section className="flex min-h-0 flex-1 flex-col">
        <ConversationEmptyState
          description="Search for someone to start chatting."
          title="No conversations yet"
        />
      </section>
    )
  }

  return (
    <section
      className="scrollbar-hidden min-h-0 flex-1 overflow-y-auto py-2"
      onScroll={(event) => void handleScroll(event)}
    >
      {conversations.map((conversation) => (
        <ConversationListItem
          conversation={conversation}
          key={conversation.localId}
        />
      ))}
      {loadingMore ? (
        <div className="py-3 text-center text-xs text-zinc-500">
          Loading more conversations...
        </div>
      ) : null}
    </section>
  )
}
