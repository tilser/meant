import { describe, expect, test } from 'bun:test'

import type { DirectPurchasePreparation } from '../product/directPurchasePreparation'
import type { Product } from '../types'
import { addChatProductToCart, cartInChatMessage, productOfferAnchorKey } from './chatCartAddition'

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

function ready(offerKey: string): DirectPurchasePreparation {
  return {
    status: 'ready',
    offerKey,
    product: {
      ...product,
      offers: [{ ...product.offers[0]!, offerKey }],
    },
  }
}

describe('chat cart addition', () => {
  test('prepares a current exact selection before binding it to cart', async () => {
    const calls: { product: Product; offerKey: string }[] = []
    const events: string[] = []

    const result = await addChatProductToCart(
      product,
      async () => {
        events.push('prepare')
        return ready('current-exact-offer')
      },
      async (candidate, offerKey) => {
        events.push('add')
        calls.push({ product: candidate, offerKey })
        return true
      },
      () => events.push('show-cart'),
    )

    expect(result).toBe('added')
    expect(calls[0]?.offerKey).toBe('current-exact-offer')
    expect(calls[0]?.product.offers[0]?.offerKey).toBe('current-exact-offer')
    expect(events).toEqual(['prepare', 'show-cart', 'add'])
  })

  test('requires selection without opening or mutating the cart', async () => {
    let cartCalls = 0
    let cartMessages = 0

    const result = await addChatProductToCart(
      product,
      async () => ({ status: 'requires-selection', message: 'Choose a size.' }),
      async () => {
        cartCalls += 1
        return true
      },
      () => {
        cartMessages += 1
      },
    )

    expect(result).toBe('requires-selection')
    expect(cartCalls).toBe(0)
    expect(cartMessages).toBe(0)
  })

  test('reports an unavailable preparation without calling the cart', async () => {
    const result = await addChatProductToCart(
      product,
      async () => ({ status: 'unavailable', message: 'No current offer.' }),
      async () => true,
      () => undefined,
    )

    expect(result).toBe('unavailable')
  })

  test('does not retry an untyped merchant failure', async () => {
    let preparations = 0

    const result = await addChatProductToCart(
      product,
      async () => {
        preparations += 1
        return ready('exact-offer')
      },
      async () => {
        throw new Error('request failed')
      },
      () => undefined,
    )

    expect(result).toBe('failed')
    expect(preparations).toBe(1)
  })

  test('creates a live cart placeholder that the chat row can hydrate from cart state', () => {
    expect(cartInChatMessage('local-cart')).toEqual({
      id: 'local-cart',
      role: 'ai',
      blocks: [{ type: 'cart', lines: [] }],
    })
  })

  test('normalizes a historical offer only as a trusted action anchor', () => {
    expect(productOfferAnchorKey(product)).toBe('history-offer')
  })
})
