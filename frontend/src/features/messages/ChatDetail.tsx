import { observeConversation } from '@/db/repositories/conversationsRepo'
import { observeUser } from '@/db/repositories/usersRepo'

import { ChatHeader } from './ChatHeader'

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

  return <ChatDetailContent peerUser={peerUser} />
}

function DraftChatDetail({ peerUserId }: { peerUserId: string }) {
  const peerUser = observeUser(peerUserId)

  return <ChatDetailContent peerUser={peerUser} />
}

function ChatDetailContent({
  peerUser,
}: {
  peerUser: ReturnType<typeof observeUser>
}) {
  return (
    <div className="flex h-full min-w-0 flex-1 flex-col bg-zinc-950">
      <ChatHeader peerUser={peerUser} />

      <section className="flex min-h-0 flex-1 items-center justify-center px-6 text-center">
        <p className="text-sm text-zinc-500">No messages yet</p>
      </section>

      <footer className="border-t border-zinc-800 px-5 py-4">
        <div className="rounded-md border border-zinc-800 bg-zinc-900/70 px-4 py-3 text-sm text-zinc-500">
          Message composer will be available in Phase 3
        </div>
      </footer>
    </div>
  )
}
