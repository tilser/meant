import { expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { CheckoutProfile } from '../../../lib/apiClient'
import { InlineCheckoutBlock } from '../chat/blocks/InlineCheckoutBlock'
import type { CartItem, Product } from '../types'
import { CartCheckoutDialog } from './CartCheckoutDialog'
import { MerchantCheckoutHandoff } from './MerchantCheckoutHandoff'
import type { ActiveCheckoutSession } from './checkoutTypes'

const continueUrl = 'https://merchant.example/continue'
const product: Product = {
  id: 'product-1',
  name: 'Test product',
  brand: 'Test brand',
  category: 'Test category',
  tone: '#ffffff',
  match: 90,
  priceFrom: 10,
  priceCurrency: 'USD',
  listPrice: null,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: 'Test product',
  pros: [],
  cons: [],
  review: { score: 4.5, count: 10, insight: 'Well reviewed.' },
  offers: [
    {
      offerKey: 'offer-1',
      merchant: 'Example Shop',
      price: 10,
      priceCurrency: 'USD',
      delivery: 'Delivery estimate available',
      productVariantId: 'variant-1',
      available: true,
    },
  ],
}
const items: CartItem[] = [
  {
    id: product.id,
    merchant: 'Example Shop',
    qty: 1,
    cartId: 'cart-1',
    productVariantId: 'variant-1',
    cartLineId: 'line-1',
    unitPriceAmount: '10.00',
  },
]
const profile: CheckoutProfile = {
  cartId: 'cart-1',
  remoteCartId: 'remote-cart-1',
  status: 'requires_escalation',
  continueUrl,
  nextAction: 'HANDOFF',
  selectedRail: 'MERCHANT_HANDOFF',
  requiresEscalation: true,
  ineligibilityReasons: ['MERCHANT_REDIRECT_REQUIRED', 'FALLBACK_SELECTED'],
  capabilities: [],
  messages: [
    {
      type: 'error',
      code: 'redirect_to_checkout_required',
      severity: 'requires_buyer_input',
      content: 'Cross-border checkout is not supported for this channel.',
    },
  ],
  nativeCheckoutEnabled: false,
}
const session: ActiveCheckoutSession = {
  ownerId: 'user-a',
  cartId: 'cart-1',
  merchant: 'Example Shop',
  source: 'cart',
  items,
  saved: 0,
  savedNote: '',
  profile,
  completion: null,
}

test('renders a compact merchant checkout CTA without exposing the raw URL as copy', () => {
  const markup = renderToStaticMarkup(
    <MerchantCheckoutHandoff session={session} busy={false} onRefresh={() => undefined} />,
  )

  expect(markup).toContain('class="mt-embedded-checkout merchant-handoff"')
  expect(markup).toContain('Checkout inside Meant is not available for this Merchant')
  expect(markup).toContain('Please continue to Merchant checkout to finish your order.')
  expect(markup).toContain('Open Merchant checkout')
  expect(markup).toContain(`href="${continueUrl}"`)
  expect(markup).toContain('target="_blank"')
  expect(markup).toContain('rel="noopener noreferrer"')
  expect(markup).toContain('aria-label="Open Merchant checkout (opens in a new tab)"')
  expect(markup).not.toContain(`>${continueUrl}</a>`)
})

test('does not substitute a generic checkout URL when continue_url is unavailable', () => {
  const checkoutUrl = 'https://merchant.example/generic-checkout'
  const withoutContinueUrl: ActiveCheckoutSession = {
    ...session,
    profile: {
      ...profile,
      continueUrl: undefined,
      checkoutUrl,
    },
  }
  const markup = renderToStaticMarkup(
    <MerchantCheckoutHandoff
      session={withoutContinueUrl}
      busy={false}
      onRefresh={() => undefined}
    />,
  )

  expect(markup).toContain('Get Merchant checkout link')
  expect(markup).not.toContain(`href="${checkoutUrl}"`)
})

test('uses the same merchant handoff panel in cart and chat without entering embedded checkout', () => {
  const cartMarkup = renderToStaticMarkup(
    <CartCheckoutDialog
      session={session}
      busy={false}
      error={null}
      onClose={() => undefined}
      onRefresh={() => undefined}
      onCheckoutAssistant={async () => null}
    />,
  )
  const chatMarkup = renderToStaticMarkup(
    <InlineCheckoutBlock
      threadId="thread-1"
      cart={items}
      products={[product]}
      onCheckout={() => undefined}
      activeCheckout={{ ...session, source: 'chat' }}
      checkoutBusy={false}
      checkoutError={null}
      onCheckoutAssistant={async () => null}
      onRefreshCheckout={() => undefined}
      onOpenCart={() => undefined}
      onOpenOrders={() => undefined}
    />,
  )

  for (const markup of [cartMarkup, chatMarkup]) {
    expect(markup).toContain('Checkout inside Meant is not available for this Merchant')
    expect(markup).toContain('Open Merchant checkout')
    expect(markup).not.toContain('Open secure checkout')
    expect(markup).not.toContain('Tell the checkout agent what to adjust')
  }
})

test('keeps non-redirect merchant handoffs on their existing reason-aware UI', () => {
  const genericSession: ActiveCheckoutSession = {
    ...session,
    profile: {
      ...profile,
      messages: [],
      ineligibilityReasons: ['MISSING_SCOPES', 'FALLBACK_SELECTED'],
    },
  }
  const markup = renderToStaticMarkup(
    <CartCheckoutDialog
      session={genericSession}
      busy={false}
      error={null}
      onClose={() => undefined}
      onRefresh={() => undefined}
      onCheckoutAssistant={async () => null}
    />,
  )

  expect(markup).toContain('Direct completion is not authorized for this checkout')
  expect(markup).toContain('Continue on merchant site')
  expect(markup).not.toContain('not available for this Merchant')
  expect(markup).not.toContain('Open Merchant checkout')
})

test('keeps embedded checkout mounted despite stale merchant redirect metadata', () => {
  const embeddedSession: ActiveCheckoutSession = {
    ...session,
    profile: {
      ...profile,
      nextAction: 'OPEN_EMBEDDED_CHECKOUT',
      selectedRail: 'EMBEDDED_CHECKOUT',
      ineligibilityReasons: ['MERCHANT_REDIRECT_REQUIRED'],
      messages: profile.messages,
    },
  }
  const cartMarkup = renderToStaticMarkup(
    <CartCheckoutDialog
      session={embeddedSession}
      busy={false}
      error={null}
      onClose={() => undefined}
      onRefresh={() => undefined}
      onCheckoutAssistant={async () => null}
    />,
  )
  const chatMarkup = renderToStaticMarkup(
    <InlineCheckoutBlock
      threadId="thread-1"
      cart={items}
      products={[product]}
      onCheckout={() => undefined}
      activeCheckout={{ ...embeddedSession, source: 'chat' }}
      checkoutBusy={false}
      checkoutError={null}
      onCheckoutAssistant={async () => null}
      onRefreshCheckout={() => undefined}
      onOpenCart={() => undefined}
      onOpenOrders={() => undefined}
    />,
  )

  for (const markup of [cartMarkup, chatMarkup]) {
    expect(markup).toContain('Checkout inside Meant')
    expect(markup).not.toContain('not available for this Merchant')
    expect(markup).not.toContain('Open Merchant checkout')
    expect(markup).not.toContain('Continue on merchant site')
    expect(markup).not.toContain(`href="${continueUrl}"`)
  }
})
