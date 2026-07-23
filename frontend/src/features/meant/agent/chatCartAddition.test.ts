import { describe, expect, test } from 'bun:test'

import type { Product } from '../types'
import { addChatProductToCart, exactProductOfferKey } from './chatCartAddition'

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

    const result = await addChatProductToCart(product, async (candidate, offerKey) => {
      calls.push({ product: candidate, offerKey })
      return true
    })

    expect(result).toBe('added')
    expect(calls).toEqual([{ product, offerKey: 'history-offer' }])
  })

  test('does not call the cart endpoint without an exact offer reference', async () => {
    let calls = 0

    const result = await addChatProductToCart({ ...product, offers: [] }, async () => {
      calls += 1
      return true
    })

    expect(result).toBe('missing-offer')
    expect(calls).toBe(0)
  })

  test('reports a rejected or failed cart request without throwing into the chat view', async () => {
    expect(await addChatProductToCart(product, async () => false)).toBe('failed')
    expect(
      await addChatProductToCart(product, async () => {
        throw new Error('request failed')
      }),
    ).toBe('failed')
  })

  test('normalizes the exact offer key selected from history', () => {
    expect(exactProductOfferKey(product)).toBe('history-offer')
  })
})
