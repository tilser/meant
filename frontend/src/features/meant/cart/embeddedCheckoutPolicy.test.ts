import { describe, expect, test } from 'bun:test'

import type { EmbeddedCheckoutBootstrapProfile } from '../../../lib/apiClient'
import { resolveEmbeddedCheckoutBootstrap, safeExternalCheckoutUrl } from './embeddedCheckoutPolicy'

const descriptor: EmbeddedCheckoutBootstrapProfile = {
  action: 'EMBEDDED',
  sessionId: 'session-1',
  cartId: 'cart-1',
  checkoutAttemptId: 'attempt-1',
  checkoutId: 'checkout-1',
  checkoutUrl: 'https://shop.example/checkouts/cn/test',
  fallbackContinueUrl: 'https://shop.example/checkouts/cn/test',
  protocolVersion: '2026-04-08',
  allowedDelegations: [],
  expiresAt: '2026-07-12T00:10:00Z',
}

describe('embedded checkout bootstrap policy', () => {
  test('admits only the pinned base Checkout Kit protocol without delegations or auth', () => {
    expect(
      resolveEmbeddedCheckoutBootstrap(descriptor, true, Date.parse('2026-07-12T00:00:00Z')),
    ).toEqual({ mode: 'EMBEDDED', reason: 'NONE' })
    expect(
      resolveEmbeddedCheckoutBootstrap(
        { ...descriptor, protocolVersion: '2026-01-23' },
        true,
        Date.parse('2026-07-12T00:00:00Z'),
      ),
    ).toEqual({ mode: 'FALLBACK', reason: 'UNSUPPORTED_PROTOCOL' })
    expect(
      resolveEmbeddedCheckoutBootstrap(
        { ...descriptor, allowedDelegations: ['payment.credential'] },
        true,
        Date.parse('2026-07-12T00:00:00Z'),
      ),
    ).toEqual({ mode: 'FALLBACK', reason: 'UNSUPPORTED_PROTOCOL' })
  })

  test('keeps the kill switch, expiry, and merchant handoff fail-safe', () => {
    expect(resolveEmbeddedCheckoutBootstrap(descriptor, false)).toEqual({
      mode: 'FALLBACK',
      reason: 'KILL_SWITCH',
    })
    expect(
      resolveEmbeddedCheckoutBootstrap(descriptor, true, Date.parse('2026-07-12T00:11:00Z')),
    ).toEqual({ mode: 'FALLBACK', reason: 'SESSION_EXPIRED' })
    expect(
      resolveEmbeddedCheckoutBootstrap(
        { ...descriptor, action: 'EXTERNAL_HANDOFF', sessionId: undefined, checkoutUrl: undefined },
        true,
      ),
    ).toEqual({ mode: 'FALLBACK', reason: 'MERCHANT_HANDOFF' })
  })

  test('renders only absolute credential-free HTTPS fallback URLs', () => {
    expect(safeExternalCheckoutUrl('https://shop.example/checkout')).toBe(
      'https://shop.example/checkout',
    )
    expect(safeExternalCheckoutUrl('javascript:alert(1)')).toBeNull()
    expect(safeExternalCheckoutUrl('http://shop.example/checkout')).toBeNull()
    expect(safeExternalCheckoutUrl('https://user:secret@shop.example/checkout')).toBeNull()
    expect(safeExternalCheckoutUrl(undefined)).toBeNull()
  })
})
