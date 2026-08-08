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
    expect(markup).toContain('Your secure checkout is ready.')
    expect(markup).toContain('Open secure checkout')
    expect(markup).toContain('Not just yet')
    expect(markup).not.toContain('Continue securely')
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
    expect(markup).toContain('A short detour — still Meant for you.')
    expect(markup).toContain('Your prepared cart will be waiting there.')
    expect(markup).toContain('Continue securely')
    expect(markup).toContain(
      'aria-label="Continue securely with the Merchant (opens in a new tab)"',
    )
    expect(markup).not.toContain('>https://shop.example/checkout</a>')
  })
})
