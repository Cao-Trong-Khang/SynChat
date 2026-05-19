import { Link } from 'react-router-dom'

import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'

export function LoginPage() {
  return (
    <section className="w-full max-w-sm space-y-6">
      <div className="space-y-2 text-center">
        <h1 className="text-3xl font-semibold text-zinc-950">SynChat Web</h1>
        <p className="text-sm text-zinc-500">Sign in placeholder</p>
      </div>

      <div className="space-y-3">
        <Input aria-label="Email" placeholder="Email" type="email" />
        <Input aria-label="Password" placeholder="Password" type="password" />
        <Button className="w-full" type="button">
          Sign in
        </Button>
      </div>

      <p className="text-center text-sm text-zinc-500">
        New here?{' '}
        <Link className="font-medium text-zinc-950 hover:underline" to="/register">
          Create an account
        </Link>
      </p>
    </section>
  )
}
