import { Link } from 'react-router-dom'

import { LoginForm } from '@/features/auth/LoginForm'

export function LoginPage() {
  return (
    <section className="w-full max-w-sm space-y-6">
      <div className="space-y-2 text-center">
        <h1 className="text-3xl font-semibold text-zinc-950">SynChat Web</h1>
        <p className="text-sm text-zinc-500">Sign in to continue</p>
      </div>

      <LoginForm />

      <p className="text-center text-sm text-zinc-500">
        New here?{' '}
        <Link className="font-medium text-zinc-950 hover:underline" to="/register">
          Create an account
        </Link>
      </p>
    </section>
  )
}
