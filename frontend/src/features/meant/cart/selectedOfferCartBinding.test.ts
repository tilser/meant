import { describe, expect, test } from 'bun:test'

import type { CartProfile } from '../../../lib/apiClient'
import type { CartItem } from '../types'
import {
  confirmedCartIdForOffer,
  mergeConfirmedCartSnapshot,
  mergeInitialSelectedOfferSnapshot,
} from './selectedOfferCartBinding'

function confirmedItem(overrides: Partial<CartItem>): CartItem {
  return {
    id: 'product-a',
    merchant: 'Same merchant name',
    qty: 1,
    offerKey: 'offer-a',
    cartId: 'cart-a',
    cartLineId: 'line-a',
    syncing: false,
    syncError: null,
    ...overrides,
  }
}

describe('selected offer cart binding', () => {
  test('does not reuse a same-name merchant cart for a different exact offer', () => {
    const cart = [confirmedItem({ offerKey: 'offer-other', cartId: 'cart-other' })]

    expect(confirmedCartIdForOffer(cart, 'offer-a')).toBeUndefined()
  })

  test('reuses a confirmed cart only for the exact repeated offer key', () => {
    const cart = [confirmedItem({})]

    expect(confirmedCartIdForOffer(cart, 'offer-a')).toBe('cart-a')
  })

  test('does not reuse optimistic or rejected local cart state', () => {
    expect(
      confirmedCartIdForOffer([confirmedItem({ cartLineId: null, syncing: true })], 'offer-a'),
    ).toBeUndefined()
    expect(
      confirmedCartIdForOffer([confirmedItem({ syncError: 'Rejected by server' })], 'offer-a'),
    ).toBeUndefined()
  })

  test('initial response reconciliation does not mutate an unrelated same-name item', () => {
    const unrelated = confirmedItem({
      id: 'unrelated-product',
      offerKey: 'unrelated-offer',
      cartId: null,
      cartLineId: null,
    })
    const selected = confirmedItem({
      cartId: null,
      cartLineId: null,
      syncing: true,
    })
    const snapshot = {
      cartId: 'server-cart',
      merchantId: 'server-merchant',
      merchantDomain: 'server.example',
      lines: [
        {
          offerKey: 'offer-a',
          cartLineId: 'server-line',
          quantity: 1,
        },
      ],
    } as unknown as CartProfile

    const merged = mergeInitialSelectedOfferSnapshot(
      [unrelated, selected],
      'product-a',
      'offer-a',
      snapshot,
    )

    expect(merged[0]).toEqual(unrelated)
    expect(merged[1]).toMatchObject({
      offerKey: 'offer-a',
      cartId: 'server-cart',
      cartLineId: 'server-line',
      merchantId: 'server-merchant',
      merchantDomain: 'server.example',
      syncing: false,
    })
  })

  test('repeat response reconciliation is scoped by confirmed cart id, not merchant name', () => {
    const sameNameOtherCart = confirmedItem({
      id: 'other-product',
      offerKey: 'other-offer',
      cartId: 'other-cart',
      cartLineId: 'other-line',
    })
    const repeated = confirmedItem({})
    const snapshot = {
      cartId: 'cart-a',
      lines: [{ offerKey: 'offer-a', cartLineId: 'line-a', quantity: 2 }],
    } as unknown as CartProfile

    const merged = mergeConfirmedCartSnapshot([sameNameOtherCart, repeated], 'cart-a', snapshot)

    expect(merged[0]).toEqual(sameNameOtherCart)
    expect(merged[1]?.qty).toBe(2)
  })
})
