import { describe, expect, test } from 'bun:test'

import type { CartItem, Product } from '../types'
import { orderLineTotal, orderLineUnitPrice } from './orderMapping'

const product = {
  id: 'product-a',
  offers: [
    {
      offerKey: 'offer-small',
      productVariantId: 'variant-small',
      merchant: 'Shared merchant',
      price: 10,
      delivery: 'Standard',
    },
    {
      offerKey: 'offer-large',
      productVariantId: 'variant-large',
      merchant: 'Shared merchant',
      price: 18,
      delivery: 'Standard',
    },
  ],
} as unknown as Product

function item(overrides: Partial<CartItem>): CartItem {
  return { id: product.id, merchant: 'Shared merchant', qty: 1, ...overrides }
}

describe('order line prices', () => {
  test('prefers remote amounts and exact variant identity over merchant display fallback', () => {
    expect(
      orderLineUnitPrice(item({ offerKey: 'offer-small', unitPriceAmount: '7.50' }), product),
    ).toBe(7.5)
    expect(
      orderLineUnitPrice(item({ offerKey: 'offer-small', lineTotalAmount: '16', qty: 2 }), product),
    ).toBe(8)
    expect(orderLineUnitPrice(item({ offerKey: 'offer-large' }), product)).toBe(18)
    expect(orderLineUnitPrice(item({ productVariantId: 'variant-large' }), product)).toBe(18)
    expect(orderLineUnitPrice(item({ offerKey: 'missing-exact-offer' }), product)).toBe(0)
    expect(orderLineUnitPrice(item({}), product)).toBe(10)
    expect(
      orderLineTotal(item({ offerKey: 'offer-small', lineTotalAmount: '31', qty: 2 }), product),
    ).toBe(31)
  })
})
