import { describe, expect, test } from 'bun:test'

import { agentShelfContext } from './agentShelfContext'
import type { ShelfItem } from './types'

describe('agentShelfContext', () => {
  test('omits an empty Shelf', () => {
    expect(agentShelfContext([])).toBeUndefined()
  })

  test('maps product and message display snapshots in Shelf order', () => {
    const items: ShelfItem[] = [
      {
        uid: 'shelf-product',
        kind: 'product',
        productId: 'product-linen-shirt',
        collapsed: false,
        snapshot: {
          productId: 'product-linen-shirt',
          name: 'Linen shirt',
          brand: 'Meant',
          category: 'Clothing',
          tone: '#fff',
          priceFrom: 49,
          merchants: 2,
        },
      },
      {
        uid: 'shelf-message',
        kind: 'message',
        messageId: 'message-1',
        collapsed: false,
        snapshot: {
          side: 'meant',
          title: 'Meant picks',
          text: 'A few options worth comparing.',
          thumbs: [
            { name: 'Canvas cap', tone: '#eee' },
            { name: 'Mesh cap', tone: '#ddd' },
          ],
        },
      },
    ]

    expect(agentShelfContext(items)).toEqual({
      items: [
        {
          kind: 'PRODUCT',
          canonicalProductKey: 'product-linen-shirt',
          title: 'Linen shirt',
          text: 'Meant · Clothing',
          relatedProductNames: [],
        },
        {
          kind: 'MESSAGE',
          title: 'Meant picks',
          text: 'A few options worth comparing.',
          relatedProductNames: ['Canvas cap', 'Mesh cap'],
        },
      ],
    })
  })

  test('does not put a historical technical seller label into agent context', () => {
    const technicalSeller = 'mcp.shop.example'
    const item: ShelfItem = {
      uid: 'poisoned-shelf-product',
      kind: 'product',
      productId: 'product-trail-shoe',
      collapsed: false,
      snapshot: {
        productId: 'product-trail-shoe',
        name: 'Trail shoe',
        brand: technicalSeller,
        category: 'Running shoes',
        tone: '#fff',
        priceFrom: 119,
        merchants: 1,
      },
    }

    const context = agentShelfContext([item])

    expect(context?.items[0]?.text).toBe('Merchant · Running shoes')
    expect(JSON.stringify(context)).not.toContain(technicalSeller)
  })

  test('uses structured cart facts instead of technical assistant prose', () => {
    const cartId = '48c92e26-4696-46a6-b47b-ed83b7ee7345'
    const item: ShelfItem = {
      uid: 'shelf-cart',
      kind: 'message',
      messageId: 'message-cart',
      collapsed: false,
      snapshot: {
        side: 'meant',
        title: 'Your cart',
        text: '2 items · 2 merchants',
        thumbs: [],
        cart: {
          itemCount: 2,
          merchantCount: 2,
          total: 76,
          priceCurrency: 'USD',
          lines: [
            {
              name: 'Organic cotton fabric',
              merchant: 'cotton.example',
              quantity: 1,
              lineTotal: 28,
              priceCurrency: 'USD',
              tone: '#eee',
            },
            {
              name: 'Linen fabric',
              merchant: 'linen.example',
              quantity: 1,
              lineTotal: 48,
              priceCurrency: 'USD',
              tone: '#ddd',
            },
          ],
        },
      },
    }

    const context = agentShelfContext([item])

    expect(context).toEqual({
      items: [
        {
          kind: 'MESSAGE',
          title: 'Your cart',
          text: '2 cart items across 2 merchants.',
          relatedProductNames: ['Organic cotton fabric', 'Linen fabric'],
        },
      ],
    })
    expect(JSON.stringify(context)).not.toContain(cartId)
  })

  test('does not forward raw IDs from a legacy cart snapshot', () => {
    const cartId = '48c92e26-4696-46a6-b47b-ed83b7ee7345'
    const technicalProductId = 'gid://shopify/Product/123'
    const item: ShelfItem = {
      uid: 'legacy-shelf-cart',
      kind: 'message',
      messageId: 'message-cart-legacy',
      collapsed: false,
      snapshot: {
        side: 'meant',
        title: 'Meant picks',
        text: `You have 1 active cart with items: Cart ID: ${cartId} | Item | Qty`,
        thumbs: [
          { name: 'Organic cotton fabric', tone: '#eee' },
          { name: technicalProductId, tone: '#ddd' },
        ],
      },
    }

    const context = agentShelfContext([item])

    expect(context?.items[0]).toEqual({
      kind: 'MESSAGE',
      title: 'Your cart',
      text: 'Cart saved from chat.',
      relatedProductNames: ['Organic cotton fabric'],
    })
    expect(JSON.stringify(context)).not.toContain(cartId)
    expect(JSON.stringify(context)).not.toContain(technicalProductId)
    expect(JSON.stringify(context)).not.toContain('Cart ID')
  })
})
