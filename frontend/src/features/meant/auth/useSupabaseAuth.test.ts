import { describe, expect, test } from 'bun:test'
import type { Session } from '@supabase/supabase-js'

import {
  createAnonymousSessionBootstrap,
  isAnonymousSession,
  isAuthCallbackLocation,
} from './useSupabaseAuth'

describe('Supabase auth routing', () => {
  test('recognizes anonymous sessions without relying on email', () => {
    const session = { user: { email: undefined, is_anonymous: true } } as Session
    expect(isAnonymousSession(session)).toBeTrue()
  })

  test('keeps login and callback routes out of anonymous bootstrap', () => {
    expect(isAuthCallbackLocation({ pathname: '/auth/callback', search: '', hash: '' })).toBeTrue()
    expect(
      isAuthCallbackLocation({ pathname: '/', search: '?code=oauth-code', hash: '' }),
    ).toBeTrue()
    expect(isAuthCallbackLocation({ pathname: '/', search: '', hash: '' })).toBeFalse()
  })

  test('creates exactly one anonymous session for concurrent bootstrap requests', async () => {
    const session = { user: { id: 'guest', is_anonymous: true } } as Session
    let creations = 0
    const bootstrap = createAnonymousSessionBootstrap(
      async () => ({ data: { session: null }, error: null }),
      async () => {
        creations += 1
        await Promise.resolve()
        return { data: { session }, error: null }
      },
    )

    const [first, second, third] = await Promise.all([bootstrap(), bootstrap(), bootstrap()])

    expect(creations).toBe(1)
    expect(first.session).toBe(session)
    expect(second.session).toBe(session)
    expect(third.session).toBe(session)
  })

  test('never replaces an existing permanent session with a guest', async () => {
    const session = { user: { id: 'permanent', is_anonymous: false } } as Session
    let creations = 0
    const bootstrap = createAnonymousSessionBootstrap(
      async () => ({ data: { session }, error: null }),
      async () => {
        creations += 1
        return { data: { session: null }, error: null }
      },
    )

    expect((await bootstrap()).session).toBe(session)
    expect(creations).toBe(0)
  })
})
