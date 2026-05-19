import { ConversationList } from '@/features/conversations/ConversationList'

import { LeftHeader } from './LeftHeader'

export function LeftPane() {
  return (
    <aside className="flex h-svh w-[360px] shrink-0 flex-col border-r border-zinc-800 bg-zinc-950">
      <LeftHeader />
      <ConversationList />
    </aside>
  )
}
