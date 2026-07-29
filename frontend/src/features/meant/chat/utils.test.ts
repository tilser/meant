import { describe, expect, mock, test } from 'bun:test'

import type { CartItem, Product } from '../types'
import type { DiscoverChatMessage } from './types'
import {
  cartItemsForChatBlock,
  cartItemsWithFallback,
  cartLineForAddedBlock,
  discoverChatMessageCopyText,
  discoverProductResearchQuery,
  latestCartBlockMessageId,
  productOpenWithResearchQuery,
} from './utils'

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

describe('discover chat copy', () => {
  test('copies grounded similarity results without a second generic heading', () => {
    const product = historyProduct({ name: 'Black Field Jacket', category: 'Jackets' })
    const message: DiscoverChatMessage = {
      id: 'similarity-result',
      role: 'ai',
      blocks: [
        { type: 'text', text: 'Here are similar jackets to your Black Quilted Jacket:' },
        {
          type: 'similar',
          products: [product],
          similarityAnchor: {
            canonicalProductKey: 'canonical:owned-jacket',
            inventoryItemId: '00000000-0000-0000-0000-000000000402',
            label: 'Black Quilted Jacket',
            query: 'similar jackets',
          },
        },
      ],
    }

    const copied = discoverChatMessageCopyText(message)

    expect(copied).toContain('Here are similar jackets to your Black Quilted Jacket:')
    expect(copied).toContain('Black Field Jacket')
    expect(copied).not.toContain('Similar products:')
  })

  test('copies cart lines with only the trusted merchant origin', () => {
    const message: DiscoverChatMessage = {
      id: 'cart-result',
      role: 'ai',
      blocks: [
        {
          type: 'cart',
          lines: [
            {
              id: 'product-1',
              merchant: 'sollys-online-grocery.myshopify.com',
              merchantOrigin: 'nycfactory.com',
              qty: 1,
              productTitle: 'Trail shoe',
            },
          ],
        },
      ],
    }

    const copied = discoverChatMessageCopyText(message)

    expect(copied).toContain('nycfactory.com')
    expect(copied).not.toContain('sollys-online-grocery.myshopify.com')
  })

  test('fails closed for technical merchant labels in product, watch, and added copy', () => {
    const technicalSeller = 'sollys-online-grocery.myshopify.com'
    const product = historyProduct({
      brand: technicalSeller,
      offers: [{ merchant: technicalSeller, price: 129, delivery: 'Ships in two days' }],
    })
    const message: DiscoverChatMessage = {
      id: 'poisoned-merchant-copy',
      role: 'ai',
      blocks: [
        { type: 'products', products: [product] },
        { type: 'watch', product, merchant: technicalSeller, price: 119 },
        {
          type: 'added',
          product,
          merchant: technicalSeller,
          synced: true,
          price: 119,
          count: 1,
        },
      ],
    }

    const copied = discoverChatMessageCopyText(message)

    expect(copied).toContain('Merchant')
    expect(copied).not.toContain(technicalSeller)
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
    expect(cartItemsForChatBlock([live], [historical], true, true)).toEqual([live])
    expect(cartItemsForChatBlock([], [historical], true, true)).toEqual([])
  })

  test('selects only the newest cart message for live shared-cart rendering', () => {
    const historical = {
      id: 'product-old',
      merchant: 'Historical Merchant',
      qty: 1,
    } satisfies CartItem
    const messages = [
      { id: 'cart-old', role: 'ai', blocks: [{ type: 'cart', lines: [historical] }] },
      { id: 'text-newer', role: 'ai', blocks: [{ type: 'text', text: 'Still working.' }] },
      { id: 'cart-current', role: 'ai', blocks: [{ type: 'cart', lines: [] }] },
      { id: 'user-latest', role: 'you', text: 'Thanks' },
    ] satisfies DiscoverChatMessage[]

    expect(latestCartBlockMessageId(messages)).toBe('cart-current')
    expect(latestCartBlockMessageId(messages.slice(1, 2))).toBeNull()
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
