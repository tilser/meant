import { describe, expect, test } from 'bun:test'

import type { CartItem } from '../types'
import { checkoutInChatMessage } from './checkoutPreparation'

function cartLine(id: string, cartId?: string | null): CartItem {
  return { id, merchant: 'Test merchant', qty: 1, cartId }
}

describe('checkoutInChatMessage', () => {
  test('opens a local checkout surface for carts created through the cart API', () => {
    expect(
      checkoutInChatMessage('checkout-1', [
        cartLine('product-1', ' cart-1 '),
        cartLine('product-2', 'cart-1'),
      ]),
    ).toEqual({
      id: 'checkout-1',
      role: 'ai',
      blocks: [
        {
          type: 'text',
          text: 'I grouped checkout by merchant and kept it inside the chat.',
        },
        { type: 'checkout', merchantCount: 1 },
      ],
    })
  })

  test('counts one checkout group per server cart', () => {
    expect(
      checkoutInChatMessage('checkout-2', [
        cartLine('product-1', 'cart-1'),
        cartLine('product-2', 'cart-2'),
      ]),
    ).toMatchObject({
      blocks: [{ type: 'text' }, { type: 'checkout', merchantCount: 2 }],
    })
  })

  test('does not open checkout when the live cart has no server cart ID', () => {
    expect(
      checkoutInChatMessage('checkout-missing', [
        cartLine('local-product'),
        cartLine('syncing-product', '  '),
      ]),
    ).toBeNull()
  })
})
