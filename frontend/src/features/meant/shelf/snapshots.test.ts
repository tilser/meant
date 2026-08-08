import { describe, expect, test } from 'bun:test'

import type { DiscoverChatMessage } from '../chat/types'
import type { CartItem, Product } from '../types'
import { isLegacyCartShelfSnapshot, shelfMessageSnapshot, shelfProductSnapshot } from './snapshots'
import type { ShelfMessageSnapshot } from './types'

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 'linen-shirt',
    name: 'Linen shirt',
    brand: 'Atelier',
    category: 'Shirts',
    tone: '#d9cfbd',
    imageUrl: 'https://images.example/linen-shirt.jpg',
    match: 94,
    priceFrom: 20,
    priceCurrency: 'USD',
    merchants: 1,
    satisfies: [],
    misses: [],
    note: '',
    pros: [],
    cons: [],
    review: { score: null, count: 0, insight: '' },
    offers: [
      {
        offerKey: 'offer-secret',
        merchant: 'Internal merchant label',
        price: 20,
        priceCurrency: 'USD',
        delivery: 'Arrives in 2–3 days',
      },
    ],
    ...overrides,
  }
}

function cartItem(overrides: Partial<CartItem> = {}): CartItem {
  return {
    id: 'linen-shirt',
    merchant: 'Internal merchant label',
    merchantOrigin: 'shop.example',
    qty: 2,
    offerKey: 'offer-secret',
    cartId: '48c92e26-4696-46a6-b47b-ed83b7ee7345',
    remoteCartId: 'gid://shopify/Cart/private-cart',
    cartLineId: 'private-line-id',
    remoteCartLineId: 'gid://shopify/CartLine/private-line',
    checkoutUrl: 'https://checkout.example/private-cart',
    continueUrl: 'https://shop.example/cart/private-cart',
    productUrl: 'https://shop.example/products/linen-shirt',
    unitPriceAmount: '20',
    lineTotalAmount: '40',
    cartTotalAmount: '45',
    cartCurrency: 'USD',
    ...overrides,
  }
}

describe('Shelf snapshots', () => {
  test('keeps product and ordinary message snapshot behavior', () => {
    const selectedProduct = product()

    expect(shelfProductSnapshot(selectedProduct)).toEqual({
      productId: 'linen-shirt',
      name: 'Linen shirt',
      brand: 'Atelier',
      category: 'Shirts',
      tone: '#d9cfbd',
      priceFrom: 20,
      priceCurrency: 'USD',
      merchants: 1,
      imageUrl: 'https://images.example/linen-shirt.jpg',
    })
    expect(
      shelfMessageSnapshot({
        id: 'message-1',
        role: 'ai',
        text: '**A good match**',
        blocks: [{ type: 'products', products: [selectedProduct] }],
      }),
    ).toEqual({
      side: 'meant',
      title: 'Meant picks',
      text: 'A good match',
      thumbs: [
        {
          name: 'Linen shirt',
          tone: '#d9cfbd',
          imageUrl: 'https://images.example/linen-shirt.jpg',
        },
      ],
    })
  })

  test('prefers a typed cart block and excludes raw prose and internal cart values', () => {
    const privateUuid = '48c92e26-4696-46a6-b47b-ed83b7ee7345'
    const message: DiscoverChatMessage = {
      id: 'cart-message',
      role: 'ai',
      text: `You have 1 active cart with items. Cart ID: ${privateUuid} | Item | Qty |`,
      blocks: [{ type: 'cart', lines: [cartItem()], products: [product()] }],
    }

    const snapshot = shelfMessageSnapshot(message)

    expect(snapshot).toMatchObject({
      side: 'meant',
      title: 'Your cart',
      text: '2 items · 1 merchant',
      cart: {
        itemCount: 2,
        merchantCount: 1,
        total: 45,
        priceCurrency: 'USD',
        lines: [
          {
            name: 'Linen shirt',
            merchant: 'shop.example',
            quantity: 2,
            lineTotal: 40,
            priceCurrency: 'USD',
            tone: '#d9cfbd',
            imageUrl: 'https://images.example/linen-shirt.jpg',
            delivery: 'Arrives in 2–3 days',
          },
        ],
      },
    })
    const json = JSON.stringify(snapshot)
    expect(json).not.toContain(privateUuid)
    expect(json).not.toContain('private-line-id')
    expect(json).not.toContain('offer-secret')
    expect(json).not.toContain('checkout.example')
    expect(json).not.toContain('shop.example/cart/private-cart')
    expect(json).not.toContain('shop.example/products/linen-shirt')
  })

  test('captures optional live cart and product overrides', () => {
    const staleProduct = product({ name: 'Old title', priceFrom: 20 })
    const liveProduct = product({ name: 'Current title', priceFrom: 31 })
    const message: DiscoverChatMessage = {
      id: 'current-cart-message',
      role: 'ai',
      blocks: [
        {
          type: 'cart',
          lines: [cartItem({ qty: 1, unitPriceAmount: '20', lineTotalAmount: '20' })],
          products: [staleProduct],
        },
      ],
    }

    const snapshot = shelfMessageSnapshot(
      message,
      [cartItem({ qty: 3, unitPriceAmount: '31', lineTotalAmount: '93', cartTotalAmount: '93' })],
      [liveProduct],
    )

    expect(snapshot.cart).toMatchObject({
      itemCount: 3,
      total: 93,
      lines: [{ name: 'Current title', quantity: 3, lineTotal: 93 }],
    })
  })

  test('uses buyer-facing fallbacks when a cart product is missing', () => {
    const missingProductId = 'gid://shopify/Product/123'
    const missingMerchantId = '00000000-0000-0000-0000-000000000000'
    const message: DiscoverChatMessage = {
      id: 'fallback-cart-message',
      role: 'ai',
      blocks: [
        {
          type: 'cart',
          lines: [
            cartItem({
              id: missingProductId,
              productTitle: `Product ${missingProductId}`,
              merchant: missingMerchantId,
              merchantOrigin: missingMerchantId,
              merchantId: missingMerchantId,
              offerKey: 'opaque-offer',
              qty: Number.NaN,
              lineTotalAmount: null,
              unitPriceAmount: '12.50',
              cartTotalAmount: null,
            }),
          ],
          products: [],
        },
      ],
    }

    const snapshot = shelfMessageSnapshot(message)

    expect(snapshot.cart).toEqual({
      lines: [
        {
          name: 'Product',
          merchant: 'Merchant',
          quantity: 1,
          lineTotal: 12.5,
          priceCurrency: 'USD',
          tone: '#e7ebef',
          imageUrl: null,
        },
      ],
      itemCount: 1,
      merchantCount: 1,
      total: 12.5,
      priceCurrency: 'USD',
    })
    const json = JSON.stringify(snapshot.cart)
    expect(json).not.toContain(missingProductId)
    expect(json).not.toContain(missingMerchantId)
    expect(json).not.toContain('opaque-offer')
  })

  test('prefers quantity-safe prices and falls back to cart-line artwork', () => {
    const lineArtwork = 'https://images.example/cart-line-linen.jpg'
    const message: DiscoverChatMessage = {
      id: 'optimistic-cart-message',
      role: 'ai',
      blocks: [
        {
          type: 'cart',
          lines: [
            cartItem({
              qty: 2,
              imageUrl: lineArtwork,
              unitPriceAmount: '20',
              lineTotalAmount: '20',
              cartSubtotalAmount: '20',
              cartTotalAmount: '0',
            }),
          ],
          products: [
            product({
              imageUrl: null,
              media: [],
              offers: [
                {
                  offerKey: 'offer-secret',
                  merchant: 'Internal merchant label',
                  price: 20,
                  priceCurrency: 'USD',
                  delivery: 'gid://shopify/DeliveryMethod/123',
                },
              ],
            }),
          ],
        },
      ],
    }

    const snapshot = shelfMessageSnapshot(message)

    expect(snapshot.cart).toMatchObject({
      total: 40,
      priceCurrency: 'USD',
      lines: [
        {
          name: 'Linen shirt',
          quantity: 2,
          lineTotal: 40,
          imageUrl: lineArtwork,
        },
      ],
    })
    expect(snapshot.cart?.lines[0]).not.toHaveProperty('delivery')
    expect(JSON.stringify(snapshot)).not.toContain('gid://shopify/DeliveryMethod/123')
  })
})

describe('isLegacyCartShelfSnapshot', () => {
  const snapshot = (text: string, side: ShelfMessageSnapshot['side'] = 'meant') =>
    ({ side, title: 'Meant', text, thumbs: [] }) satisfies ShelfMessageSnapshot

  test('recognizes structured and narrowly shaped legacy cart entries', () => {
    expect(
      isLegacyCartShelfSnapshot({
        ...snapshot('Safe cart summary'),
        cart: { lines: [], itemCount: 0, merchantCount: 0, total: null, priceCurrency: null },
      }),
    ).toBe(true)
    expect(isLegacyCartShelfSnapshot(snapshot('Cart ID: internal-value'))).toBe(true)
    expect(isLegacyCartShelfSnapshot(snapshot('You have 2 active carts with items:'))).toBe(true)
    expect(isLegacyCartShelfSnapshot(snapshot('Item | Qty | Price'))).toBe(true)
    expect(
      isLegacyCartShelfSnapshot(snapshot('I found two useful baskets for your kitchen.')),
    ).toBe(false)
    expect(isLegacyCartShelfSnapshot(snapshot('Cart ID: internal-value', 'you'))).toBe(false)
  })
})
