import { useEffect } from 'react'

import { observeConversation } from '@/db/repositories/conversationsRepo'
import { observeUser } from '@/db/repositories/usersRepo'
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

  return (
    <ChatDetailContent
      composerKey={conversationLocalId}
      conversationLocalId={conversationLocalId}
      hasMoreBefore={hasMoreBefore}
      isLoadingLatest={isLoadingLatest}
      loadOlder={loadOlder}
      loadingOlder={loadingOlder}
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
  peerUser,
  serverConversationId,
}: {
  composerKey: string
  conversationLocalId?: string
  hasMoreBefore?: boolean
  isLoadingLatest?: boolean
  loadOlder?: () => Promise<boolean>
  loadingOlder?: boolean
  peerUser: ReturnType<typeof observeUser>
  serverConversationId?: null | string
}) {
  return (
    <div className="flex h-full min-w-0 flex-1 flex-col bg-zinc-950">
      <ChatHeader peerUser={peerUser} />

      <MessageList
        conversationLocalId={conversationLocalId}
        hasMoreBefore={hasMoreBefore}
        isLoadingLatest={isLoadingLatest}
        loadOlder={loadOlder}
        loadingOlder={loadingOlder}
        peerUser={peerUser}
      />

      <footer className="border-t border-zinc-800 px-5 py-4">
        <TypingIndicator conversationId={serverConversationId} />
        <MessageComposer
          composerKey={composerKey}
          serverConversationId={serverConversationId}
        />
      </footer>
    </div>
  )
}
