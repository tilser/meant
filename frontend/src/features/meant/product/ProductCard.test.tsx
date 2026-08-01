import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Preference, Product } from '../types'
import { ProductCard } from './ProductCard'

const preferences: Preference[] = [
  { id: 'organic', label: 'Organic', desc: '' },
  { id: 'natural', label: 'Natural materials', desc: '' },
  { id: 'local', label: 'Locally made', desc: '' },
  { id: 'repairable', label: 'Repairable', desc: '' },
  { id: 'no-polyester', label: 'No polyester', desc: '', polarity: 'avoid' },
  { id: 'highly-rated', label: 'Strong reviews', desc: '' },
]

const product: Product = {
  id: 'product-with-many-tags',
  name: 'Product with every tag',
  brand: 'Meant Test',
  category: 'Product',
  tone: '#eee',
  match: 91,
  priceFrom: 35,
  merchants: 1,
  satisfies: ['organic', 'natural', 'local', 'repairable'],
  misses: [],
  note: 'A strong preference match.',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  certifications: ['Fair Trade', 'GOTS'],
  materials: ['Organic cotton'],
  collections: ['Essentials'],
  offers: [],
}

describe('ProductCard tag rails', () => {
  test('renders every preference and product detail tag without a fixed item limit', () => {
    const markup = renderToStaticMarkup(
      <ProductCard
        product={product}
        index={0}
        deliveryLocations={[]}
        preferences={preferences}
        onOpen={() => undefined}
        savedSet={new Set()}
        savePendingSet={new Set()}
        onToggleSave={() => undefined}
      />,
    )

    expect(markup).toContain('aria-label="Preference tags"')
    expect(markup).toContain('Organic')
    expect(markup).toContain('Natural materials')
    expect(markup).toContain('Locally made')
    expect(markup).toContain('Repairable')
    expect(markup).toContain('aria-label="Product detail tags"')
    expect(markup).toContain('Fair Trade')
    expect(markup).toContain('GOTS')
    expect(markup).toContain('Organic cotton')
    expect(markup).toContain('Essentials')
  })

  test('does not render Shopify or generic MCP transport identities as a product brand', () => {
    for (const technicalSeller of ['sollys-online-grocery.myshopify.com', 'mcp.shop.example']) {
      const markup = renderToStaticMarkup(
        <ProductCard
          product={{ ...product, brand: technicalSeller }}
          index={0}
          deliveryLocations={[]}
          preferences={preferences}
          onOpen={() => undefined}
          savedSet={new Set()}
          savePendingSet={new Set()}
          onToggleSave={() => undefined}
        />,
      )

      expect(markup).toContain('>Merchant<')
      expect(markup).not.toContain(technicalSeller)
    }
  })

  test('renders unknown preference evidence and does not claim there are no trade-offs', () => {
    const markup = renderToStaticMarkup(
      <ProductCard
        product={{
          ...product,
          satisfies: [],
          misses: [],
          unknowns: ['no-polyester', 'highly-rated'],
          hardConstraints: ['no-polyester'],
          note: 'Unknown: No polyester and Strong reviews. Hard constraints: No polyester (unknown).',
        }}
        index={0}
        deliveryLocations={[]}
        preferences={preferences}
        onOpen={() => undefined}
        savedSet={new Set()}
        savePendingSet={new Set()}
        onToggleSave={() => undefined}
      />,
    )

    expect(markup).toContain('Hard constraint unknown: No polyester')
    expect(markup).toContain('Unknown: Strong reviews')
    expect(markup).toContain(
      'Unknown: No polyester and Strong reviews. Hard constraints: No polyester (unknown).',
    )
    expect(markup.toLowerCase()).not.toContain('no preference trade-offs')
  })
})
