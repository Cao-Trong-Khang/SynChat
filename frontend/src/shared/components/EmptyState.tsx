import type { ReactNode } from 'react'

type EmptyStateProps = {
  action?: ReactNode
  description?: string
  title: string
}

export function EmptyState({ action, description, title }: EmptyStateProps) {
  return (
    <section className="flex flex-1 items-center justify-center px-6 text-center">
      <div className="max-w-md space-y-3">
        <h1 className="text-3xl font-semibold tracking-normal">{title}</h1>
        {description ? <p className="text-sm text-zinc-500">{description}</p> : null}
        {action ? <div className="pt-2">{action}</div> : null}
      </div>
    </section>
  )
}
