import type { AuthChangeEvent, Session } from '@supabase/supabase-js'

import { identityForSupabaseSession, type PostHogAnalyticsIntegration } from './postHogAnalytics'

interface PostHogAuthSource {
  getSession(): Promise<{ data: { session: Session | null } }>
  onAuthStateChange(callback: (event: AuthChangeEvent, session: Session | null) => void): {
    data: { subscription: { unsubscribe(): void } }
  }
}

export function installPostHogAnalyticsBridge(
  integration: PostHogAnalyticsIntegration | null,
  auth: PostHogAuthSource,
  warn: (message: string) => void = console.warn,
): (() => void) | undefined {
  if (!integration) return
  integration.start()

  let active = true
  let authRevision = 0
  void auth
    .getSession()
    .then(({ data }) => {
      if (active && authRevision === 0) {
        integration.syncIdentity(identityForSupabaseSession(data.session))
      }
    })
    .catch(() => {
      if (active) warn('PostHog identity sync could not read the current auth session.')
    })

  const {
    data: { subscription },
  } = auth.onAuthStateChange((event, session) => {
    authRevision += 1
    if (event === 'SIGNED_OUT') {
      integration.resetIdentity()
      return
    }
    integration.syncIdentity(identityForSupabaseSession(session))
  })

  return () => {
    active = false
    subscription.unsubscribe()
    integration.stop()
  }
}
