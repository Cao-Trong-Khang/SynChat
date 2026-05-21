import { create } from 'zustand'

const EMPTY_TYPING_USERS: string[] = []

type TypingStore = {
  byConversationId: Record<string, string[]>
  clearConversation: (conversationId: string) => void
  clearTyping: (conversationId: string, userId: string) => void
  getTypingUsers: (conversationId: string) => string[]
  setTyping: (conversationId: string, userId: string) => void
}

export const useTypingStore = create<TypingStore>((set, get) => ({
  byConversationId: {},
  clearConversation: (conversationId) =>
    set((state) => {
      if (!state.byConversationId[conversationId]) {
        return state
      }

      const byConversationId = { ...state.byConversationId }
      delete byConversationId[conversationId]

      return { byConversationId }
    }),
  clearTyping: (conversationId, userId) =>
    set((state) => {
      const typingUsers = state.byConversationId[conversationId]

      if (!typingUsers?.includes(userId)) {
        return state
      }

      const nextTypingUsers = typingUsers.filter((id) => id !== userId)
      const byConversationId = { ...state.byConversationId }

      if (nextTypingUsers.length > 0) {
        byConversationId[conversationId] = nextTypingUsers
      } else {
        delete byConversationId[conversationId]
      }

      return { byConversationId }
    }),
  getTypingUsers: (conversationId) =>
    get().byConversationId[conversationId] ?? EMPTY_TYPING_USERS,
  setTyping: (conversationId, userId) =>
    set((state) => {
      const typingUsers = state.byConversationId[conversationId] ?? []

      if (typingUsers.includes(userId)) {
        return state
      }

      return {
        byConversationId: {
          ...state.byConversationId,
          [conversationId]: [...typingUsers, userId],
        },
      }
    }),
}))
