import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { AuthSheet } from './AuthSheet'

const auth = {
  linkOAuthIdentity: async () => ({ error: null }),
  linkEmailIdentity: async () => ({ error: null, sent: true }),
}

describe('guest account sheet', () => {
  test('uses the playful Meant brand voice for the generic account invitation', () => {
    const markup = renderToStaticMarkup(
      <AuthSheet
        open
        reason="generic"
        auth={auth}
        onClose={() => undefined}
        onExistingAccountSignIn={() => undefined}
      />,
    )

    expect(markup).toContain('You and Meant were meant to be.')
    expect(markup).toContain('Everything Meant for you, always within reach.')
    expect(markup).toContain('Already have a Meant account? Log in')
  })

  test('uses the polished preference-preservation message', () => {
    const markup = renderToStaticMarkup(
      <AuthSheet
        open
        reason="preferences"
        auth={auth}
        onClose={() => undefined}
        onExistingAccountSignIn={() => undefined}
      />,
    )

    expect(markup).toContain('Let Meant remember what makes it yours.')
    expect(markup).toContain('Everything Meant for you, always within reach.')
    expect(markup).toContain('Continue with Google')
    expect(markup).toContain('Continue with Apple')
    expect(markup).toContain('Continue with email')
  })

  test('explains that checkout requires sign-in without losing the cart', () => {
    const markup = renderToStaticMarkup(
      <AuthSheet
        open
        reason="checkout"
        auth={auth}
        onClose={() => undefined}
        onExistingAccountSignIn={() => undefined}
      />,
    )

    expect(markup).toContain('This cart is Meant to be yours.')
    expect(markup).toContain('everything in your cart will wait right here')
  })
})
