import { describe, expect, test } from 'bun:test'

import type { CartProfile } from '../../../lib/apiClient'
import type { CartDeliveryGroup, CartDeliveryOption, CartItem } from '../types'
import type { DeliveryAddressDraft, MerchantCartSnapshot } from './types'
import {
  cartSnapshotFromProfile,
  cartSnapshotHasReliableTotal,
  cartSnapshotSavings,
  cartSnapshotSubtotal,
  cartSnapshotTotal,
  cartSummaryDelivery,
  deliveryAddressArguments,
  selectedDeliveryOptionsForCart,
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
  test('builds a typed camelCase delivery address payload', () => {
    const address: DeliveryAddressDraft = {
      countryCode: ' us ',
      city: ' New York ',
      postalCode: ' 10001 ',
      provinceCode: ' NY ',
    }

    expect(deliveryAddressArguments(address)).toEqual({
      addressCountry: 'US',
      addressLocality: 'New York',
      postalCode: '10001',
      addressRegion: 'NY',
    })
  })

  test('builds typed camelCase selections for every delivery group', () => {
    const standard: CartDeliveryOption = { handle: 'standard', selected: true }
    const express: CartDeliveryOption = { handle: 'express' }
    const firstGroup: CartDeliveryGroup = {
      id: 'group-1',
      deliveryOptions: [standard, express],
      selectedDeliveryOption: standard,
    }
    const secondGroup: CartDeliveryGroup = {
      handle: 'group-2',
      deliveryOptions: [standard],
      selectedDeliveryOption: standard,
    }
    const cart: CartItem[] = [
      {
        id: 'product-1',
        merchant: 'Merchant',
        merchantId: 'merchant-1',
        qty: 1,
        deliveryGroups: [firstGroup, secondGroup],
      },
    ]

    expect(selectedDeliveryOptionsForCart(cart, 'merchant-1', firstGroup, express)).toEqual([
      { groupId: 'group-1', selectedOptionId: 'express' },
      { groupId: 'group-2', selectedOptionId: 'standard' },
    ])
  })

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

  test('ignores negative snapshot amounts', () => {
    const snapshot = merchantSnapshot({
      subtotalAmount: -35,
      totalAmount: -42,
    })

    expect(cartSnapshotSubtotal(snapshot, 35)).toBe(35)
    expect(cartSnapshotTotal(snapshot, 39.99, 35)).toBe(39.99)
    expect(cartSnapshotHasReliableTotal(snapshot, 39.99, 35)).toBe(false)
  })

  test('derives summary delivery from displayed totals', () => {
    expect(cartSummaryDelivery(35, 0, 42)).toBe(7)
    expect(cartSummaryDelivery(35, 5, 42)).toBe(12)
    expect(cartSummaryDelivery(42, 0, 35)).toBe(0)
  })
})
