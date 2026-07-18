import { afterEach, beforeEach, describe, expect, mock, test } from 'bun:test'

import type { CartItem, Product } from '../types'
import type { DiscoverChatBlock } from './types'
import {
  cartItemsForChatBlock,
  cartItemsWithFallback,
  canRestoreDiscoverThreadAfterConflict,
  cartLineForAddedBlock,
  createDiscoverChatThread,
  deleteStoredDiscoverChatThread,
  discoverChatThreadPersistenceSnapshot,
  discoverProductResearchQuery,
  discoverSuggestedReplies,
  discoverThreadSearchContext,
  durableDiscoverChatThread,
  initialDiscoverChatThreads,
  mergeDiscoverThreadSources,
  normalizeDiscoverChatThreads,
  normalizedSimilarReferenceBlock,
  rehydratedSimilarBlock,
  productOpenWithResearchQuery,
  saveStoredDiscoverChatThreads,
} from './utils'

const originalWindow = globalThis.window
let store: Map<string, string>

function historyProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 'product-1',
    name: 'Brooks Ghost 14',
    brand: 'Brooks',
    category: 'Running shoes',
    tone: '#ffffff',
    imageUrl: 'https://cdn.shopify.com/volatile-product.jpg',
    match: 91,
    priceFrom: 129,
    merchants: 1,
    satisfies: [],
    misses: [],
    note: 'The strongest fit for the requested cushioning.',
    pros: [],
    cons: [],
    review: { score: 4.7, count: 321, insight: 'Runners consistently praise the cushioning.' },
    offers: [
      {
        offerKey: 'volatile-offer-key',
        merchant: 'Running Store',
        price: 129,
        delivery: 'Ships in two days',
      },
    ],
    ...overrides,
  }
}

beforeEach(() => {
  store = new Map<string, string>()
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {
      localStorage: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => {
          store.set(key, value)
        },
      },
    } as unknown as Window,
  })
})

afterEach(() => {
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: originalWindow,
  })
})

describe('discover chat history storage', () => {
  test('restores locally saved discover chat threads', () => {
    const thread = {
      ...createDiscoverChatThread(
        [
          {
            id: 'message-1',
            role: 'you' as const,
            text: 'running shoes',
          },
        ],
        'running shoes',
      ),
      updatedAt: 123,
    }

    saveStoredDiscoverChatThreads([thread])

    expect(initialDiscoverChatThreads()[0]).toMatchObject(thread)
  })

  test('persists successful Similar history as references without product facts', () => {
    const qualificationId = '00000000-0000-4000-8000-000000000099'
    const anchor = {
      id: 'anchor-key',
      name: 'SENTINEL PRODUCT TITLE',
      imageUrl: 'https://example.test/SENTINEL-IMAGE.jpg',
      priceFrom: 49.99,
      offers: [{ merchant: 'SENTINEL OFFER', price: 49.99 }],
      canonicalProduct: { key: 'anchor-key', title: 'SENTINEL CANONICAL PAYLOAD' },
    } as unknown as Product
    const result = {
      ...anchor,
      id: 'result-key',
      name: 'SENTINEL RESULT TITLE',
      canonicalProduct: { key: 'result-key', title: 'SENTINEL RESULT CANONICAL' },
    } as unknown as Product
    const thread = {
      ...createDiscoverChatThread(
        [
          {
            id: 'similar-request',
            role: 'you' as const,
            text: 'Show me products similar to SENTINEL PRODUCT TITLE.',
            productContext: anchor,
            similarMessageRole: 'request' as const,
            similarSearchStatus: 'requested' as const,
            similarAnchorCanonicalProductKey: 'anchor-key',
          },
          {
            id: 'similar-response',
            role: 'ai' as const,
            query: 'jeans under 50 USD',
            similarMessageRole: 'response' as const,
            similarSearchStatus: 'success' as const,
            similarAnchorCanonicalProductKey: 'anchor-key',
            blocks: [
              { type: 'text' as const, text: 'SENTINEL PRODUCT TITLE result summary' },
              {
                type: 'similar' as const,
                product: anchor,
                products: [result],
                query: 'jeans under 50 USD',
                qualificationId,
                anchorCanonicalProductKey: 'anchor-key',
                resultCanonicalProductKeys: ['result-key'],
              },
            ],
          },
        ],
        'SENTINEL PRODUCT TITLE',
      ),
      autoTitleSource: 'similar-product-search' as const,
    }

    const remote = discoverChatThreadPersistenceSnapshot(thread, false)
    saveStoredDiscoverChatThreads([thread])
    const localJson = Array.from(store.values()).find((value) => value.includes('similar-request'))

    expect(localJson).toBeDefined()
    const remoteEnvelopeJson = JSON.stringify(remote)
    for (const serialized of [localJson ?? '', remote.threadJson, remoteEnvelopeJson]) {
      expect(serialized).toContain('anchor-key')
      expect(serialized).toContain('result-key')
      expect(serialized).toContain('jeans under 50 USD')
      expect(serialized).toContain(qualificationId)
      expect(serialized).toContain('similar-reference')
      expect(serialized).not.toContain('SENTINEL')
      expect(serialized).not.toContain('imageUrl')
      expect(serialized).not.toContain('priceFrom')
      expect(serialized).not.toContain('offers')
      expect(serialized).not.toContain('canonicalProduct')
      expect(serialized).not.toContain('productContext')
    }
    expect(remote.title).toBe('Similar products')
    expect({ ...JSON.parse(localJson ?? '[]')[0], archived: false }).toEqual(
      JSON.parse(remote.threadJson),
    )
  })

  test.each([
    {
      name: 'pending',
      status: 'pending' as const,
      pending: true,
      pendingText: 'Finding products like SENTINEL PRODUCT TITLE',
    },
    {
      name: 'no-query error',
      status: 'error' as const,
      pending: false,
      pendingText: undefined,
    },
  ])('sanitizes $name Similar messages without a rich result block', (scenario) => {
    const product = {
      id: 'anchor-key',
      name: 'SENTINEL PRODUCT TITLE',
      imageUrl: 'https://example.test/SENTINEL-IMAGE.jpg',
      priceFrom: 49.99,
      offers: [{ merchant: 'SENTINEL OFFER', price: 49.99 }],
      canonicalProduct: { key: 'anchor-key', title: 'SENTINEL CANONICAL PAYLOAD' },
    } as unknown as Product
    const thread = {
      ...createDiscoverChatThread(
        [
          {
            id: 'similar-request',
            role: 'you' as const,
            text: 'Show me products similar to SENTINEL PRODUCT TITLE.',
            productContext: product,
            similarMessageRole: 'request' as const,
            similarSearchStatus: 'requested' as const,
            similarAnchorCanonicalProductKey: 'anchor-key',
          },
          {
            id: 'similar-response',
            role: 'ai' as const,
            pending: scenario.pending,
            pendingText: scenario.pendingText,
            pendingOperation: scenario.pending ? ('similar-product-search' as const) : undefined,
            similarMessageRole: 'response' as const,
            similarSearchStatus: scenario.status,
            similarAnchorCanonicalProductKey: 'anchor-key',
            blocks: [{ type: 'text' as const, text: 'SENTINEL PRODUCT TITLE could not be loaded' }],
          },
        ],
        'SENTINEL PRODUCT TITLE',
      ),
      autoTitleSource: 'similar-product-search' as const,
    }

    const serialized = JSON.stringify(discoverChatThreadPersistenceSnapshot(thread, false))

    expect(serialized).toContain('anchor-key')
    expect(serialized).not.toContain('SENTINEL')
    expect(serialized).not.toContain('productContext')
    expect(serialized).not.toContain('imageUrl')
    expect(serialized).not.toContain('priceFrom')
    expect(serialized).not.toContain('offers')
    expect(serialized).not.toContain('canonicalProduct')
  })

  test('rejects malformed Similar references before restoration', () => {
    expect(
      normalizedSimilarReferenceBlock({
        type: 'similar-reference',
        anchorCanonicalProductKey: 'a'.repeat(201),
        resultCanonicalProductKeys: ['result-key'],
        query: 'jeans',
        status: 'idle',
      }),
    ).toBeNull()
    expect(
      normalizedSimilarReferenceBlock({
        type: 'similar-reference',
        anchorCanonicalProductKey: 'anchor-key',
        resultCanonicalProductKeys: ['result-key'],
        query: 'jeans',
        qualificationId: 'not-a-uuid',
        status: 'idle',
      }),
    ).toEqual({
      type: 'similar-reference',
      anchorCanonicalProductKey: 'anchor-key',
      resultCanonicalProductKeys: ['result-key'],
      query: 'jeans',
      qualificationId: undefined,
      status: 'idle',
    })
    expect(
      normalizedSimilarReferenceBlock({
        type: 'similar-reference',
        anchorCanonicalProductKey: 'anchor-key',
        resultCanonicalProductKeys: [],
        query: 'jeans',
        status: 'idle',
      }),
    ).toBeNull()
    expect(
      normalizedSimilarReferenceBlock({
        type: 'similar-reference',
        anchorCanonicalProductKey: 'anchor-key',
        resultCanonicalProductKeys: ['r'.repeat(201)],
        query: 'jeans',
        status: 'idle',
      }),
    ).toBeNull()
    expect(
      normalizedSimilarReferenceBlock({
        type: 'similar-reference',
        anchorCanonicalProductKey: 'anchor-key',
        resultCanonicalProductKeys: Array.from({ length: 21 }, (_, index) => `result-${index}`),
        query: 'jeans',
        status: 'idle',
      }),
    ).toBeNull()
  })

  test('rehydrates Similar products in stored order with an optional unavailable anchor', () => {
    const first = {
      id: 'first-key',
      canonicalProduct: { key: 'first-key' },
    } as Product
    const second = {
      id: 'second-key',
      canonicalProduct: { key: 'second-key' },
    } as Product
    const reference = {
      type: 'similar-reference' as const,
      anchorCanonicalProductKey: 'anchor-key',
      resultCanonicalProductKeys: ['second-key', 'missing-key', 'first-key'],
      query: 'jeans under 50 USD',
      qualificationId: '00000000-0000-4000-8000-000000000099',
      status: 'loading' as const,
    }

    expect(
      rehydratedSimilarBlock(reference, {
        products: [first, second],
        unavailableCanonicalProductKeys: ['anchor-key', 'missing-key'],
      }),
    ).toEqual({
      type: 'similar',
      product: undefined,
      products: [second, first],
      query: 'jeans under 50 USD',
      qualificationId: '00000000-0000-4000-8000-000000000099',
      anchorCanonicalProductKey: 'anchor-key',
      resultCanonicalProductKeys: ['second-key', 'missing-key', 'first-key'],
    })
  })

  test('marks interrupted restored similarity and other pending work as retryable', () => {
    const thread = createDiscoverChatThread(
      [
        {
          id: 'similar-search',
          role: 'ai',
          pending: true,
          pendingText: 'Finding similar products.',
          pendingOperation: 'similar-product-search',
          blocks: [],
        },
        {
          id: 'discount-search',
          role: 'ai',
          pending: true,
          pendingText: 'Finding a discount code.',
          blocks: [],
        },
      ],
      'running shoes',
    )
    saveStoredDiscoverChatThreads([thread])

    const [restoredSimilarity, restoredDiscount] = initialDiscoverChatThreads()[0]?.messages ?? []

    expect(restoredSimilarity).toEqual({
      id: 'similar-search',
      role: 'ai',
      pending: false,
      blocks: [
        {
          type: 'text',
          text: 'This similar-product search was interrupted before it finished. Please try again.',
        },
      ],
    })
    expect(restoredDiscount).toMatchObject({
      id: 'discount-search',
      role: 'ai',
      pending: false,
      blocks: [
        {
          type: 'system',
          text: 'This search was interrupted. Send your last answer again to continue.',
        },
      ],
    })
  })

  test('deletes locally saved discover chat threads', () => {
    const thread = createDiscoverChatThread(
      [
        {
          id: 'message-1',
          role: 'you' as const,
          text: 'coffee',
        },
      ],
      'coffee',
    )
    saveStoredDiscoverChatThreads([thread])

    deleteStoredDiscoverChatThread(thread.id)

    expect(initialDiscoverChatThreads()[0]?.messages).toHaveLength(0)
  })

  test('persists qualification identity and generic suggested replies with the thread', () => {
    const thread = {
      ...createDiscoverChatThread([
        {
          id: 'message-1',
          role: 'ai' as const,
          blocks: [{ type: 'text' as const, text: 'Which details matter?' }],
          suggestedReplies: ['Option one', 'No preference'],
        },
      ]),
      qualificationId: 'qualification-1',
    }

    saveStoredDiscoverChatThreads([thread])

    expect(initialDiscoverChatThreads()[0]).toMatchObject(thread)
  })

  test('keeps only the server result-set reference for durable product history', () => {
    const product = {
      id: 'product-1',
      name: 'Session-only shoe',
      image: 'https://cdn.shopify.com/session-only.jpg',
      price: 129,
    } as unknown as Product
    const thread = {
      ...createDiscoverChatThread([
        {
          id: 'message-1',
          role: 'ai' as const,
          query: 'running shoes',
          blocks: [
            { type: 'text' as const, text: 'I found matching products.' },
            {
              type: 'products' as const,
              products: [product],
              query: 'running shoes',
              productResultSetId: '00000000-0000-4000-8000-000000000101',
            },
          ],
        },
      ]),
      qualificationId: 'qualification-1',
    }

    const serialized = JSON.stringify(durableDiscoverChatThread(thread))
    expect(serialized).not.toContain('Session-only shoe')
    expect(serialized).not.toContain('cdn.shopify.com')
    expect(serialized).toContain('I found matching products.')
    expect(serialized).toContain('00000000-0000-4000-8000-000000000101')

    saveStoredDiscoverChatThreads([thread], 'user-1')
    const [restored] = initialDiscoverChatThreads('user-1')
    expect(restored?.qualificationId).toBe('qualification-1')
    expect(restored?.messages[0]?.blocks).toEqual([
      {
        type: 'text',
        text: 'I found matching products.',
      },
      {
        type: 'products',
        products: [],
        productResultSetId: '00000000-0000-4000-8000-000000000101',
        query: 'running shoes',
      },
    ])
  })

  test('preserves a legacy product block when no server result-set reference exists', () => {
    const product = { id: 'legacy-product', name: 'Legacy product' } as unknown as Product
    const thread = createDiscoverChatThread([
      {
        id: 'message-1',
        role: 'ai',
        blocks: [
          { type: 'text', text: 'Legacy result text' },
          { type: 'products', products: [product], query: 'legacy query' },
        ],
      },
    ])

    expect(durableDiscoverChatThread(thread).messages[0]?.blocks).toEqual(
      thread.messages[0]?.blocks,
    )
  })

  test('preserves the product snapshot when its result-set reference is malformed', () => {
    const product = { id: 'product-1', name: 'Product' } as unknown as Product
    const thread = createDiscoverChatThread([
      {
        id: 'message-1',
        role: 'ai',
        blocks: [
          {
            type: 'products',
            products: [product],
            productResultSetId: 'not-a-server-uuid',
          },
        ],
      },
    ])

    expect(durableDiscoverChatThread(thread).messages[0]?.blocks).toEqual(
      thread.messages[0]?.blocks,
    )
  })

  test('keeps malformed legacy product history loadable when products are missing', () => {
    const thread = createDiscoverChatThread([
      {
        id: 'legacy-result',
        role: 'ai',
        blocks: [{ type: 'products', query: 'legacy query' } as unknown as DiscoverChatBlock],
      },
    ])

    expect(durableDiscoverChatThread(thread).messages[0]?.blocks).toEqual([
      { type: 'products', query: 'legacy query', products: [] },
    ])
  })

  test('replaces malformed legacy attachments without a type instead of crashing history', () => {
    const thread = createDiscoverChatThread([
      {
        id: 'legacy-answer',
        role: 'ai',
        blocks: [null, { legacy: true }] as unknown as DiscoverChatBlock[],
      },
    ])

    expect(durableDiscoverChatThread(thread).messages[0]?.blocks).toEqual([
      {
        type: 'system',
        text: 'This attachment is unavailable in conversation history.',
      },
      {
        type: 'system',
        text: 'This attachment is unavailable in conversation history.',
      },
    ])
  })

  test('preserves session-only transcript text while removing caller-controlled nested fields', () => {
    const thread = {
      ...createDiscoverChatThread(),
      messages: [
        {
          id: 'answer-1',
          role: 'ai' as const,
          sessionOnly: true,
          blocks: [{ type: 'text' as const, text: '$129 at a Shopify merchant' }],
        },
        {
          id: 'preferences-1',
          role: 'ai' as const,
          blocks: [
            {
              type: 'prefs' as const,
              preferences: [{ id: 'natural', label: 'Natural', desc: 'Natural materials' }],
              products: [{ name: 'hidden product' }],
            },
          ],
          sessionProducts: [{ name: 'also hidden' }],
        },
      ],
      products: [{ name: 'top-level hidden' }],
    } as unknown as ReturnType<typeof createDiscoverChatThread>

    const serialized = JSON.stringify(durableDiscoverChatThread(thread))

    expect(serialized).toContain('$129 at a Shopify merchant')
    expect(serialized).not.toContain('sessionOnly')
    expect(serialized).not.toContain('hidden product')
    expect(serialized).not.toContain('also hidden')
    expect(serialized).not.toContain('top-level hidden')
    expect(serialized).toContain('Natural materials')
  })

  test('stores the original structured review block', () => {
    const product = historyProduct()
    const thread = createDiscoverChatThread([
      {
        id: 'review-answer',
        role: 'ai',
        blocks: [
          { type: 'text', text: `Here is what reviewers say about ${product.name}.` },
          { type: 'reviews', product },
        ],
      },
    ])

    const durable = durableDiscoverChatThread(thread)
    const serialized = JSON.stringify(durable)

    expect(durable.messages[0]?.blocks).toEqual(thread.messages[0]?.blocks)
    expect(serialized).toContain('"type":"reviews"')
    expect(serialized).toContain('cdn.shopify.com')
    expect(serialized).toContain('volatile-offer-key')
    expect(serialized).toContain('"product":')
  })

  test('stores the original comparison table and decision blocks', () => {
    const first = historyProduct()
    const second = historyProduct({
      id: 'product-2',
      name: 'Nike Pegasus 41',
      priceFrom: 139,
      match: 86,
      note: 'A firmer alternative.',
      review: { score: 4.5, count: 210, insight: 'Reviewers like its responsive ride.' },
    })
    const thread = createDiscoverChatThread([
      {
        id: 'compare-answer',
        role: 'ai',
        blocks: [
          { type: 'text', text: 'I lined them up here.' },
          {
            type: 'minicompare',
            products: [first, second],
            rows: [
              { label: 'Match', values: ['91%', '86%'], winnerIndex: 0 },
              { label: 'Reviews', values: ['4.7 · 321', '4.5 · 210'], winnerIndex: 0 },
            ],
            pickIndex: 0,
          },
        ],
      },
      {
        id: 'decision-answer',
        role: 'ai',
        blocks: [{ type: 'decision', product: first, runnerUp: second }],
      },
    ])

    const durable = durableDiscoverChatThread(thread)
    const serialized = JSON.stringify(durable)

    expect(durable.messages).toEqual(thread.messages)
    expect(durable.messages[0]?.blocks?.[1]?.type).toBe('minicompare')
    expect(durable.messages[1]?.blocks?.[0]?.type).toBe('decision')
    expect(serialized).toContain('"rows"')
    expect(serialized).toContain('cdn.shopify.com')
    expect(serialized).toContain('volatile-offer-key')
    expect(serialized).toContain('"products":[')
  })

  test('keeps user text, focus, and structured product context', () => {
    const product = historyProduct()
    const thread = {
      ...createDiscoverChatThread([
        {
          id: 'question-1',
          role: 'you' as const,
          text: `What do reviewers say about ${product.name}?`,
          productContext: product,
        },
      ]),
      focusProductId: product.id,
    }

    const durable = durableDiscoverChatThread(thread)
    const serialized = JSON.stringify(durable)

    expect(durable.focusProductId).toBe(product.id)
    expect(durable.messages[0]?.productContext).toEqual(product)
    expect(serialized).toContain('productContext')
    expect(serialized).toContain('cdn.shopify.com')
  })

  test('round-trips a cart block as the original structured chat UI', () => {
    const product = historyProduct()
    const cartLine: CartItem = {
      id: product.id,
      merchant: 'Running Store',
      qty: 2,
      offerKey: 'offer-1',
      variantTitle: 'Black / 10',
      productTitle: product.name,
      lineTotalAmount: '258.00',
      cartCurrency: 'USD',
    }
    const thread = createDiscoverChatThread([
      {
        id: 'cart-answer',
        role: 'ai',
        blocks: [
          { type: 'text', text: 'Here is your cart.' },
          { type: 'cart', lines: [cartLine], products: [product] },
        ],
      },
    ])

    saveStoredDiscoverChatThreads([thread], 'user-1')
    const [restored] = initialDiscoverChatThreads('user-1')

    expect(restored?.messages[0]?.blocks).toEqual(thread.messages[0]?.blocks)
    expect(restored?.messages[0]?.blocks?.[1]?.type).toBe('cart')
  })

  test('purges pre-account shared history instead of migrating Shopify facts', () => {
    store.set(
      'meant.discoverChatThreads',
      JSON.stringify([{ products: [{ name: 'legacy Shopify result' }] }]),
    )
    store.set(
      'meant.discoverChatMessages',
      JSON.stringify([{ productContext: { name: 'legacy Shopify result' } }]),
    )

    initialDiscoverChatThreads('user-1')

    expect(store.get('meant.discoverChatThreads')).toBe('[]')
    expect(store.get('meant.discoverChatMessages')).toBe('[]')
  })

  test('never lets a newer-timestamp stale local revision replace newer server history', () => {
    const remote = {
      ...createDiscoverChatThread([{ id: 'remote', role: 'you', text: 'new server turn' }]),
      id: '00000000-0000-4000-8000-000000000001',
      persistedRevision: 3,
      updatedAt: 100,
    }
    const staleLocal = {
      ...remote,
      messages: [{ id: 'local', role: 'you' as const, text: 'stale local turn' }],
      persistedRevision: 2,
      updatedAt: 999,
    }
    const validUnsavedLocal = {
      ...staleLocal,
      persistedRevision: 3,
    }

    expect(mergeDiscoverThreadSources([remote], [staleLocal])[0]?.messages[0]?.text).toBe(
      'new server turn',
    )
    expect(mergeDiscoverThreadSources([remote], [validUnsavedLocal])[0]?.messages[0]?.text).toBe(
      'stale local turn',
    )
  })

  test('does not restore a remote conflict snapshot after another local edit', () => {
    const thread = { ...createDiscoverChatThread(), updatedAt: 20 }

    expect(canRestoreDiscoverThreadAfterConflict(thread, 20)).toBeTrue()
    expect(canRestoreDiscoverThreadAfterConflict({ ...thread, updatedAt: 21 }, 20)).toBeFalse()
    expect(canRestoreDiscoverThreadAfterConflict(undefined, 20)).toBeFalse()
  })
})

describe('qualification replies', () => {
  test('restores interrupted pending turns as retryable messages', () => {
    const [restored] = normalizeDiscoverChatThreads([
      {
        ...createDiscoverChatThread(),
        messages: [
          {
            id: 'pending-1',
            role: 'ai',
            pending: true,
            pendingText: 'Searching',
            blocks: [],
          },
        ],
      },
    ])

    expect(restored?.messages[0]).toMatchObject({
      pending: false,
      blocks: [{ type: 'system' }],
    })
  })

  test('shows sanitized replies only for the latest completed assistant question', () => {
    const assistantQuestion = {
      id: 'message-1',
      role: 'ai' as const,
      suggestedReplies: [' Black ', 'Black', '', 'No preference'],
    }

    expect(discoverSuggestedReplies([assistantQuestion])).toEqual(['Black', 'No preference'])
    expect(
      discoverSuggestedReplies([
        assistantQuestion,
        { id: 'message-2', role: 'you', text: 'Black' },
      ]),
    ).toEqual([])
    expect(discoverSuggestedReplies([{ ...assistantQuestion, pending: true }])).toEqual([])
  })

  test('keeps search context isolated to the selected conversation', () => {
    const productA = { id: 'product-a' } as Product
    const productB = { id: 'product-b' } as Product
    const threadA = {
      ...createDiscoverChatThread(),
      messages: [
        {
          id: 'answer-a',
          role: 'ai' as const,
          query: 'running shoes',
          blocks: [{ type: 'products' as const, products: [productA] }],
        },
      ],
    }
    const threadB = {
      ...createDiscoverChatThread(),
      messages: [
        {
          id: 'answer-b',
          role: 'ai' as const,
          query: 'coffee grinder',
          blocks: [{ type: 'products' as const, products: [productB] }],
        },
      ],
    }

    expect(discoverThreadSearchContext(threadA)).toEqual({
      query: 'running shoes',
      products: [productA],
    })
    expect(discoverThreadSearchContext(threadB)).toEqual({
      query: 'coffee grinder',
      products: [productB],
    })
  })

  test('uses a pending search query without leaking products from the previous turn', () => {
    const priorProduct = { id: 'prior-product' } as Product
    const thread = {
      ...createDiscoverChatThread(),
      messages: [
        {
          id: 'prior-answer',
          role: 'ai' as const,
          query: 'running shoes',
          blocks: [{ type: 'products' as const, products: [priorProduct] }],
        },
        {
          id: 'pending-answer',
          role: 'ai' as const,
          query: 'coffee grinder',
          pending: true,
          blocks: [],
        },
      ],
    }

    expect(discoverThreadSearchContext(thread)).toEqual({ query: 'coffee grinder', products: [] })
  })
})

describe('historical product context', () => {
  test('prefers the products-block query and falls back to the message query', () => {
    const product = { id: 'product-1' } as Product

    expect(
      discoverProductResearchQuery(
        { type: 'products', products: [product], query: '  original search  ' },
        'message search',
      ),
    ).toBe('original search')
    expect(
      discoverProductResearchQuery(
        { type: 'products', products: [product], query: '   ' },
        '  message search  ',
      ),
    ).toBe('message search')
    expect(
      discoverProductResearchQuery(
        {
          type: 'similar',
          product,
          products: [product],
          query: '  original similarity constraints  ',
        },
        'newer message query',
      ),
    ).toBe('original similarity constraints')
    expect(discoverProductResearchQuery({ type: 'reviews', product }, '   ')).toBeNull()
  })

  test('passes the saved query when a historical product is opened', () => {
    const product = { id: 'product-1' } as Product
    const products = [product]
    const onOpen = mock(() => undefined)

    productOpenWithResearchQuery(onOpen, '  original search  ')(product, products)

    expect(onOpen).toHaveBeenCalledWith(product, products, 'original search')
  })
})

describe('chat cart snapshots', () => {
  test('keeps an immutable agent cart message pinned to its artifact lines', () => {
    const historical = {
      id: 'product-old',
      merchant: 'Historical Merchant',
      qty: 1,
      offerKey: 'offer-old',
      cartLineId: 'line-old',
    } satisfies CartItem
    const live = {
      id: 'product-new',
      merchant: 'Current Merchant',
      qty: 2,
      offerKey: 'offer-new',
      cartLineId: 'line-new',
    } satisfies CartItem

    expect(cartItemsForChatBlock([live], [historical], true)).toEqual([historical])
    expect(cartItemsForChatBlock([live], undefined, true)).toEqual([live])
  })

  test('deduplicates a stale fallback by exact offer while retaining sibling variants', () => {
    const live = {
      id: 'product-1',
      merchant: 'Merchant',
      qty: 1,
      offerKey: 'offer-medium',
      cartLineId: 'line-medium',
    } satisfies CartItem
    const staleSameOffer = { ...live, cartLineId: null }
    const sibling = { ...live, offerKey: 'offer-large', cartLineId: null }

    expect(cartItemsWithFallback([live], [staleSameOffer, sibling])).toEqual([live, sibling])
  })

  test('resolves added messages by exact offer and uses merchant fallback only for legacy blocks', () => {
    const sibling = {
      id: 'product-1',
      merchant: 'Merchant',
      qty: 1,
      offerKey: 'offer-large',
      cartLineId: 'line-large',
    } satisfies CartItem
    const exact = { ...sibling, offerKey: 'offer-medium', cartLineId: 'line-medium' }
    const product = { id: 'product-1' } as Product

    expect(
      cartLineForAddedBlock([sibling], {
        type: 'added',
        product,
        merchant: 'Merchant',
        synced: true,
        offerKey: 'offer-medium',
      }),
    ).toBeUndefined()
    expect(
      cartLineForAddedBlock([sibling, exact], {
        type: 'added',
        product,
        merchant: 'Merchant',
        synced: true,
        offerKey: 'offer-medium',
      }),
    ).toBe(exact)
    expect(
      cartLineForAddedBlock([sibling], {
        type: 'added',
        product,
        merchant: 'Merchant',
        synced: true,
      }),
    ).toBe(sibling)
  })
})
