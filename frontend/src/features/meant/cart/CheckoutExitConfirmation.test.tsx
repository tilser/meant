import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { CheckoutExitConfirmation } from './CheckoutExitConfirmation'

describe('CheckoutExitConfirmation', () => {
  test('uses branded copy and makes the preserved cart explicit', () => {
    const markup = renderToStaticMarkup(
      <CheckoutExitConfirmation
        open
        onKeepOpen={() => undefined}
        onCloseCheckout={() => undefined}
      />,
    )

    expect(markup).toContain('role="alertdialog"')
    expect(markup).toContain('Not <em>Meant</em> to be — just yet?')
    expect(markup).toContain('<em>Meant</em> for later')
    expect(markup).toContain('your items will stay safe in your cart')
    expect(markup).toContain('You can reopen it whenever you’re ready.')
    expect(markup).toContain('Keep checkout open')
    expect(markup).toContain('Close for now')
  })

  test('renders nothing while closed', () => {
    const markup = renderToStaticMarkup(
      <CheckoutExitConfirmation
        open={false}
        onKeepOpen={() => undefined}
        onCloseCheckout={() => undefined}
      />,
    )

    expect(markup).toBe('')
  })
})
