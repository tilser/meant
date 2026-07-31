import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

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
})
