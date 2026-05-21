import { useEffect, useRef, type ChangeEvent, type FormEvent } from 'react'
import { SendHorizonal } from 'lucide-react'

import { typingService } from '@/features/typing/typingService'

import { useComposerDraftStore } from './composerDraftStore'
import { useSendMessage } from './useSendMessage'

const TYPING_START_THROTTLE_MS = 2_000
const TYPING_STOP_IDLE_MS = 1_000

export function MessageComposer({
  composerKey,
  serverConversationId,
}: {
  composerKey: string
  serverConversationId?: null | string
}) {
  const body = useComposerDraftStore(
    (state) => state.drafts[composerKey] ?? '',
  )
  const clearDraft = useComposerDraftStore((state) => state.clearDraft)
  const setDraft = useComposerDraftStore((state) => state.setDraft)
  const { isSending, sendMessage } = useSendMessage()
  const lastTypingStartAtRef = useRef(0)
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)
  const typingStopTimeoutRef = useRef<number | null>(null)
  const trimmedBody = body.trim()

  function clearTypingStopTimeout() {
    if (typingStopTimeoutRef.current === null) {
      return
    }

    window.clearTimeout(typingStopTimeoutRef.current)
    typingStopTimeoutRef.current = null
  }

  function scheduleTypingStop() {
    if (!serverConversationId) {
      return
    }

    clearTypingStopTimeout()

    typingStopTimeoutRef.current = window.setTimeout(() => {
      typingStopTimeoutRef.current = null
      sendTypingStop()
    }, TYPING_STOP_IDLE_MS)
  }

  function sendTypingStart() {
    if (!serverConversationId) {
      return
    }

    const now = Date.now()

    if (now - lastTypingStartAtRef.current < TYPING_START_THROTTLE_MS) {
      return
    }

    lastTypingStartAtRef.current = now
    typingService.start(serverConversationId)
  }

  function sendTypingStop() {
    if (!serverConversationId) {
      return
    }

    clearTypingStopTimeout()
    lastTypingStartAtRef.current = 0
    typingService.stop(serverConversationId)
  }

  useEffect(() => {
    return () => {
      if (typingStopTimeoutRef.current !== null) {
        window.clearTimeout(typingStopTimeoutRef.current)
        typingStopTimeoutRef.current = null
      }

      lastTypingStartAtRef.current = 0

      if (serverConversationId) {
        typingService.stop(serverConversationId)
      }
    }
  }, [serverConversationId])

  function handleChange(event: ChangeEvent<HTMLTextAreaElement>) {
    const nextBody = event.target.value

    setDraft(composerKey, nextBody)

    if (nextBody.trim()) {
      sendTypingStart()
      scheduleTypingStop()
    } else {
      sendTypingStop()
    }
  }

  function handleBlur() {
    sendTypingStop()
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const didInsert = await sendMessage(body)

    if (didInsert) {
      clearDraft(composerKey)
      sendTypingStop()
      requestAnimationFrame(() => textareaRef.current?.focus())
    }
  }

  return (
    <form className="flex items-end gap-3" onSubmit={handleSubmit}>
      <textarea
        className="max-h-32 min-h-11 flex-1 resize-none rounded-md border border-zinc-200 bg-white px-4 py-3 text-sm text-zinc-950 outline-none transition-colors placeholder:text-zinc-400 focus:border-zinc-400 focus:ring-2 focus:ring-zinc-200"
        disabled={isSending}
        onBlur={handleBlur}
        onChange={handleChange}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault()
            event.currentTarget.form?.requestSubmit()
          }
        }}
        placeholder="Write a message"
        ref={textareaRef}
        rows={1}
        value={body}
      />
      <button
        aria-label="Send message"
        className="inline-flex size-11 shrink-0 items-center justify-center rounded-full text-[#005ae0] transition-colors hover:bg-blue-50 hover:text-[#004bb8] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#005ae0] disabled:cursor-not-allowed disabled:text-zinc-300 disabled:hover:bg-transparent"
        disabled={!trimmedBody || isSending}
        type="submit"
      >
        <SendHorizonal aria-hidden="true" className="size-5" />
      </button>
    </form>
  )
}
