import type { LocalUser } from '@/db/models'

import { MessageBubble } from './MessageBubble'
import { MessageStatusView } from './MessageStatusView'
import type { MessageGroupModel } from './messageTypes'

type MessageGroupProps = {
  currentUserId: string | undefined
  group: MessageGroupModel
  lastOwnMessageLocalId: string | undefined
  peerUser: LocalUser | undefined
}

export function MessageGroup({
  currentUserId,
  group,
  lastOwnMessageLocalId,
  peerUser,
}: MessageGroupProps) {
  const isMine = group.senderId === currentUserId

  return (
    <div className="space-y-1">
      {group.messages.map((message, index) => {
        const isLastInGroup = index === group.messages.length - 1
        const isLastOwnMessage = message.localId === lastOwnMessageLocalId

        return (
          <div key={message.localId}>
            <MessageBubble
              isMine={isMine}
              message={message}
              peerUser={peerUser}
              showAvatar={!isMine && index === 0}
              showTime={isLastInGroup}
            />
            {isMine ? (
              <MessageStatusView
                isLastOwnMessage={isLastOwnMessage}
                message={message}
              />
            ) : null}
          </div>
        )
      })}
    </div>
  )
}
