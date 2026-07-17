import { afterEach, beforeEach, describe, expect, mock, test } from 'bun:test'

import type { CartItem, Product } from '../types'
import {
  cartItemsWithFallback,
  canRestoreDiscoverThreadAfterConflict,
  cartLineForAddedBlock,
  createDiscoverChatThread,
  deleteStoredDiscoverChatThread,
  discoverProductResearchQuery,
  discoverSuggestedReplies,
  discoverThreadSearchContext,
  durableDiscoverChatThread,
  initialDiscoverChatThreads,
  mergeDiscoverThreadSources,
  normalizeDiscoverChatThreads,
  productOpenWithResearchQuery,
  saveStoredDiscoverChatThreads,
} from './utils'

const originalWindow = globalThis.window
let store: Map<string, string>

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

    expect(initialDiscoverChatThreads()).toEqual([thread])
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

    expect(initialDiscoverChatThreads()).toEqual([thread])
  })

  test('keeps Shopify product facts and media out of durable history', () => {
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
          productContext: product,
          blocks: [
            { type: 'text' as const, text: 'I found matching products.' },
            { type: 'products' as const, products: [product], query: 'running shoes' },
          ],
        },
      ]),
      qualificationId: 'qualification-1',
    }

    const serialized = JSON.stringify(durableDiscoverChatThread(thread))
    expect(serialized).not.toContain('Session-only shoe')
    expect(serialized).not.toContain('cdn.shopify.com')
    expect(serialized).toContain('Product results are available only in the active session')

    saveStoredDiscoverChatThreads([thread], 'user-1')
    const [restored] = initialDiscoverChatThreads('user-1')
    expect(restored?.qualificationId).toBe('qualification-1')
    expect(restored?.messages[0]?.productContext).toBeUndefined()
    expect(restored?.messages[0]?.blocks).toEqual([
      {
        type: 'system',
        text: 'Product results are available only in the active session. Search again to refresh them.',
      },
    ])
  })

  test('removes product-derived text and caller-controlled nested fields', () => {
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

    expect(serialized).not.toContain('$129')
    expect(serialized).not.toContain('hidden product')
    expect(serialized).not.toContain('also hidden')
    expect(serialized).not.toContain('top-level hidden')
    expect(serialized).toContain('Natural materials')
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
