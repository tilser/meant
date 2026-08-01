import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { OrderProfile } from '../../../lib/apiClient'
import type { CartItem, Product } from '../types'
import { OrdersView } from './OrdersView'
import { orderFromProfile, orderLineTotal, orderLineUnitPrice } from './orderMapping'

const product = {
  id: 'product-a',
  priceFrom: 10,
  priceCurrency: 'USD',
  offers: [
    {
      offerKey: 'offer-small',
      productVariantId: 'variant-small',
      merchant: 'Shared merchant',
      price: 10,
      priceCurrency: 'USD',
      delivery: 'Standard',
    },
    {
      offerKey: 'offer-large',
      productVariantId: 'variant-large',
      merchant: 'Shared merchant',
      price: 18,
      priceCurrency: 'USD',
      delivery: 'Standard',
    },
  ],
} as unknown as Product

function item(overrides: Partial<CartItem>): CartItem {
  return { id: product.id, merchant: 'Shared merchant', qty: 1, ...overrides }
}

describe('order line prices', () => {
  test('prefers remote amounts and exact variant identity over merchant display fallback', () => {
    expect(
      orderLineUnitPrice(item({ offerKey: 'offer-small', unitPriceAmount: '7.50' }), product),
    ).toBe(7.5)
    expect(
      orderLineUnitPrice(item({ offerKey: 'offer-small', lineTotalAmount: '16', qty: 2 }), product),
    ).toBe(8)
    expect(orderLineUnitPrice(item({ offerKey: 'offer-large' }), product)).toBe(18)
    expect(orderLineUnitPrice(item({ productVariantId: 'variant-large' }), product)).toBe(18)
    expect(orderLineUnitPrice(item({ offerKey: 'missing-exact-offer' }), product)).toBe(0)
    expect(orderLineUnitPrice(item({}), product)).toBe(10)
    expect(
      orderLineTotal(item({ offerKey: 'offer-small', lineTotalAmount: '31', qty: 2 }), product),
    ).toBe(31)
  })

  test('renders the official merchant domain instead of technical merchant names', () => {
    const transportIdentity = 'sollys-online-grocery.myshopify.com'
    const profile: OrderProfile = {
      id: 'order-1',
      merchantId: 'merchant-1',
      merchantDomain: 'nycfactory.com',
      merchantName: transportIdentity,
      remoteOrderId: 'remote-order-1',
      displayId: 'ORDER-1',
      orderNumber: '1',
      state: 'CREATED',
      status: 'Confirmed',
      statusNote: 'Order confirmed.',
      date: '2026-07-23',
      totalAmount: '92.00',
      subtotalAmount: '92.00',
      currency: 'USD',
      totalQuantity: 1,
      orderStatusUrl: null,
      lines: [
        {
          id: 'line-1',
          productKey: product.id,
          productId: 'remote-product-1',
          productTitle: 'Trail shoe',
          merchantName: transportIdentity,
          productVariantId: 'variant-1',
          variantTitle: null,
          sku: null,
          imageUrl: null,
          productUrl: null,
          quantity: 1,
          unitAmount: '92.00',
          totalAmount: '92.00',
          currency: 'USD',
        },
      ],
      createdAt: '2026-07-23T10:00:00Z',
      updatedAt: '2026-07-23T10:00:00Z',
    }
    const order = orderFromProfile(profile)
    const markup = renderToStaticMarkup(
      <OrdersView
        orders={[order]}
        products={[
          {
            ...product,
            name: 'Trail shoe',
            brand: transportIdentity,
            category: 'Shoes',
            tone: '#ffffff',
          },
        ]}
        loading={false}
        error={null}
        flashId={null}
        preferences={[]}
        onOpen={() => undefined}
        onReorder={() => undefined}
      />,
    )

    expect(order.items[0]?.merchantOrigin).toBe('nycfactory.com')
    expect(markup).toContain('nycfactory.com')
    expect(markup).not.toContain(transportIdentity)
  })

  test('sanitizes legacy order text and rejects protocol media links', () => {
    const profile = {
      id: 'order-2',
      merchantId: 'merchant-2',
      merchantDomain: 'merchant.example',
      merchantName: 'seller.myshopify.com',
      remoteOrderId: 'remote-order-2',
      displayId: 'ORDER-2',
      orderNumber: '2',
      state: 'PROCESSING',
      status: 'Processing',
      statusNote: 'Track through seller.myshopify.com',
      date: '2026-07-23',
      totalAmount: '10.00',
      subtotalAmount: '10.00',
      currency: 'USD',
      totalQuantity: 1,
      orderStatusUrl: null,
      lines: [
        {
          id: 'line-2',
          productKey: 'unmatched-product',
          productId: 'remote-product-2',
          productTitle: 'Shoe from seller.myshopify.com',
          merchantName: 'seller.myshopify.com',
          productVariantId: 'variant-2',
          variantTitle: 'See /api/mcp',
          sku: null,
          imageUrl: 'https://gateway.example/api/ucp/mcp/image.png',
          productUrl: 'https://gateway.example/.well-known/ucp.json',
          quantity: 1,
          unitAmount: '10.00',
          totalAmount: '10.00',
          currency: 'USD',
        },
      ],
      createdAt: '2026-07-23T10:00:00Z',
      updatedAt: '2026-07-23T10:00:00Z',
    } as OrderProfile

    const order = orderFromProfile(profile)

    expect(order.statusNote).toBe('Track through merchant.example')
    expect(order.items[0]?.productTitle).toBe('Shoe from merchant.example')
    expect(order.items[0]?.variantTitle).toBe('See merchant.example')
    expect(order.items[0]?.imageUrl).toBeNull()
    expect(order.items[0]?.productUrl).toBeNull()
  })
})
