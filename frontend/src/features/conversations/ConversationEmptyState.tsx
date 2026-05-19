export function ConversationEmptyState() {
  return (
    <div className="flex flex-1 items-center justify-center px-6 text-center">
      <div className="space-y-2">
        <p className="text-sm font-medium text-zinc-200">No conversations yet</p>
        <p className="text-sm text-zinc-500">
          Search for someone to start chatting.
        </p>
      </div>
    </div>
  )
}
