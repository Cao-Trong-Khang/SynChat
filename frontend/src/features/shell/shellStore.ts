import { create } from 'zustand'

export type SelectedChat =
  | { kind: 'conversation'; conversationLocalId: string }
  | { kind: 'draft'; peerUserId: string }
  | { kind: 'none' }

type ShellState = {
  searchKeyword: string
  searchMode: boolean
  selectedChat: SelectedChat
  reset: () => void
  selectConversation: (conversationLocalId: string) => void
  selectDraft: (peerUserId: string) => void
  selectNone: () => void
  setSearchKeyword: (searchKeyword: string) => void
  setSearchMode: (searchMode: boolean) => void
}

export const useShellStore = create<ShellState>((set) => ({
  searchKeyword: '',
  searchMode: false,
  selectedChat: { kind: 'none' },
  reset: () =>
    set({
      searchKeyword: '',
      searchMode: false,
      selectedChat: { kind: 'none' },
    }),
  selectConversation: (conversationLocalId) =>
    set({ selectedChat: { conversationLocalId, kind: 'conversation' } }),
  selectDraft: (peerUserId) =>
    set({ selectedChat: { kind: 'draft', peerUserId } }),
  selectNone: () => set({ selectedChat: { kind: 'none' } }),
  setSearchKeyword: (searchKeyword) => set({ searchKeyword }),
  setSearchMode: (searchMode) => set({ searchMode }),
}))
