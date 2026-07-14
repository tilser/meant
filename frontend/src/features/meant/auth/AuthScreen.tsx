import { type FormEvent, useState } from 'react'

import type { AuthMode } from '../types'
import type { AuthActions } from './useSupabaseAuth'

const GoogleGlyph = (
  <svg width="17" height="17" viewBox="0 0 18 18" aria-hidden="true">
    <path
      d="M17.6 9.2c0-.6-.05-1.18-.16-1.74H9v3.3h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.64-3.88 2.64-6.54z"
      fill="#4285F4"
    />
    <path
      d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z"
      fill="#34A853"
    />
    <path d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z" fill="#FBBC05" />
    <path
      d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.47.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z"
      fill="#EA4335"
    />
  </svg>
)

const AppleGlyph = (
  <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" fill="currentColor">
    <path d="M11.18 8.5c-.02-1.66 1.36-2.46 1.42-2.5-.77-1.13-1.98-1.29-2.4-1.3-1.02-.1-1.99.6-2.5.6-.52 0-1.31-.59-2.16-.57-1.1.02-2.13.65-2.7 1.64-1.16 2.01-.3 4.98.83 6.6.55.8 1.21 1.69 2.07 1.66.83-.03 1.15-.54 2.15-.54 1 0 1.29.54 2.16.52.9-.01 1.46-.81 2-1.61.64-.92.9-1.82.91-1.86-.02-.01-1.75-.67-1.78-2.64zM9.6 3.62c.45-.55.76-1.31.67-2.07-.65.03-1.45.44-1.92.98-.42.48-.79 1.26-.69 2 .73.06 1.48-.37 1.94-.91z" />
  </svg>
)

export function AuthScreen({
  mode,
  onMode,
  auth,
}: Readonly<{
  mode: AuthMode
  onMode: (mode: AuthMode) => void
  auth: Omit<AuthActions, 'signOut'>
}>) {
  const signup = mode === 'signup'
  const reset = mode === 'reset'
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [sent, setSent] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const runOAuth = async (provider: 'google' | 'apple') => {
    setError(null)
    const { error: oauthError } = await auth.signInWithOAuth(provider)
    if (oauthError) {
      setError(oauthError)
    }
  }

  if (reset) {
    return (
      <div className="mt-auth">
        <AuthBrand />
        <main className="mt-auth-panel">
          <form
            className="mt-auth-card"
            onSubmit={async (event: FormEvent<HTMLFormElement>) => {
              event.preventDefault()
              if (!email.trim() || pending) {
                return
              }
              setPending(true)
              setError(null)
              const { error: resetError } = await auth.resetPassword(email.trim())
              setPending(false)
              if (resetError) {
                setError(resetError)
                return
              }
              setSent(true)
            }}
          >
            <button
              className="mt-reset-back mt-mono"
              type="button"
              onClick={() => onMode('signin')}
            >
              Back to sign in
            </button>
            <div className="mt-mono mt-auth-eyebrow">Password reset</div>
            <h2 className="mt-auth-title">Forgot your password?</h2>
            <p className="mt-auth-sub">
              Enter your email and Meant will send you a link to reset your password.
            </p>
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Email</span>
              <input
                className="mt-input"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
            {sent ? (
              <div className="mt-reset-ok mt-mono">Check your inbox for a reset link.</div>
            ) : null}
            {error ? <div className="mt-auth-error mt-mono">{error}</div> : null}
            <button className="mt-auth-primary" type="submit" disabled={!email.trim() || pending}>
              {pending ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        </main>
      </div>
    )
  }

  const canSubmit = Boolean(email.trim() && password.trim() && (!signup || name.trim())) && !pending

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!canSubmit) {
      return
    }
    setPending(true)
    setError(null)
    setNotice(null)
    if (signup) {
      const { error: signUpError, needsConfirmation } = await auth.signUp(
        email.trim(),
        password,
        name.trim(),
      )
      setPending(false)
      if (signUpError) {
        setError(signUpError)
        return
      }
      if (needsConfirmation) {
        setNotice('Account created. Check your email to confirm, then sign in.')
        onMode('signin')
      }
      // When confirmation is not required, the auth state listener flips the app into the app shell.
      return
    }
    const { error: signInError } = await auth.signInWithPassword(email.trim(), password)
    setPending(false)
    if (signInError) {
      setError(signInError)
    }
  }

  return (
    <div className="mt-auth">
      <AuthBrand />
      <main className="mt-auth-panel">
        <form className="mt-auth-card" onSubmit={submit}>
          <div className="mt-mono mt-auth-eyebrow">
            {signup ? 'Create your account' : 'Welcome back'}
          </div>
          <h2 className="mt-auth-title">
            {signup ? 'Start shopping the way you mean it.' : 'Sign in to Meant.'}
          </h2>
          <p className="mt-auth-sub">
            {signup
              ? 'Set up your profile once and Meant applies it across supported stores.'
              : 'Pick up right where you left off.'}
          </p>
          <div className="mt-auth-social">
            <button
              className="mt-social-btn"
              type="button"
              disabled={pending}
              onClick={() => runOAuth('google')}
            >
              {GoogleGlyph} Continue with Google
            </button>
            <button
              className="mt-social-btn"
              type="button"
              disabled={pending}
              onClick={() => runOAuth('apple')}
            >
              {AppleGlyph} Continue with Apple
            </button>
          </div>
          <div className="mt-auth-div">
            <span>or with email</span>
          </div>
          {signup ? (
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Full name</span>
              <input
                className="mt-input"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </label>
          ) : null}
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Email</span>
            <input
              className="mt-input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label className="mt-field">
            <span className="mt-field-label mt-mono">Password</span>
            <input
              className="mt-input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {!signup ? (
            <button
              className="mt-auth-forgot mt-mono"
              type="button"
              onClick={() => onMode('reset')}
            >
              Forgot password?
            </button>
          ) : null}
          {error ? <div className="mt-auth-error mt-mono">{error}</div> : null}
          {notice ? <div className="mt-reset-ok mt-mono">{notice}</div> : null}
          <button className="mt-auth-primary" type="submit" disabled={!canSubmit}>
            {pending ? 'Working…' : signup ? 'Create account' : 'Sign in'}
          </button>
          <div className="mt-auth-toggle">
            {signup ? 'Already have an account? ' : 'New to Meant? '}
            <button
              type="button"
              onClick={() => {
                setError(null)
                onMode(signup ? 'signin' : 'signup')
              }}
            >
              {signup ? 'Sign in' : 'Create an account'}
            </button>
          </div>
        </form>
      </main>
    </div>
  )
}

function AuthBrand() {
  return (
    <aside className="mt-auth-brand">
      <img className="mt-auth-brand-logo" src="/assets/meant-logo.png" alt="Meant" />
      <div className="mt-auth-brand-mid">
        <h1 className="mt-auth-brand-line">
          Everything here is <em>Meant</em> for you.
        </h1>
        <p className="mt-auth-brand-sub">
          One account for supported stores. Meant learns what matters to you and quietly filters out
          the rest.
        </p>
        <div className="mt-auth-pills">
          {['Organic', 'Natural materials', 'Strong reviews', 'Sustainable brands'].map((pill) => (
            <span className="mt-auth-pill" key={pill}>
              {pill}
            </span>
          ))}
        </div>
      </div>
      <div className="mt-auth-brand-foot mt-mono">
        Your preferences travel with you everywhere you shop.
      </div>
    </aside>
  )
}
