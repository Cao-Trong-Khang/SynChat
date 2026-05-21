import type { ButtonHTMLAttributes } from 'react'
import { clsx } from 'clsx'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement>

export function Button({ className, type = 'button', ...props }: ButtonProps) {
  return (
    <button
      className={clsx(
        'inline-flex h-10 items-center justify-center rounded-md bg-blue-600 px-4 text-sm font-medium text-white transition-colors hover:bg-blue-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-60',
        className,
      )}
      type={type}
      {...props}
    />
  )
}
