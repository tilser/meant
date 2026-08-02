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
      canonicalProduct: { key: 'product_v3_grounded' },
    } as Product
    const markup = renderToStaticMarkup(
      <AgentMarkdown
        text="**1. Organic cotton tee** — $21.54"
        products={[product]}
        onOpenProduct={() => undefined}
      />,
    )

    expect(markup).toContain('<strong><button class="mt-agent-product-link"')
    expect(markup).toContain('data-product-id="shirt-1"')
    expect(markup).toContain('aria-label="Open Organic cotton tee in Meant"')
    expect(markup).toContain('1. Organic cotton tee')
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
    expect(markup).not.toContain('merchant.example')
    expect(markup).not.toContain('href=')
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
