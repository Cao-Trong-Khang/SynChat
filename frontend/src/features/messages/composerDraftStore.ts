import { create } from 'zustand'

type ComposerDraftState = {
  drafts: Record<string, string>
  clearDraft: (key: string) => void
  getDraft: (key: string) => string
  setDraft: (key: string, text: string) => void
}

export const useComposerDraftStore = create<ComposerDraftState>((set, get) => ({
  drafts: {},
  clearDraft: (key) =>
    set((state) => {
      const drafts = { ...state.drafts }
      delete drafts[key]

      return { drafts }
    }),
  getDraft: (key) => get().drafts[key] ?? '',
  setDraft: (key, text) =>
    set((state) => ({
      drafts: {
        ...state.drafts,
        [key]: text,
      },
    })),
}))
