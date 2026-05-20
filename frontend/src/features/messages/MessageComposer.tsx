import { useState, type FormEvent } from 'react'
import { SendHorizonal } from 'lucide-react'

import { Button } from '@/shared/components/Button'

import { useSendMessage } from './useSendMessage'

export function MessageComposer() {
  const [body, setBody] = useState('')
  const { isSending, sendMessage } = useSendMessage()
  const trimmedBody = body.trim()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const didInsert = await sendMessage(body)

    if (didInsert) {
      setBody('')
    }
  }

  return (
    <form className="flex items-end gap-3" onSubmit={handleSubmit}>
      <textarea
        className="max-h-32 min-h-11 flex-1 resize-none rounded-md border border-zinc-800 bg-zinc-900/70 px-4 py-3 text-sm text-zinc-100 outline-none transition-colors placeholder:text-zinc-500 focus:border-zinc-600 focus:ring-2 focus:ring-zinc-800"
        disabled={isSending}
        onChange={(event) => setBody(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault()
            event.currentTarget.form?.requestSubmit()
          }
        }}
        placeholder="Write a message"
        rows={1}
        value={body}
      />
      <Button
        aria-label="Send message"
        className="size-11 shrink-0 px-0"
        disabled={!trimmedBody || isSending}
        type="submit"
      >
        <SendHorizonal aria-hidden="true" className="size-4" />
      </Button>
    </form>
  )
}
