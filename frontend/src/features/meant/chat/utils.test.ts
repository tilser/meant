import { afterEach, beforeEach, describe, expect, test } from 'bun:test'

import type { CartItem, Product } from '../types'
import {
  cartItemsWithFallback,
  cartLineForAddedBlock,
  createDiscoverChatThread,
  deleteStoredDiscoverChatThread,
  initialDiscoverChatThreads,
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
