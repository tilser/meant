import { describe, expect, test } from 'bun:test'

import { agentMarkdownText, plainAgentText } from './agentText'

describe('agent text projections', () => {
  test('strips links whose destinations are neutralized transport coordinates', () => {
    expect(
      agentMarkdownText(
        'Order at [the store](https://seller.myshopify.com/products/x), then see ' +
          '[the size guide](https://official.example/size-guide).',
      ),
    ).toBe('Order at the store, then see [the size guide](https://official.example/size-guide).')
  })

  test('removes Markdown syntax from text-only projections', () => {
    expect(
      plainAgentText('## Top picks\n\n1. **Dead Cool Jacket** — washed denim\n2. *Cloud Puffer*'),
    ).toBe('Top picks\n\n1. Dead Cool Jacket — washed denim\n2. Cloud Puffer')
  })

  test('preserves internal grounded product-link Markdown for the chat renderer', () => {
    expect(agentMarkdownText('[Organic cotton tee](meant:product:product_v3_grounded)')).toBe(
      '[Organic cotton tee](meant:product:product_v3_grounded)',
    )
  })
})
