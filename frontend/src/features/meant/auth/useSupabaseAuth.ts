import { useCallback, useEffect, useState } from 'react'
import type { Session } from '@supabase/supabase-js'

import { supabase } from '../../../lib/supabase'
import { resolveAuthCaptchaToken, type AuthCaptchaAction } from './turnstile'

export type AuthBootstrapStatus =
  'initializing' | 'anonymous' | 'permanent' | 'signed-out' | 'callback' | 'error'

export interface AuthActions {
  signInWithPassword: (email: string, password: string) => Promise<{ error: string | null }>
  signUp: (
    email: string,
    password: string,
    fullName: string,
  ) => Promise<{ error: string | null; needsConfirmation: boolean }>
  signInWithOAuth: (provider: 'google' | 'apple') => Promise<{ error: string | null }>
  linkOAuthIdentity: (provider: 'google' | 'apple') => Promise<{ error: string | null }>
  sendMagicLink: (email: string) => Promise<{ error: string | null }>
  linkEmailIdentity: (email: string) => Promise<{ error: string | null; sent: boolean }>
  resetPassword: (email: string) => Promise<{ error: string | null }>
  signInAnonymously: () => Promise<{ error: string | null }>
  ensureSession: () => Promise<{ session: Session | null; error: string | null }>
  retryAnonymousBootstrap: () => Promise<void>
  signOut: () => Promise<void>
}

export interface AuthState extends AuthActions {
  session: Session | null
  loading: boolean
  isAnonymous: boolean
  status: AuthBootstrapStatus
  bootstrapError: string | null
}

export interface SupabaseAuthOptions {
  bootstrapAnonymous?: boolean
}

type AnonymousSessionResult = { session: Session | null; error: string | null }

export function createAnonymousSessionBootstrap(
  getSession: () => Promise<{
    data: { session: Session | null }
    error: { message: string } | null
  }>,
  signIn: () => Promise<{
    data: { session: Session | null }
    error: { message: string } | null
  }>,
): () => Promise<AnonymousSessionResult> {
  let inFlight: Promise<AnonymousSessionResult> | null = null
  return () => {
    if (inFlight) return inFlight
    inFlight = (async () => {
      try {
        const current = await getSession()
        if (current.data.session) return { session: current.data.session, error: null }
        if (current.error) return { session: null, error: current.error.message }
        const created = await signIn()
        return { session: created.data.session, error: created.error?.message ?? null }
      } catch (error) {
        return {
          session: null,
          error: error instanceof Error ? error.message : 'Security verification failed.',
        }
      }
    })().finally(() => {
      inFlight = null
    })
    return inFlight
  }
}

export function isAnonymousSession(session: Session | null): boolean {
  return session?.user.is_anonymous === true
}

export function isAuthCallbackLocation(location: Pick<Location, 'pathname' | 'search' | 'hash'>) {
  if (
    location.pathname.startsWith('/auth/callback') ||
    location.pathname.startsWith('/auth/recovery')
  ) {
    return true
  }
  const parameters = `${location.search}&${location.hash}`
  return /(?:code=|error=|type=recovery|access_token=|refresh_token=)/.test(parameters)
}

function redirectUrl(path = '/auth/callback') {
  return `${window.location.origin}${path}`
}

function callbackError(location: Pick<Location, 'search' | 'hash'>): string | null {
  const parameters = new URLSearchParams(
    `${location.search.replace(/^\?/, '')}&${location.hash.replace(/^#/, '')}`,
  )
  return parameters.get('error_description') ?? parameters.get('error')
}

function authActionError(error: unknown): string {
  return error instanceof Error ? error.message : 'Security verification failed.'
}

export async function withAuthCaptchaToken<T>(
  action: AuthCaptchaAction,
  request: (captchaToken: string | undefined) => Promise<T>,
  resolveCaptchaToken: (
    action: AuthCaptchaAction,
  ) => Promise<string | null> = resolveAuthCaptchaToken,
): Promise<T> {
  const captchaToken = await resolveCaptchaToken(action)
  return request(captchaToken ?? undefined)
}

const createOrRestoreAnonymousSession = createAnonymousSessionBootstrap(
  () => supabase.auth.getSession(),
  () =>
    withAuthCaptchaToken('anonymous-sign-in', (captchaToken) =>
      supabase.auth.signInAnonymously(captchaToken ? { options: { captchaToken } } : undefined),
    ),
)

/**
 * Resolves a persisted Supabase session and, for the main application only, creates one anonymous
 * identity when no session exists. The module-level promise makes creation idempotent across React
 * remounts and concurrent listeners.
 */
export function useSupabaseAuth(options: SupabaseAuthOptions = {}): AuthState {
  const bootstrapAnonymous = options.bootstrapAnonymous ?? true
  const callback = typeof window !== 'undefined' && isAuthCallbackLocation(window.location)
  const [session, setSession] = useState<Session | null>(null)
  const [status, setStatus] = useState<AuthBootstrapStatus>(callback ? 'callback' : 'initializing')
  const [bootstrapError, setBootstrapError] = useState<string | null>(null)

  const applySession = useCallback((nextSession: Session | null) => {
    setSession(nextSession)
    setStatus(
      nextSession ? (isAnonymousSession(nextSession) ? 'anonymous' : 'permanent') : 'signed-out',
    )
  }, [])

  const ensureSession: AuthActions['ensureSession'] = useCallback(async () => {
    setStatus('initializing')
    setBootstrapError(null)
    const result = await createOrRestoreAnonymousSession()
    if (result.error) {
      setSession(null)
      setBootstrapError(result.error)
      setStatus('error')
      return result
    }
    applySession(result.session)
    return result
  }, [applySession])

  useEffect(() => {
    let active = true
    const initialize = async () => {
      const { data, error } = await supabase.auth.getSession()
      if (!active) return
      const callbackFailure = callback ? callbackError(window.location) : null
      if (callbackFailure) {
        setSession(data.session)
        setBootstrapError(callbackFailure)
        setStatus('error')
        return
      }
      if (data.session) {
        applySession(data.session)
        return
      }
      if (error) {
        setBootstrapError(error.message)
        setStatus('error')
        return
      }
      if (bootstrapAnonymous && !callback) {
        await ensureSession()
      } else {
        setStatus(callback ? 'callback' : 'signed-out')
      }
    }
    void initialize()

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      if (!active) return
      if (nextSession) {
        applySession(nextSession)
      } else if (bootstrapAnonymous && !callback) {
        void ensureSession()
      } else {
        applySession(null)
      }
    })

    return () => {
      active = false
      subscription.unsubscribe()
    }
  }, [applySession, bootstrapAnonymous, callback, ensureSession])

  const signInWithPassword: AuthActions['signInWithPassword'] = async (email, password) => {
    try {
      const { error } = await withAuthCaptchaToken('password-sign-in', (captchaToken) =>
        supabase.auth.signInWithPassword({
          email,
          password,
          options: captchaToken ? { captchaToken } : undefined,
        }),
      )
      return { error: error?.message ?? null }
    } catch (error) {
      return { error: authActionError(error) }
    }
  }

  const signUp: AuthActions['signUp'] = async (email, password, fullName) => {
    try {
      const { data, error } = await withAuthCaptchaToken('sign-up', (captchaToken) =>
        supabase.auth.signUp({
          email,
          password,
          options: {
            data: fullName.trim() ? { full_name: fullName.trim() } : undefined,
            captchaToken,
          },
        }),
      )
      return { error: error?.message ?? null, needsConfirmation: !error && !data.session }
    } catch (error) {
      return { error: authActionError(error), needsConfirmation: false }
    }
  }

  const signInWithOAuth: AuthActions['signInWithOAuth'] = async (provider) => {
    const { error } = await supabase.auth.signInWithOAuth({
      provider,
      options: { redirectTo: redirectUrl() },
    })
    return { error: error?.message ?? null }
  }

  const linkOAuthIdentity: AuthActions['linkOAuthIdentity'] = async (provider) => {
    const { error } = await supabase.auth.linkIdentity({
      provider,
      options: { redirectTo: redirectUrl() },
    })
    return { error: error?.message ?? null }
  }

  const sendMagicLink: AuthActions['sendMagicLink'] = async (email) => {
    try {
      const { error } = await withAuthCaptchaToken('magic-link', (captchaToken) =>
        supabase.auth.signInWithOtp({
          email,
          options: { emailRedirectTo: redirectUrl(), shouldCreateUser: true, captchaToken },
        }),
      )
      return { error: error?.message ?? null }
    } catch (error) {
      return { error: authActionError(error) }
    }
  }

  const linkEmailIdentity: AuthActions['linkEmailIdentity'] = async (email) => {
    const { error } = await supabase.auth.updateUser({ email }, { emailRedirectTo: redirectUrl() })
    return { error: error?.message ?? null, sent: !error }
  }

  const resetPassword: AuthActions['resetPassword'] = async (email) => {
    try {
      const { error } = await withAuthCaptchaToken('password-reset', (captchaToken) =>
        supabase.auth.resetPasswordForEmail(email, {
          redirectTo: redirectUrl('/auth/recovery'),
          captchaToken,
        }),
      )
      return { error: error?.message ?? null }
    } catch (error) {
      return { error: authActionError(error) }
    }
  }

  const signInAnonymously: AuthActions['signInAnonymously'] = async () => {
    const result = await createOrRestoreAnonymousSession()
    if (result.session) applySession(result.session)
    return { error: result.error }
  }

  const retryAnonymousBootstrap = useCallback(async () => {
    await ensureSession()
  }, [ensureSession])

  const signOut = async () => {
    await supabase.auth.signOut()
    setSession(null)
    if (bootstrapAnonymous && !callback) await ensureSession()
    else setStatus('signed-out')
  }

  return {
    session,
    loading: status === 'initializing' || status === 'callback',
    isAnonymous: isAnonymousSession(session),
    status,
    bootstrapError,
    signInWithPassword,
    signUp,
    signInWithOAuth,
    linkOAuthIdentity,
    sendMagicLink,
    linkEmailIdentity,
    resetPassword,
    signInAnonymously,
    ensureSession,
    retryAnonymousBootstrap,
    signOut,
  }
}
