import { describe, expect, test } from 'bun:test'

import type { CartItem } from '../types'
import { cartCountSummary, formatCartCount } from './cartCounts'

function item(overrides: Partial<CartItem> = {}): CartItem {
  return {
    id: 'product-1',
    merchant: 'Merchant A',
    merchantId: 'merchant-a',
    qty: 1,
    ...overrides,
  }
}

describe('cart count semantics', () => {
  test('reports an empty cart with zero lines, units, and merchants', () => {
    expect(cartCountSummary([])).toEqual({
      lineItemCount: 0,
      unitCount: 0,
      merchantCount: 0,
    })
    expect(formatCartCount(0, 'line item')).toBe('0 line items')
    expect(formatCartCount(0, 'unit')).toBe('0 units')
  })

  test('reports one line with one unit using singular labels', () => {
    expect(cartCountSummary([item()])).toEqual({
      lineItemCount: 1,
      unitCount: 1,
      merchantCount: 1,
    })
    expect(formatCartCount(1, 'line item')).toBe('1 line item')
    expect(formatCartCount(1, 'unit')).toBe('1 unit')
    expect(formatCartCount(1, 'merchant')).toBe('1 merchant')
  })

  test('distinguishes multiple units from a single line item', () => {
    expect(cartCountSummary([item({ qty: 2 })])).toEqual({
      lineItemCount: 1,
      unitCount: 2,
      merchantCount: 1,
    })
    expect(formatCartCount(2, 'unit')).toBe('2 units')
  })

  test('counts multiple lines and merchants independently from total units', () => {
    const cart = [
      item({ id: 'product-1', qty: 2 }),
      item({ id: 'product-2', qty: 1 }),
      item({ id: 'product-3', merchant: 'Merchant B', merchantId: 'merchant-b', qty: 3 }),
    ]

    expect(cartCountSummary(cart)).toEqual({
      lineItemCount: 3,
      unitCount: 6,
      merchantCount: 2,
    })
    expect(formatCartCount(3, 'line item')).toBe('3 line items')
    expect(formatCartCount(2, 'merchant')).toBe('2 merchants')
  })
})
