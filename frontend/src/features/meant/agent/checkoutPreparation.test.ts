import { describe, expect, test } from 'bun:test'

import type { CartItem } from '../types'
import { checkoutInChatBlock } from './checkoutPreparation'

function cartLine(id: string, cartId?: string | null): CartItem {
  return { id, merchant: 'Test merchant', qty: 1, cartId }
}

describe('checkoutInChatBlock', () => {
  test('prepares an inline checkout surface for carts created through the cart API', () => {
    expect(
      checkoutInChatBlock([cartLine('product-1', ' cart-1 '), cartLine('product-2', 'cart-1')]),
    ).toEqual({
      type: 'checkout',
      merchantCount: 1,
    })
  })

  test('counts one checkout group per server cart', () => {
    expect(
      checkoutInChatBlock([cartLine('product-1', 'cart-1'), cartLine('product-2', 'cart-2')]),
    ).toEqual({ type: 'checkout', merchantCount: 2 })
  })

  test('does not open checkout when the live cart has no server cart ID', () => {
    expect(
      checkoutInChatBlock([cartLine('local-product'), cartLine('syncing-product', '  ')]),
    ).toBeNull()
  })
})
