import { describe, expect, test } from 'bun:test'
import { createRef } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import { EmbeddedCheckoutView } from './EmbeddedCheckoutView'

const actions = {
  onOpen: () => undefined,
  onFocus: () => undefined,
  onCancel: () => undefined,
  onPrepare: () => undefined,
  onReconcile: () => undefined,
  onFallback: () => undefined,
}

describe('EmbeddedCheckoutView', () => {
  test('renders an explicit user-gesture action before opening Checkout Kit', () => {
    const markup = renderToStaticMarkup(
      <EmbeddedCheckoutView
        phase="ready"
        message="Secure checkout is ready."
        fallbackUrl={null}
        actionButtonRef={createRef<HTMLButtonElement>()}
        {...actions}
      />,
    )

    expect(markup).toContain('aria-live="polite"')
    expect(markup).toContain('Checkout inside Meant')
    expect(markup).toContain('Open secure checkout')
    expect(markup).toContain('Close checkout')
    expect(markup).not.toContain('Open Merchant checkout')
  })

  test('keeps a validated external fallback in a new browsing context', () => {
    const markup = renderToStaticMarkup(
      <EmbeddedCheckoutView
        phase="fallback"
        message="Continue with the merchant."
        fallbackUrl="https://shop.example/checkout"
        actionButtonRef={createRef<HTMLButtonElement>()}
        {...actions}
      />,
    )

    expect(markup).toContain('target="_blank"')
    expect(markup).toContain('rel="noopener noreferrer"')
    expect(markup).toContain('Checkout inside Meant is not available for this Merchant')
    expect(markup).toContain('Please continue to Merchant checkout to finish your order.')
    expect(markup).toContain('Open Merchant checkout')
    expect(markup).toContain('aria-label="Open Merchant checkout (opens in a new tab)"')
    expect(markup).not.toContain('>https://shop.example/checkout</a>')
  })
})
