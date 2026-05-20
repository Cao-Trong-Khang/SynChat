import type { LocalMessage } from '@/db/models'

import type { MessageGroupModel, MessageRenderItem } from './messageTypes'

const TIME_SEPARATOR_GAP_MS = 30 * 60 * 1000
const MESSAGE_GROUP_GAP_MS = 5 * 60 * 1000

function timestamp(message: LocalMessage) {
  return new Date(message.createdAt).getTime()
}

function hasTimeSeparator(previous: LocalMessage, current: LocalMessage) {
  return timestamp(current) - timestamp(previous) > TIME_SEPARATOR_GAP_MS
}

function canGroup(previous: LocalMessage, current: LocalMessage) {
  return (
    previous.senderId === current.senderId &&
    !hasTimeSeparator(previous, current) &&
    timestamp(current) - timestamp(previous) <= MESSAGE_GROUP_GAP_MS
  )
}

function createGroup(message: LocalMessage): MessageGroupModel {
  return {
    id: message.localId,
    messages: [message],
    senderId: message.senderId,
  }
}

export function groupMessages(messages: LocalMessage[]): MessageRenderItem[] {
  const renderItems: MessageRenderItem[] = []
  let currentGroup: MessageGroupModel | null = null

  messages.forEach((message, index) => {
    const previousMessage = messages[index - 1]
    const shouldAddSeparator =
      index === 0 ||
      (previousMessage ? hasTimeSeparator(previousMessage, message) : false)

    if (shouldAddSeparator) {
      currentGroup = null
      renderItems.push({
        id: `time:${message.localId}`,
        message,
        type: 'time-separator',
      })
    }

    if (
      currentGroup &&
      previousMessage &&
      canGroup(previousMessage, message)
    ) {
      currentGroup.messages.push(message)
      return
    }

    currentGroup = createGroup(message)
    renderItems.push({
      group: currentGroup,
      id: `group:${currentGroup.id}`,
      type: 'group',
    })
  })

  return renderItems
}
