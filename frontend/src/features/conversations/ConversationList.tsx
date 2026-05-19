import { ConversationEmptyState } from './ConversationEmptyState'
import { ConversationListItem } from './ConversationListItem'
import { useConversationList } from './useConversationList'

export function ConversationList() {
  const conversations = useConversationList()

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
    <section className="min-h-0 flex-1 overflow-y-auto py-2">
      {conversations.map((conversation) => (
        <ConversationListItem
          conversation={conversation}
          key={conversation.localId}
        />
      ))}
    </section>
  )
}
