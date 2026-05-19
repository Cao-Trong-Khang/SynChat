import { useState, type SubmitEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'

import { Button } from '@/shared/components/Button'
import { Input } from '@/shared/components/Input'
import { getErrorMessage } from '@/shared/api/apiError'

import { authService } from './authService'

export function LoginForm() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const loginMutation = useMutation({
    mutationFn: authService.login,
    onSuccess: () => navigate('/chat'),
  })

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    loginMutation.mutate({ email, password })
  }

  return (
    <form className="space-y-3" onSubmit={handleSubmit}>
      <Input
        aria-label="Email"
        autoComplete="email"
        disabled={loginMutation.isPending}
        onChange={(event) => setEmail(event.target.value)}
        placeholder="Email"
        required
        type="email"
        value={email}
      />
      <Input
        aria-label="Password"
        autoComplete="current-password"
        disabled={loginMutation.isPending}
        minLength={8}
        onChange={(event) => setPassword(event.target.value)}
        placeholder="Password"
        required
        type="password"
        value={password}
      />

      {loginMutation.isError ? (
        <p className="text-sm text-red-600">{getErrorMessage(loginMutation.error)}</p>
      ) : null}

      <Button className="w-full" disabled={loginMutation.isPending} type="submit">
        {loginMutation.isPending ? 'Signing in...' : 'Sign in'}
      </Button>
    </form>
  )
}
