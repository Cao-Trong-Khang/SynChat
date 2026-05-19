import { LandingView } from './LandingView'
import { useShellStore } from './shellStore'

export function RightPane() {
  const selectedConversationLocalId = useShellStore(
    (state) => state.selectedConversationLocalId,
  )

  return (
    <section className="min-w-0 flex-1 bg-zinc-950">
      {selectedConversationLocalId ? null : <LandingView />}
    </section>
  )
}
