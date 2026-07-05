import { describe, expect, test } from 'bun:test'

import type { CartProfile } from '../../../lib/apiClient'
import type { MerchantCartSnapshot } from './types'
import {
  cartSnapshotFromProfile,
  cartSnapshotHasReliableTotal,
  cartSnapshotSavings,
  cartSnapshotSubtotal,
  cartSnapshotTotal,
} from './utils'

function merchantSnapshot(overrides: Partial<MerchantCartSnapshot>): MerchantCartSnapshot {
  return {
    merchantKey: 'merchant-1',
    merchant: 'Merchant',
    cartId: 'cart-1',
    remoteCartId: null,
    checkoutUrl: null,
    continueUrl: null,
    subtotalAmount: null,
    totalAmount: null,
    currency: 'USD',
    appliedCodes: [],
    ...overrides,
  }
}

describe('cart feature utilities', () => {
  test('preserves numeric zero cart amounts from cart profiles', () => {
    const profile = {
      cartId: 'cart-1',
      subtotalAmount: 0,
      totalAmount: 0,
      currency: 'USD',
      appliedCodes: [
        {
          type: 'DISCOUNT',
          code: 'ZERO',
          amount: 0,
          currency: 'USD',
        },
      ],
    } as unknown as CartProfile

    const snapshot = cartSnapshotFromProfile(profile, 'merchant-1', 'Merchant')

    expect(snapshot.subtotalAmount).toBe(0)
    expect(snapshot.totalAmount).toBe(0)
    expect(snapshot.appliedCodes[0]?.amount).toBe(0)
  })

  test('keeps delivery fees in automatic merchant savings calculations', () => {
    const snapshot = merchantSnapshot({
      subtotalAmount: 100,
      totalAmount: 95,
    })

    expect(cartSnapshotSavings(snapshot, 100, 110)).toBe(15)
  })

  test('uses explicit applied code amounts before inferred savings', () => {
    const snapshot = merchantSnapshot({
      subtotalAmount: 100,
      totalAmount: 95,
      appliedCodes: [
        {
          type: 'DISCOUNT',
          code: 'SAVE5',
          displayCode: 'SAVE5',
          label: null,
          applicable: true,
          amount: -5,
          currency: 'USD',
        },
      ],
    })

    expect(cartSnapshotSavings(snapshot, 100, 110)).toBe(5)
  })

  test('ignores stale snapshot totals that do not match fallback cart lines', () => {
    const snapshot = merchantSnapshot({
      subtotalAmount: 752,
      totalAmount: 752,
    })

    expect(cartSnapshotSubtotal(snapshot, 35)).toBe(35)
    expect(cartSnapshotTotal(snapshot, 39.99)).toBe(39.99)
    expect(cartSnapshotSavings(snapshot, 35, 39.99)).toBe(0)
    expect(cartSnapshotHasReliableTotal(snapshot, 39.99)).toBe(false)
  })

  test('uses plausible snapshot totals', () => {
    const snapshot = merchantSnapshot({
      subtotalAmount: 35,
      totalAmount: 42,
    })

    expect(cartSnapshotSubtotal(snapshot, 35)).toBe(35)
    expect(cartSnapshotTotal(snapshot, 39.99)).toBe(42)
    expect(cartSnapshotHasReliableTotal(snapshot, 39.99)).toBe(true)
  })

  test('validates snapshot totals against explicit subtotal baseline', () => {
    const snapshot = merchantSnapshot({
      totalAmount: 150,
    })

    expect(cartSnapshotTotal(snapshot, 200, 35)).toBe(200)
    expect(cartSnapshotHasReliableTotal(snapshot, 200, 35)).toBe(false)
  })
})
