import { observeConversation } from '@/db/repositories/conversationsRepo'
import { observeUser } from '@/db/repositories/usersRepo'

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

  return (
    <ChatDetailContent
      conversationLocalId={conversationLocalId}
      hasMoreBefore={hasMoreBefore}
      isLoadingLatest={isLoadingLatest}
      loadOlder={loadOlder}
      loadingOlder={loadingOlder}
      peerUser={peerUser}
    />
  )
}

function DraftChatDetail({ peerUserId }: { peerUserId: string }) {
  const peerUser = observeUser(peerUserId)

  return <ChatDetailContent peerUser={peerUser} />
}

function ChatDetailContent({
  conversationLocalId,
  hasMoreBefore,
  isLoadingLatest,
  loadOlder,
  loadingOlder,
  peerUser,
}: {
  conversationLocalId?: string
  hasMoreBefore?: boolean
  isLoadingLatest?: boolean
  loadOlder?: () => Promise<boolean>
  loadingOlder?: boolean
  peerUser: ReturnType<typeof observeUser>
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
        <MessageComposer />
      </footer>
    </div>
  )
}
