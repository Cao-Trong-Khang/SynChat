import { Link } from 'react-router-dom'

import { RegisterForm } from '@/features/auth/RegisterForm'

export function RegisterPage() {
  return (
    <section className="w-full max-w-sm space-y-6">
      <div className="space-y-2 text-center">
        <h1 className="text-3xl font-semibold text-zinc-950">Create account</h1>
        <p className="text-sm text-zinc-500">Start using SynChat Web</p>
      </div>

      <RegisterForm />

      <p className="text-center text-sm text-zinc-500">
        Already have an account?{' '}
        <Link className="font-medium text-zinc-950 hover:underline" to="/login">
          Sign in
        </Link>
      </p>
    </section>
  )
}
