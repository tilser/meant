import { describe, expect, test } from 'bun:test'

import { checkoutLifecycleEventDetail } from './checkoutAnalytics'

describe('embedded checkout analytics', () => {
  test('exposes only bounded typed lifecycle dimensions', () => {
    const event = checkoutLifecycleEventDetail('checkout_kit_ready', {
      surface: 'cart',
      result: 'succeeded',
      reason: 'NONE',
      latencyMs: 999_999,
    })

    expect(event).toEqual({
      name: 'checkout_kit_ready',
      fields: {
        surface: 'cart',
        result: 'succeeded',
        reason: 'NONE',
        latencyMs: 120_000,
      },
    })
    expect(Object.keys(event.fields)).not.toContain('checkoutUrl')
    expect(Object.keys(event.fields)).not.toContain('sessionId')
  })
})
