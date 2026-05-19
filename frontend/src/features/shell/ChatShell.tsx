import { LeftPane } from './LeftPane'
import { RightPane } from './RightPane'

export function ChatShell() {
  return (
    <div className="flex h-svh w-full overflow-hidden bg-zinc-950 text-zinc-50">
      <LeftPane />
      <RightPane />
    </div>
  )
}
