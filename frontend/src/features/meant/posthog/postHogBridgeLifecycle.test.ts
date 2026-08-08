import { describe, expect, test } from 'bun:test'
import type { AuthChangeEvent, Session } from '@supabase/supabase-js'

import { installPostHogAnalyticsBridge } from './postHogBridgeLifecycle'
import type { MeantAnalyticsIdentity, PostHogAnalyticsIntegration } from './postHogAnalytics'

class FakeIntegration implements PostHogAnalyticsIntegration {
  readonly identities: MeantAnalyticsIdentity[] = []
  starts = 0
  stops = 0
  resets = 0

  start() {
    this.starts += 1
  }

  stop() {
    this.stops += 1
  }

  ready() {
    return Promise.resolve()
  }

  syncIdentity(identity: MeantAnalyticsIdentity) {
    this.identities.push(identity)
  }

  resetIdentity() {
    this.resets += 1
  }
}

class FakeAuthSource {
  readonly callbacks = new Set<(event: AuthChangeEvent, session: Session | null) => void>()
  maximumSubscribers = 0

  getSession(): Promise<{ data: { session: Session | null } }> {
    return new Promise(() => undefined)
  }

  onAuthStateChange(callback: (event: AuthChangeEvent, session: Session | null) => void) {
    this.callbacks.add(callback)
    this.maximumSubscribers = Math.max(this.maximumSubscribers, this.callbacks.size)
    return {
      data: {
        subscription: {
          unsubscribe: () => this.callbacks.delete(callback),
        },
      },
    }
  }

  emit(event: AuthChangeEvent, session: Session | null) {
    for (const callback of this.callbacks) callback(event, session)
  }
}

describe('PostHog root bridge lifecycle', () => {
  test('cleans up and remounts without duplicate auth subscriptions or identity events', () => {
    const integration = new FakeIntegration()
    const auth = new FakeAuthSource()
    const firstCleanup = installPostHogAnalyticsBridge(integration, auth)

    firstCleanup?.()
    const secondCleanup = installPostHogAnalyticsBridge(integration, auth)
    auth.emit('SIGNED_IN', {
      user: { id: 'canonical-user-id', is_anonymous: false },
    } as unknown as Session)

    expect(integration.starts).toBe(2)
    expect(integration.stops).toBe(1)
    expect(auth.maximumSubscribers).toBe(1)
    expect(integration.identities).toEqual([{ kind: 'permanent', userId: 'canonical-user-id' }])

    auth.emit('SIGNED_OUT', null)
    expect(integration.resets).toBe(1)

    secondCleanup?.()
    auth.emit('SIGNED_OUT', null)
    expect(integration.resets).toBe(1)
    expect(auth.callbacks.size).toBe(0)
  })
})
