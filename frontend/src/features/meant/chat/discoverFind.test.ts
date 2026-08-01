import { describe, expect, test } from 'bun:test'

import type { Product } from '../types'
import { resolveDiscoverFind } from './discoverFind'
import type { DiscoverChatMessage } from './types'

const product: Product = {
  id: 'running-shorts',
  name: 'Running shorts',
  brand: 'Example',
  category: 'Shorts',
  tone: '#eee',
  match: 90,
  priceFrom: 36,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: '',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  offers: [],
}

const messages: readonly DiscoverChatMessage[] = [
  { id: 'earlier-message', role: 'ai', blocks: [{ type: 'products', products: [product] }] },
  { id: 'source-message', role: 'ai', blocks: [{ type: 'products', products: [product] }] },
]

describe('resolveDiscoverFind', () => {
  test('switches to the shelf item source conversation before looking for its message', () => {
    expect(
      resolveDiscoverFind(
        {
          id: 'find-1',
          kind: 'message',
          conversationId: 'source-conversation',
          messageId: 'source-message',
        },
        'different-conversation',
        messages,
      ),
    ).toEqual({ kind: 'switch-conversation', conversationId: 'source-conversation' })
  })

  test('waits for the target conversation transcript to load', () => {
    expect(
      resolveDiscoverFind(
        {
          id: 'find-2',
          kind: 'message',
          conversationId: 'source-conversation',
          messageId: 'source-message',
        },
        'source-conversation',
        [],
      ),
    ).toEqual({ kind: 'pending' })
  })

  test('finds the exact source message when a product appears more than once', () => {
    expect(
      resolveDiscoverFind(
        {
          id: 'find-3',
          kind: 'product',
          conversationId: 'source-conversation',
          messageId: 'source-message',
          productId: product.id,
        },
        'source-conversation',
        messages,
      ),
    ).toEqual({ kind: 'found', messageId: 'source-message' })
  })

  test('falls back to product lookup for shelf entries saved before message provenance', () => {
    expect(
      resolveDiscoverFind(
        { id: 'find-4', kind: 'product', productId: product.id },
        'source-conversation',
        messages,
      ),
    ).toEqual({ kind: 'found', messageId: 'earlier-message' })
  })
})
