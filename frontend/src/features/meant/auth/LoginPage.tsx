import { useEffect, useState } from 'react'

import type { AuthMode } from '../types'
import { AuthScreen } from './AuthScreen'
import { useSupabaseAuth } from './useSupabaseAuth'

export function LoginPage() {
  const [mode, setMode] = useState<AuthMode>('signin')
  const auth = useSupabaseAuth({ bootstrapAnonymous: false })
  const { session, loading, isAnonymous } = auth

  useEffect(() => {
    if (session && !isAnonymous) window.location.replace('/')
  }, [isAnonymous, session])

  if (loading || (session && !isAnonymous))
    return <div className="mt-auth-loading" role="status" aria-label="Resolving account" />
  return <AuthScreen mode={mode} onMode={setMode} auth={auth} />
}

export function AuthCallbackPage() {
  const { status, bootstrapError } = useSupabaseAuth({ bootstrapAnonymous: false })
  useEffect(() => {
    if (status === 'permanent') window.location.replace('/')
  }, [status])
  const failed = status === 'error' || status === 'signed-out' || status === 'anonymous'
  return (
    <main className="mt-auth-callback" role="status">
      <img src="/assets/meant-logo.png" alt="Meant" />
      <h1>{failed ? 'Authentication did not finish' : 'Returning you to Meant…'}</h1>
      {failed ? <p>{bootstrapError ?? 'The sign-in link is invalid or has expired.'}</p> : null}
      {failed ? (
        <div className="mt-auth-callback-actions">
          <a href="/">Return to Meant</a>
          <a href="/login">Try another sign-in method</a>
        </div>
      ) : null}
    </main>
  )
}
