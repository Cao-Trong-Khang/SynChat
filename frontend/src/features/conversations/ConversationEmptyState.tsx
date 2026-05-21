import { Search } from 'lucide-react'

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
      <div className="flex flex-col items-center">
        <div className="mb-4 flex size-12 items-center justify-center rounded-full bg-blue-50 text-[#005ae0]">
          <Search aria-hidden="true" className="size-5" />
        </div>
        <p className="text-sm font-medium text-zinc-900">{title}</p>
        <p className="mt-2 text-sm text-zinc-500">{description}</p>
      </div>
    </div>
  )
}
