import { useEffect, useState } from 'react'
import type { Session } from '@supabase/supabase-js'

import { supabase } from '../../../lib/supabase'

export interface AuthActions {
  signInWithPassword: (email: string, password: string) => Promise<{ error: string | null }>
  signUp: (
    email: string,
    password: string,
    fullName: string,
  ) => Promise<{ error: string | null; needsConfirmation: boolean }>
  signInWithOAuth: (provider: 'google' | 'apple') => Promise<{ error: string | null }>
  resetPassword: (email: string) => Promise<{ error: string | null }>
  signOut: () => Promise<void>
}

export interface AuthState extends AuthActions {
  session: Session | null
  loading: boolean
}

/**
 * Bridges Supabase auth into React state. Reads the persisted session on mount and subscribes to
 * auth changes (sign in/out, token refresh, OAuth redirect return) so `session` always reflects the
 * live state. Login/registration are delegated to the Supabase client SDK; the Spring backend only
 * validates the resulting JWT.
 */
export function useSupabaseAuth(): AuthState {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true

    supabase.auth.getSession().then(({ data }) => {
      if (!active) return
      setSession(data.session)
      setLoading(false)
    })

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession)
      setLoading(false)
    })

    return () => {
      active = false
      subscription.unsubscribe()
    }
  }, [])

  const signInWithPassword: AuthActions['signInWithPassword'] = async (email, password) => {
    const { error } = await supabase.auth.signInWithPassword({ email, password })
    return { error: error?.message ?? null }
  }

  const signUp: AuthActions['signUp'] = async (email, password, fullName) => {
    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      options: { data: { full_name: fullName } },
    })
    // When email confirmation is required, Supabase returns a user without an active session.
    const needsConfirmation = !error && !data.session
    return { error: error?.message ?? null, needsConfirmation }
  }

  const signInWithOAuth: AuthActions['signInWithOAuth'] = async (provider) => {
    const { error } = await supabase.auth.signInWithOAuth({
      provider,
      options: { redirectTo: window.location.origin },
    })
    return { error: error?.message ?? null }
  }

  const resetPassword: AuthActions['resetPassword'] = async (email) => {
    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo: window.location.origin,
    })
    return { error: error?.message ?? null }
  }

  const signOut: AuthActions['signOut'] = async () => {
    await supabase.auth.signOut()
  }

  return {
    session,
    loading,
    signInWithPassword,
    signUp,
    signInWithOAuth,
    resetPassword,
    signOut,
  }
}
