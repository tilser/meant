import { expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { ActiveCheckoutSession } from './checkoutTypes'
import { CheckoutJourney } from './CheckoutJourney'

const session: ActiveCheckoutSession = {
  ownerId: 'user-a',
  cartId: 'cart-1',
  merchant: 'merchant.example',
  merchantOrigin: 'shop.example',
  source: 'cart',
  items: [
    {
      id: 'product-1',
      merchant: 'merchant.example',
      qty: 1,
      productTitle: 'The perfect jacket',
      imageUrl: 'https://images.example/jacket.jpg',
    },
  ],
  saved: 0,
  savedNote: '',
  profile: {
    cartId: 'cart-1',
    remoteCartId: 'remote-cart-1',
    requiresEscalation: false,
    nextAction: 'UPDATE_CHECKOUT',
    selectedRail: 'PROVIDER_CHECKOUT_SESSION',
    ineligibilityReasons: [],
    capabilities: [],
    messages: [],
    nativeCheckoutEnabled: false,
  },
  completion: null,
}

test('turns delivery into a branded, guided Meant match', () => {
  const markup = renderToStaticMarkup(
    <CheckoutJourney
      session={session}
      stage="delivery"
      merchantDisplay="shop.example"
      titleId="checkout-title"
    />,
  )

  expect(markup).toContain('We knew you two were')
  expect(markup).toContain('<em>Meant</em> together.')
  expect(markup).toContain('where should we send the find')
  expect(markup).toContain('aria-current="step"')
  expect(markup).toContain('Delivery')
  expect(markup).toContain('Secure checkout')
  expect(markup).toContain('src="https://images.example/jacket.jpg"')
})

test('moves the story to a secure final step after delivery details are set', () => {
  const markup = renderToStaticMarkup(
    <CheckoutJourney session={session} stage="secure" merchantDisplay="shop.example" compact />,
  )

  expect(markup).toContain('Almost Meant to be')
  expect(markup).toContain('What’s <em>Meant</em> for you is close.')
  expect(markup).toContain('One secure step with shop.example')
  expect(markup).toContain('class="mt-checkout-journey compact"')
})
