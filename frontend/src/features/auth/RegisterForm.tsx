import { useState, type SubmitEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'

import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'
import { getErrorMessage } from '@/shared/api/apiError'

import { authService } from './authService'

export function RegisterForm() {
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const registerMutation = useMutation({
    mutationFn: authService.register,
    onSuccess: () => navigate('/login'),
  })

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    registerMutation.mutate({ displayName, email, password })
  }

  return (
    <form className="space-y-3" onSubmit={handleSubmit}>
      <Input
        aria-label="Display name"
        autoComplete="name"
        disabled={registerMutation.isPending}
        maxLength={120}
        onChange={(event) => setDisplayName(event.target.value)}
        placeholder="Display name"
        required
        value={displayName}
      />
      <Input
        aria-label="Email"
        autoComplete="email"
        disabled={registerMutation.isPending}
        onChange={(event) => setEmail(event.target.value)}
        placeholder="Email"
        required
        type="email"
        value={email}
      />
      <Input
        aria-label="Password"
        autoComplete="new-password"
        disabled={registerMutation.isPending}
        minLength={8}
        onChange={(event) => setPassword(event.target.value)}
        placeholder="Password"
        required
        type="password"
        value={password}
      />

      {registerMutation.isError ? (
        <p className="text-sm text-red-600">
          {getErrorMessage(registerMutation.error)}
        </p>
      ) : null}

      <Button className="w-full" disabled={registerMutation.isPending} type="submit">
        {registerMutation.isPending ? 'Creating account...' : 'Register'}
      </Button>
    </form>
  )
}
