import { create } from 'zustand'

type SearchMode = 'conversations' | 'people'

type ShellState = {
  searchKeyword: string
  searchMode: SearchMode
  selectedConversationLocalId: null | string
  setSearchKeyword: (searchKeyword: string) => void
  setSearchMode: (searchMode: SearchMode) => void
  setSelectedConversationLocalId: (
    selectedConversationLocalId: null | string,
  ) => void
}

export const useShellStore = create<ShellState>((set) => ({
  searchKeyword: '',
  searchMode: 'conversations',
  selectedConversationLocalId: null,
  setSearchKeyword: (searchKeyword) => set({ searchKeyword }),
  setSearchMode: (searchMode) => set({ searchMode }),
  setSelectedConversationLocalId: (selectedConversationLocalId) =>
    set({ selectedConversationLocalId }),
}))
