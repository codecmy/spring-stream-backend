import { useState, useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import useStore from '../store/useStore'

export default function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const checkSession = useStore((s) => s.checkSession)
  const isAuthenticated = useStore((s) => s.isAuthenticated)

  const redirectTo = useMemo(() => searchParams.get('redirect') || '/', [searchParams])
  const [isSignUp, setIsSignUp] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  if (isAuthenticated) {
    if (redirectTo !== '/') {
      window.location.href = redirectTo
    } else {
      navigate('/', { replace: true })
    }
    return null
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const endpoint = isSignUp ? '/api/v1/auth/register' : '/api/v1/auth/login'
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      })

      const data = await res.json()
      if (!res.ok) {
        setError(data.error || data.message || 'Authentication failed')
        setLoading(false)
        return
      }

      localStorage.setItem('token', data.token)
      await checkSession()

      if (redirectTo && redirectTo !== '/') {
        window.location.href = redirectTo
      } else {
        navigate('/', { replace: true })
      }
    } catch {
      setError('Connection error. Please try again.')
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-surface-900 flex flex-col items-center justify-center px-4">
      <div className="mb-8 text-center">
        <h1 className="font-display text-3xl font-bold tracking-tight text-accent">STREAMFLIX</h1>
        <p className="text-surface-200 text-sm mt-1">{isSignUp ? 'Create your account' : 'Sign in to your account'}</p>
      </div>
      <div className="w-full max-w-sm">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <input
              type="email"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full px-4 py-3 bg-surface-800 border border-surface-700 rounded-lg text-white placeholder-surface-400 focus:outline-none focus:border-accent transition-colors text-sm"
            />
          </div>
          <div>
            <input
              type="password"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={4}
              className="w-full px-4 py-3 bg-surface-800 border border-surface-700 rounded-lg text-white placeholder-surface-400 focus:outline-none focus:border-accent transition-colors text-sm"
            />
          </div>
          {error && (
            <p className="text-error text-sm text-center">{error}</p>
          )}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 bg-accent text-white font-semibold rounded-lg hover:bg-accent-hover transition-colors disabled:opacity-50 text-sm"
          >
            {loading ? 'Please wait...' : isSignUp ? 'Sign Up' : 'Sign In'}
          </button>
        </form>
        <p className="text-surface-200 text-sm text-center mt-6">
          {isSignUp ? 'Already have an account?' : "Don't have an account?"}{' '}
          <button
            onClick={() => { setIsSignUp(!isSignUp); setError('') }}
            className="text-accent hover:underline font-medium"
          >
            {isSignUp ? 'Sign In' : 'Sign Up'}
          </button>
        </p>
      </div>
    </div>
  )
}
