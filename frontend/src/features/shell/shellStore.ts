import { create } from 'zustand'

type ShellState = {
  searchKeyword: string
  searchMode: boolean
  selectedConversationLocalId: null | string
  setSearchKeyword: (searchKeyword: string) => void
  setSearchMode: (searchMode: boolean) => void
  setSelectedConversationLocalId: (
    selectedConversationLocalId: null | string,
  ) => void
}

export const useShellStore = create<ShellState>((set) => ({
  searchKeyword: '',
  searchMode: false,
  selectedConversationLocalId: null,
  setSearchKeyword: (searchKeyword) => set({ searchKeyword }),
  setSearchMode: (searchMode) => set({ searchMode }),
  setSelectedConversationLocalId: (selectedConversationLocalId) =>
    set({ selectedConversationLocalId }),
}))
