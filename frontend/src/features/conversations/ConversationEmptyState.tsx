type ConversationEmptyStateProps = {
  description?: string
  title?: string
}

export function ConversationEmptyState({
  description = 'Search for someone to start chatting.',
  title = 'No conversations yet',
}: ConversationEmptyStateProps) {
  return (
    <div className="flex flex-1 items-center justify-center px-6 text-center">
      <div className="space-y-2">
        <p className="text-sm font-medium text-zinc-200">{title}</p>
        <p className="text-sm text-zinc-500">{description}</p>
      </div>
    </div>
  )
}
