import { describe, expect, test } from 'bun:test'
import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Product } from '../types'
import { Shelf } from './Shelf'
import type { ShelfItem, ShelfProductSnapshot } from './types'

const snapshot: ShelfProductSnapshot = {
  productId: 'scuffers-shirt',
  name: 'Scuffers shirt',
  brand: 'Scuffers',
  category: 'Shirts',
  tone: '#eee',
  priceFrom: 44.94,
  priceCurrency: 'USD',
  merchants: 1,
}

const product: Product = {
  id: snapshot.productId,
  name: snapshot.name,
  brand: snapshot.brand,
  category: snapshot.category,
  tone: snapshot.tone,
  match: 90,
  priceFrom: null,
  priceCurrency: null,
  merchants: 0,
  satisfies: [],
  misses: [],
  note: '',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  offers: [],
  commercialFactsAuthoritative: false,
}

function DustingContainer({
  children,
  className,
}: Readonly<{
  children: ReactNode
  dusting: boolean
  className?: string
  onGone: () => void
}>) {
  return <div className={className}>{children}</div>
}

function renderShelfItems(
  items: readonly ShelfItem[],
  productsById: ReadonlyMap<string, Product> = new Map(),
): string {
  return renderToStaticMarkup(
    <Shelf
      open={true}
      items={items}
      productsById={productsById}
      DustingContainer={DustingContainer}
      onToggle={() => undefined}
      onAddMessage={() => undefined}
      onAddProduct={() => undefined}
      onRemove={() => undefined}
      onClear={() => undefined}
      onToggleCollapse={() => undefined}
      onFind={() => undefined}
      onFindProduct={() => undefined}
      onOpenProduct={() => undefined}
    />,
  )
}

function renderShelf(currentProduct: Product, currentSnapshot = snapshot): string {
  return renderShelfItems(
    [
      {
        uid: 'shelf-scuffers-shirt',
        kind: 'product',
        productId: currentSnapshot.productId,
        collapsed: false,
        snapshot: currentSnapshot,
      },
    ],
    new Map([[currentProduct.id, currentProduct]]),
  )
}

describe('Shelf product commercial facts', () => {
  test('keeps the last checked snapshot visible while rehydration is pending', () => {
    const markup = renderShelf(product)

    expect(markup).toContain('$44.94')
    expect(markup).toContain('from 1 store · Last checked')
    expect(markup).not.toContain('Price unavailable')
    expect(markup).not.toContain('current store count unavailable')
  })

  test('replaces the snapshot with a successful authoritative refresh', () => {
    const markup = renderShelf({
      ...product,
      priceFrom: 51.25,
      priceCurrency: 'USD',
      merchants: 3,
      commercialFactsAuthoritative: true,
    })

    expect(markup).toContain('$51.25')
    expect(markup).toContain('from 3 stores')
    expect(markup).not.toContain('$44.94')
    expect(markup).not.toContain('Last checked')
  })

  test('still reports genuinely unavailable snapshot facts', () => {
    const markup = renderShelf(product, {
      ...snapshot,
      priceFrom: null,
      priceCurrency: null,
      merchants: 0,
    })

    expect(markup).toContain('Price unavailable')
    expect(markup).toContain('current store count unavailable · Last checked')
  })
})

describe('Shelf cart messages', () => {
  const cartId = '48c92e26-4696-46a6-b47b-ed83b7ee7345'
  const structuredCartItem: ShelfItem = {
    uid: 'shelf-cart',
    kind: 'message',
    messageId: 'message-cart',
    collapsed: false,
    snapshot: {
      side: 'meant',
      title: 'Your cart',
      text: `Cart ID: ${cartId}`,
      thumbs: [],
      cart: {
        itemCount: 3,
        merchantCount: 2,
        total: 84,
        priceCurrency: 'USD',
        lines: [
          {
            name: 'Organic cotton fabric',
            merchant: 'cotton.example',
            quantity: 2,
            lineTotal: 36,
            priceCurrency: 'USD',
            tone: '#eee',
            imageUrl: 'https://images.example/cotton.jpg',
            delivery: 'Arrives Monday',
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

  test('renders a branded read-only cart card without technical prose', () => {
    const markup = renderShelfItems([structuredCartItem])

    expect(markup).toContain('Cart from chat')
    expect(markup).toContain('Your <em>Meant</em> finds')
    expect(markup).toContain('3 items · 2 stores')
    expect(markup).toContain('Organic cotton fabric')
    expect(markup).toContain('cotton.example · Arrives Monday')
    expect(markup).toContain('Qty 2')
    expect(markup).toContain('$36.00')
    expect(markup).toContain('$84.00')
    expect(markup).toContain('https://images.example/cotton.jpg')
    expect(markup).toContain('Find in chat')
    expect(markup).not.toContain(cartId)
    expect(markup).not.toContain('Cart ID')
  })

  test('keeps old stored cart messages useful while hiding their raw IDs', () => {
    const markup = renderShelfItems([
      {
        uid: 'legacy-shelf-cart',
        kind: 'message',
        messageId: 'message-cart-legacy',
        collapsed: false,
        snapshot: {
          side: 'meant',
          title: 'Meant picks',
          text: `You have 1 active cart with items: Cart ID: ${cartId} | Item | Qty`,
          thumbs: [
            {
              name: 'Organic cotton fabric',
              tone: '#eee',
              imageUrl: 'https://images.example/cotton.jpg',
            },
          ],
        },
      },
    ])

    expect(markup).toContain('Cart from chat')
    expect(markup).toContain('Your <em>Meant</em> finds')
    expect(markup).toContain('Saved from chat')
    expect(markup).toContain('Organic cotton fabric')
    expect(markup).not.toContain(cartId)
    expect(markup).not.toContain('Cart ID')
    expect(markup).not.toContain('| Item | Qty')
  })

  test('replaces unsafe legacy thumbnail labels with a neutral cart item name', () => {
    const technicalProductId = 'gid://shopify/Product/123'
    const markup = renderShelfItems([
      {
        uid: 'unsafe-legacy-shelf-cart',
        kind: 'message',
        messageId: 'message-cart-unsafe-legacy',
        collapsed: false,
        snapshot: {
          side: 'meant',
          title: 'Meant picks',
          text: `Cart ID: ${cartId}`,
          thumbs: [{ name: technicalProductId, tone: '#eee' }],
        },
      },
    ])

    expect(markup).toContain('Cart item')
    expect(markup).toContain('Saved from chat')
    expect(markup).not.toContain(technicalProductId)
    expect(markup).not.toContain(cartId)
  })

  test('uses a clean compact summary when a cart card is collapsed', () => {
    const markup = renderShelfItems([{ ...structuredCartItem, collapsed: true }])

    expect(markup).toContain('3 items · 2 stores')
    expect(markup).not.toContain('Organic cotton fabric')
    expect(markup).not.toContain(cartId)
    expect(markup).toContain('Find in chat')
  })
})
