import { useState } from 'react'

import { useShellStore } from '@/features/shell/shellStore'

import { messageService } from './messageService'

export function useSendMessage() {
  const selectedChat = useShellStore((state) => state.selectedChat)
  const [isSending, setIsSending] = useState(false)

  async function sendMessage(body: string) {
    setIsSending(true)

    try {
      return await messageService.sendMessage(selectedChat, body)
    } finally {
      setIsSending(false)
    }
  }

  return {
    isSending,
    sendMessage,
  }
}
