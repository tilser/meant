import { describe, expect, test } from 'bun:test'
import type { ReactNode } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Product } from '../types'
import { Shelf } from './Shelf'
import type { ShelfProductSnapshot } from './types'

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

function renderShelf(currentProduct: Product, currentSnapshot = snapshot): string {
  return renderToStaticMarkup(
    <Shelf
      open={true}
      items={[
        {
          uid: 'shelf-scuffers-shirt',
          kind: 'product',
          productId: currentSnapshot.productId,
          collapsed: false,
          snapshot: currentSnapshot,
        },
      ]}
      productsById={new Map([[currentProduct.id, currentProduct]])}
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
