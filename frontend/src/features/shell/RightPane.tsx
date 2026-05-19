import { ChatDetail } from '@/features/messages/ChatDetail'

import { LandingView } from './LandingView'
import { useShellStore } from './shellStore'

export function RightPane() {
  const selectedChat = useShellStore((state) => state.selectedChat)

  if (selectedChat.kind === 'conversation') {
    return (
      <section className="min-w-0 flex-1 bg-zinc-950">
        <ChatDetail conversationLocalId={selectedChat.conversationLocalId} />
      </section>
    )
  }

  if (selectedChat.kind === 'draft') {
    return (
      <section className="min-w-0 flex-1 bg-zinc-950">
        <ChatDetail peerUserId={selectedChat.peerUserId} />
      </section>
    )
  }

  return (
    <section className="min-w-0 flex-1 bg-zinc-950">
      <LandingView />
    </section>
  )
}
