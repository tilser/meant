import { describe, expect, test } from 'bun:test'

import {
  merchantCheckoutStartBlocked,
  shouldAutoStartMerchantCheckout,
} from './checkoutActivationPolicy'

describe('merchantCheckoutStartBlocked', () => {
  test('blocks a second merchant while the current checkout session is active', () => {
    expect(
      merchantCheckoutStartBlocked({
        groupReady: true,
        payingMerchant: null,
        activeCartId: 'cart-a',
        releasedCartIds: new Set(),
        targetCartId: 'cart-b',
      }),
    ).toBe(true)
  })

  test('allows the next merchant after the active checkout closes or completes', () => {
    expect(
      merchantCheckoutStartBlocked({
        groupReady: true,
        payingMerchant: null,
        activeCartId: 'cart-a',
        releasedCartIds: new Set(['cart-a']),
        targetCartId: 'cart-b',
      }),
    ).toBe(false)
  })

  test('continues to block unsynced groups and concurrent startup requests', () => {
    const base = {
      activeCartId: null,
      releasedCartIds: new Set<string>(),
      targetCartId: 'cart-b',
    }
    expect(merchantCheckoutStartBlocked({ ...base, groupReady: false, payingMerchant: null })).toBe(
      true,
    )
    expect(
      merchantCheckoutStartBlocked({ ...base, groupReady: true, payingMerchant: 'merchant-a' }),
    ).toBe(true)
  })
})

describe('shouldAutoStartMerchantCheckout', () => {
  const readyCheckout = {
    enabled: true,
    merchantCount: 1,
    groupReady: true,
    checkoutBusy: false,
    payingMerchant: null,
    activeCartId: null,
    targetCartId: 'cart-a',
  }

  test('starts the newest ready single-merchant agent checkout', () => {
    expect(shouldAutoStartMerchantCheckout(readyCheckout)).toBe(true)
  })

  test('keeps historical, multi-merchant, busy, and active checkouts manual', () => {
    expect(shouldAutoStartMerchantCheckout({ ...readyCheckout, enabled: false })).toBe(false)
    expect(shouldAutoStartMerchantCheckout({ ...readyCheckout, merchantCount: 2 })).toBe(false)
    expect(shouldAutoStartMerchantCheckout({ ...readyCheckout, checkoutBusy: true })).toBe(false)
    expect(
      shouldAutoStartMerchantCheckout({ ...readyCheckout, activeCartId: 'cart-existing' }),
    ).toBe(false)
  })
})
