import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Product } from '../types'
import { AgentMarkdown } from './AgentMarkdown'

describe('AgentMarkdown', () => {
  test('renders scannable message structure', () => {
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text={`## Top picks

1. **Dead Cool Jacket** — washed denim
2. **Cloud Puffer** — lightweight insulation

Want me to narrow these by *size* or price?`}
      />,
    )

    expect(markup).toContain('<h2>Top picks</h2>')
    expect(markup).toContain('<ol>')
    expect(markup).toContain('<strong>Dead Cool Jacket</strong>')
    expect(markup).toContain('<em>size</em>')
    expect(markup.match(/<p>/g)).toHaveLength(1)
  })

  test('renders GFM comparison tables as accessible scrollable tables', () => {
    const products = [
      {
        id: 'superman-shirt',
        name: 'Superman 2025 T-Shirt — Midnight Black',
        imageUrl: 'https://cdn.example/superman-shirt.jpg',
      },
      {
        id: 'batman-shirt',
        name: 'Batman & Superman T-Shirt — Washed Black',
        imageUrl: 'https://cdn.example/batman-shirt.jpg',
      },
    ] as Product[]
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text={`| Shirt | Price | Best for |
| --- | ---: | --- |
| **Superman 2025 T-Shirt** — Midnight Black | $27 | Best overall |
| **Batman & Superman T-Shirt** — Washed Black | $17 | Budget pick |`}
        products={products}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup).toContain(
      '<div class="mt-agent-markdown-table" role="region" aria-label="Scrollable table" tabindex="0">',
    )
    expect(markup).toContain('<table>')
    expect(markup).toContain('<thead>')
    expect(markup).toContain('<th>Shirt</th>')
    expect(markup).toContain('<th style="text-align:right">Price</th>')
    expect(markup).toContain('<tbody>')
    expect(markup).toContain('<strong><button class="mt-agent-product-link"')
    expect(markup).toContain('data-product-id="superman-shirt"')
    expect(markup).toContain('data-product-id="batman-shirt"')
    expect(markup).toContain('Superman 2025 T-Shirt — Midnight Black')
    expect(markup).toContain('Batman &amp; Superman T-Shirt — Washed Black')
    expect(markup.match(/class="mt-agent-product-link"/g)).toHaveLength(2)
    expect(markup).not.toContain('| --- |')
  })

  test('does not execute agent-authored HTML or unsafe media and links', () => {
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text={`Safe <script>alert('no')</script>

<img src="https://tracker.example/pixel.gif" onerror="alert('no')">

![tracking pixel](https://tracker.example/pixel.gif)

[bad link](javascript:alert('no')), [relative link](the merchant), and
[transport link](https://seller.myshopify.com/products/jacket)`}
      />,
    )

    expect(markup).not.toContain('<script')
    expect(markup).not.toContain('<img')
    expect(markup).not.toContain('javascript:')
    expect(markup).not.toContain('myshopify.com')
    expect(markup).not.toContain('href=')
  })

  test('opens absolute web links in a separate tab', () => {
    const markup = renderToStaticMarkup(
      <AgentMarkdown text="See the [size guide](https://shop.example/size-guide)." />,
    )

    expect(markup).toContain('href="https://shop.example/size-guide"')
    expect(markup).toContain('target="_blank"')
    expect(markup).toContain('rel="noreferrer"')
  })

  test('renders a grounded product reference as an internal Meant action', () => {
    const product = {
      id: 'shirt-1',
      name: 'Organic cotton tee',
      imageUrl: 'https://cdn.example/organic-cotton-tee.jpg',
      canonicalProduct: { key: 'product_v3_grounded' },
    } as Product
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text="**1. Organic cotton tee** — $21.54"
        products={[product]}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup).toContain('<strong>1. <button class="mt-agent-product-link"')
    expect(markup).toContain('data-product-id="shirt-1"')
    expect(markup).toContain('aria-label="Open Organic cotton tee in Meant"')
    expect(markup).toContain('Organic cotton tee')
    expect(markup).toContain('<span class="mt-agent-product-link-media" aria-hidden="true">')
    expect(markup).toContain('class="mt-agent-product-link-image"')
    expect(markup).toContain('src="https://cdn.example/organic-cotton-tee.jpg"')
    expect(markup).toContain('loading="lazy"')
    expect(markup).toContain('decoding="async"')
    expect(markup).toContain('View product')
    expect(markup).not.toContain('href=')
  })

  test('keeps a grounded product title inside an agent-authored link in Meant', () => {
    const product = {
      id: 'shirt-1',
      name: 'Organic cotton tee',
    } as Product
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text="Try the [Organic cotton tee](https://merchant.example/products/shirt)."
        products={[product]}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup).toContain('<button class="mt-agent-product-link"')
    expect(markup).toContain('data-product-id="shirt-1"')
    expect(markup).toContain('class="mt-agent-product-link-fallback"')
    expect(markup).toContain('>O</span>')
    expect(markup).not.toContain('<img')
    expect(markup).not.toContain('merchant.example')
    expect(markup).not.toContain('href=')
  })

  test('visualizes an unformatted grounded product mention without matching a larger word', () => {
    const product = {
      id: 'shirt-1',
      name: 'Organic cotton tee',
      imageUrl: 'https://cdn.example/organic-cotton-tee.jpg',
    } as Product
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text="The Organic cotton tee is the strongest pick; organic cotton teepee is unrelated."
        products={[product]}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup.match(/class="mt-agent-product-link"/g)).toHaveLength(1)
    expect(markup).toContain('data-product-id="shirt-1"')
    expect(markup).toContain('organic cotton teepee is unrelated')
  })

  test('visualizes a product name split across Markdown in headings, prose, and lists', () => {
    const product = {
      id: 'jacket-1',
      name: 'Alo Yoga Airlift Jacket — Black',
      imageUrl: 'https://cdn.example/alo-jacket.jpg',
    } as Product
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text={`## **Alo Yoga Airlift Jacket** — Black

Try **Alo Yoga Airlift Jacket** — Black for winter.

- [Alo Yoga Airlift Jacket](https://merchant.example/jacket) — Black`}
        products={[product]}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup.match(/class="mt-agent-product-link"/g)).toHaveLength(3)
    expect(markup.match(/data-product-id="jacket-1"/g)).toHaveLength(3)
    expect(markup).toContain('Alo Yoga Airlift Jacket — Black')
    expect(markup).toContain('for winter')
    expect(markup).not.toContain('merchant.example')
    expect(markup).not.toContain('href=')
  })

  test('does not turn a product name inside code into a product action', () => {
    const product = { id: 'shirt-1', name: 'Organic cotton tee' } as Product
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text="Compare `Organic cotton tee` with the Organic cotton tee."
        products={[product]}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup).toContain('<code>Organic cotton tee</code>')
    expect(markup.match(/class="mt-agent-product-link"/g)).toHaveLength(1)
  })

  test('does not activate an ungrounded or ambiguous product title', () => {
    const first = { id: 'shirt-1', name: 'Organic cotton tee' } as Product
    const second = { id: 'shirt-2', name: 'Organic cotton tee' } as Product
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text="Try the **Organic cotton tee** or **Invented tee**."
        products={[first, second]}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup).toContain('<strong>Organic cotton tee</strong>')
    expect(markup).toContain('Invented tee')
    expect(markup).not.toContain('<button')
    expect(markup).not.toContain('href=')
  })

  test('offers progressive disclosure for long product-result messages', () => {
    const text = `## Top picks\n\n${'A useful product detail. '.repeat(24)}\n\nUnique full-message ending.`
    const markup = renderToStaticMarkup(<AgentMarkdown text={text} compact />)

    expect(markup).toContain('is-collapsed')
    expect(markup).toContain('aria-expanded="false"')
    expect(markup).toContain('Show full message')
    expect(markup).toContain('<h2>Top picks</h2>')
    expect(markup).toContain('…')
    expect(markup).not.toContain('Unique full-message ending.')
  })

  test('does not expose links from outside the collapsed preview', () => {
    const text = `${'Useful shopping context. '.repeat(24)}\n\n[Hidden action](https://shop.example/hidden)`
    const markup = renderToStaticMarkup(<AgentMarkdown text={text} compact />)

    expect(markup).toContain('is-collapsed')
    expect(markup).not.toContain('Hidden action')
    expect(markup).not.toContain('href=')
  })

  test('keeps short product-result messages fully visible', () => {
    const markup = renderToStaticMarkup(<AgentMarkdown text="Here are the best matches." compact />)

    expect(markup).not.toContain('is-collapsed')
    expect(markup).not.toContain('Show full message')
  })
})
