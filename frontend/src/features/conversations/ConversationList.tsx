import { ConversationEmptyState } from './ConversationEmptyState'

export function ConversationList() {
  return (
    <section className="flex min-h-0 flex-1 flex-col">
      <ConversationEmptyState />
    </section>
  )
}
