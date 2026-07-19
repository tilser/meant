import { describe, expect, test } from 'bun:test'

import type { Product } from '../types'
import {
  updateVisibleProductContextRegistry,
  visibleProductContextForBatch,
  type VisibleProductContextRegistry,
} from './visibleProductContext'

function product(key: string): Product {
  return {
    id: key,
    name: key,
    brand: 'Meant Test',
    category: 'Test products',
    tone: '#e7ebef',
    match: 90,
    priceFrom: 10,
    priceCurrency: 'USD',
    listPrice: null,
    merchants: 1,
    satisfies: [],
    misses: [],
    note: 'Test product.',
    pros: [],
    cons: [],
    review: { score: null, count: 0, insight: 'No reviews.' },
    offers: [],
    canonicalProduct: { key } as NonNullable<Product['canonicalProduct']>,
  }
}

describe('discover product visible order', () => {
  const products = Array.from({ length: 7 }, (_, index) => product(`product-${index + 1}`))

  test('reports the current desktop page of four in card order', () => {
    expect(
      visibleProductContextForBatch({
        products,
        sourceMessageId: 'message-tool-search',
        isPhone: false,
        page: 0,
        phoneIndex: 0,
      }),
    ).toEqual({
      sourceMessageId: 'message-tool-search',
      orderedCanonicalProductKeys: ['product-1', 'product-2', 'product-3', 'product-4'],
    })
    expect(
      visibleProductContextForBatch({
        products,
        sourceMessageId: 'message-tool-search',
        isPhone: false,
        page: 1,
        phoneIndex: 0,
      }),
    ).toEqual({
      sourceMessageId: 'message-tool-search',
      orderedCanonicalProductKeys: ['product-5', 'product-6', 'product-7'],
    })
  })

  test('reports only the current mobile carousel item', () => {
    expect(
      visibleProductContextForBatch({
        products,
        sourceMessageId: 'message-tool-search',
        isPhone: true,
        page: 0,
        phoneIndex: 2,
      }),
    ).toEqual({
      sourceMessageId: 'message-tool-search',
      orderedCanonicalProductKeys: ['product-3'],
    })
  })

  test('does not reuse stale card order while a newer visible batch is unbindable', () => {
    const registry: VisibleProductContextRegistry = new Map()
    const older = {
      sourceMessageId: 'older-message',
      orderedCanonicalProductKeys: ['product-1', 'product-2'],
    }
    const newer = {
      sourceMessageId: 'newer-message',
      orderedCanonicalProductKeys: ['product-5', 'product-6'],
    }

    expect(
      updateVisibleProductContextRegistry(registry, 'conversation-1', older.sourceMessageId, older),
    ).toEqual(older)
    expect(
      updateVisibleProductContextRegistry(registry, 'conversation-1', newer.sourceMessageId, newer),
    ).toEqual(newer)
    expect(
      updateVisibleProductContextRegistry(registry, 'conversation-1', 'newer-message', null),
    ).toBeNull()
    expect(
      updateVisibleProductContextRegistry(registry, 'conversation-1', 'newer-message', undefined),
    ).toEqual(older)
  })
})
