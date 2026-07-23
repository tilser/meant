import { describe, expect, test } from 'bun:test'

import type { Product } from '../types'
import { addChatProductToCart, cartInChatMessage, exactProductOfferKey } from './chatCartAddition'

const product: Product = {
  id: 'history-product',
  name: 'History product',
  brand: 'Meant',
  category: 'Test',
  tone: 'neutral',
  match: 90,
  priceFrom: 25,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: '',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  offers: [
    {
      offerKey: '  history-offer  ',
      merchant: 'Merchant',
      price: 25,
      delivery: 'Calculated at checkout',
    },
  ],
}

describe('chat cart addition', () => {
  test('uses the shared selected-offer cart callback with the historical exact offer key', async () => {
    const calls: { product: Product; offerKey: string }[] = []
    const events: string[] = []

    const result = await addChatProductToCart(
      product,
      async (candidate, offerKey) => {
        events.push('add')
        calls.push({ product: candidate, offerKey })
        return true
      },
      () => events.push('show-cart'),
    )

    expect(result).toBe('added')
    expect(calls).toEqual([{ product, offerKey: 'history-offer' }])
    expect(events).toEqual(['show-cart', 'add'])
  })

  test('does not call the cart endpoint without an exact offer reference', async () => {
    let calls = 0
    let cartMessages = 0

    const result = await addChatProductToCart(
      { ...product, offers: [] },
      async () => {
        calls += 1
        return true
      },
      () => {
        cartMessages += 1
      },
    )

    expect(result).toBe('missing-offer')
    expect(calls).toBe(0)
    expect(cartMessages).toBe(0)
  })

  test('reports a rejected or failed cart request without throwing into the chat view', async () => {
    expect(
      await addChatProductToCart(
        product,
        async () => false,
        () => undefined,
      ),
    ).toBe('failed')
    expect(
      await addChatProductToCart(
        product,
        async () => {
          throw new Error('request failed')
        },
        () => undefined,
      ),
    ).toBe('failed')
  })

  test('creates a live cart placeholder that the chat row can hydrate from cart state', () => {
    expect(cartInChatMessage('local-cart')).toEqual({
      id: 'local-cart',
      role: 'ai',
      blocks: [{ type: 'cart', lines: [] }],
    })
  })

  test('normalizes the exact offer key selected from history', () => {
    expect(exactProductOfferKey(product)).toBe('history-offer')
  })
})
