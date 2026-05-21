import { useEffect } from 'react'

import { observeConversation } from '@/db/repositories/conversationsRepo'
import { getNewestSeqByConversationLocalId } from '@/db/repositories/messagesRepo'
import { observeUser } from '@/db/repositories/usersRepo'
import { receiptService } from '@/features/receipts/receiptService'
import { typingService } from '@/features/typing/typingService'
import { useTypingStore } from '@/features/typing/typingStore'
import { TypingIndicator } from '@/features/typing/TypingIndicator'

import { ChatHeader } from './ChatHeader'
import { MessageComposer } from './MessageComposer'
import { MessageList } from './MessageList'
import { useMessagePagination } from './useMessagePagination'

type ChatDetailProps =
  | {
      conversationLocalId: string
      peerUserId?: never
    }
  | {
      conversationLocalId?: never
      peerUserId: string
    }

export function ChatDetail(props: ChatDetailProps) {
  if (props.conversationLocalId !== undefined) {
    return (
      <ExistingConversationDetail
        conversationLocalId={props.conversationLocalId}
      />
    )
  }

  return <DraftChatDetail peerUserId={props.peerUserId} />
}

function ExistingConversationDetail({
  conversationLocalId,
}: {
  conversationLocalId: string
}) {
  const conversation = observeConversation(conversationLocalId)
  const peerUser = observeUser(conversation?.peerUserId ?? '')
  const { hasMoreBefore, isLoadingLatest, loadOlder, loadingOlder } =
    useMessagePagination(
      conversationLocalId,
      conversation?.serverId,
    )
  const serverConversationId = conversation?.serverId ?? null
  const lastMessageSeq = conversation?.lastMessageSeq ?? null
  const peerUserId = conversation?.peerUserId
  const unreadCount = conversation?.unreadCount ?? 0

  useEffect(() => {
    if (!serverConversationId) {
      return
    }

    typingService.subscribe(serverConversationId)

    return () => {
      typingService.unsubscribe(serverConversationId)
      useTypingStore.getState().clearConversation(serverConversationId)
    }
  }, [serverConversationId])

  useEffect(() => {
    if (!serverConversationId || unreadCount <= 0) {
      return
    }

    let cancelled = false

    async function markOpenConversationRead() {
      const newestPeerSeq = peerUserId
        ? await getNewestSeqByConversationLocalId(conversationLocalId, peerUserId)
        : null
      const newestSeq =
        newestPeerSeq ??
        lastMessageSeq ??
        (await getNewestSeqByConversationLocalId(conversationLocalId))

      if (cancelled) {
        return
      }

      await receiptService.markReadUpTo(conversationLocalId, newestSeq)
    }

    void markOpenConversationRead()

    return () => {
      cancelled = true
    }
  }, [
    conversationLocalId,
    lastMessageSeq,
    peerUserId,
    serverConversationId,
    unreadCount,
  ])

  return (
    <ChatDetailContent
      composerKey={conversationLocalId}
      conversationLocalId={conversationLocalId}
      hasMoreBefore={hasMoreBefore}
      isLoadingLatest={isLoadingLatest}
      loadOlder={loadOlder}
      loadingOlder={loadingOlder}
      peerDeliveredSeq={conversation?.peerDeliveredSeq ?? 0}
      peerReadSeq={conversation?.peerReadSeq ?? 0}
      peerUser={peerUser}
      serverConversationId={serverConversationId}
    />
  )
}

function DraftChatDetail({ peerUserId }: { peerUserId: string }) {
  const peerUser = observeUser(peerUserId)

  return (
    <ChatDetailContent
      composerKey={`direct:${peerUserId}`}
      peerUser={peerUser}
    />
  )
}

function ChatDetailContent({
  composerKey,
  conversationLocalId,
  hasMoreBefore,
  isLoadingLatest,
  loadOlder,
  loadingOlder,
  peerDeliveredSeq,
  peerReadSeq,
  peerUser,
  serverConversationId,
}: {
  composerKey: string
  conversationLocalId?: string
  hasMoreBefore?: boolean
  isLoadingLatest?: boolean
  loadOlder?: () => Promise<boolean>
  loadingOlder?: boolean
  peerDeliveredSeq?: number
  peerReadSeq?: number
  peerUser: ReturnType<typeof observeUser>
  serverConversationId?: null | string
}) {
  return (
    <div className="flex h-full min-w-0 flex-1 flex-col bg-zinc-50">
      <ChatHeader peerUser={peerUser} />

      <MessageList
        conversationLocalId={conversationLocalId}
        hasMoreBefore={hasMoreBefore}
        isLoadingLatest={isLoadingLatest}
        loadOlder={loadOlder}
        loadingOlder={loadingOlder}
        peerDeliveredSeq={peerDeliveredSeq}
        peerReadSeq={peerReadSeq}
        peerUser={peerUser}
      />

      <footer className="border-t border-zinc-200 bg-white px-5 py-4">
        <TypingIndicator
          conversationId={serverConversationId}
          peerUser={peerUser}
        />
        <MessageComposer
          composerKey={composerKey}
          serverConversationId={serverConversationId}
        />
      </footer>
    </div>
  )
}
