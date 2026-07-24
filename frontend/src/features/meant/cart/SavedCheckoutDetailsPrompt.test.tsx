import { expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { CheckoutProfile } from '../../../lib/apiClient'
import { PRODUCTS } from '../data'
import type { CartItem } from '../types'
import { InlineCheckoutBlock } from '../chat/blocks/InlineCheckoutBlock'
import { CartCheckoutDialog } from './CartCheckoutDialog'
import type { ActiveCheckoutSession } from './checkoutTypes'
import { SavedCheckoutDetailsPrompt } from './SavedCheckoutDetailsPrompt'
import type { SavedCheckoutDetailsProfile } from './savedCheckoutDetails'

test('renders the saved checkout summary and explicit reuse choices', () => {
  const details: SavedCheckoutDetailsProfile = {
    buyer: {
      email: 'ada@example.com',
      firstName: 'Ada',
      lastName: 'Lovelace',
      phoneNumber: '+1 415 555 2671',
    },
    shippingAddress: {
      streetAddress: '1 Market St',
      extendedAddress: null,
      addressLocality: 'San Francisco',
      addressRegion: 'CA',
      postalCode: '94105',
      addressCountry: 'US',
    },
    updatedAt: '2026-07-22T10:00:00Z',
  }
  const markup = renderToStaticMarkup(
    <SavedCheckoutDetailsPrompt
      details={details}
      busy={false}
      onUse={() => undefined}
      onManual={() => undefined}
      onCheckoutAssistant={async () => null}
      onRefresh={() => undefined}
    />,
  )

  expect(markup).toContain('Use your saved contact and delivery details for this checkout?')
  expect(markup).toContain('Ada Lovelace')
  expect(markup).toContain('1 Market St')
  expect(markup).toContain('San Francisco, CA 94105')
  expect(markup).toContain('ada@example.com')
  expect(markup).toContain('Use saved details')
  expect(markup).toContain('Enter different details')
  expect(markup).not.toContain(details.updatedAt)
})

test('offers the same saved-details prompt in cart and chat checkout surfaces', () => {
  const details: SavedCheckoutDetailsProfile = {
    buyer: {
      email: 'ada@example.com',
      firstName: 'Ada',
      lastName: 'Lovelace',
    },
    shippingAddress: {
      streetAddress: '1 Market St',
      addressLocality: 'San Francisco',
      addressRegion: 'CA',
      postalCode: '94105',
      addressCountry: 'US',
    },
    updatedAt: '2026-07-22T10:00:00Z',
  }
  const product = PRODUCTS[0]!
  const items: CartItem[] = [
    {
      id: product.id,
      merchant: 'sollys-online-grocery.myshopify.com',
      merchantOrigin: 'nycfactory.com',
      merchantDomain: 'nycfactory.com',
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
    nextAction: 'UPDATE_CHECKOUT',
    selectedRail: 'PROVIDER_CHECKOUT_SESSION',
    requiresEscalation: true,
    ineligibilityReasons: [],
    capabilities: [],
    messages: [
      {
        code: 'delivery_address_required',
        content: 'A destination address is required.',
      },
    ],
    savedCheckoutDetails: details,
    nativeCheckoutEnabled: false,
  }
  const session: ActiveCheckoutSession = {
    ownerId: 'user-a',
    cartId: 'cart-1',
    merchant: 'sollys-online-grocery.myshopify.com',
    merchantOrigin: 'nycfactory.com',
    source: 'cart',
    items,
    saved: 0,
    savedNote: '',
    profile,
    completion: null,
  }
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
    expect(markup).toContain('nycfactory.com')
    expect(markup).not.toContain('sollys-online-grocery.myshopify.com')
    expect(markup).toContain('Use your saved contact and delivery details for this checkout?')
    expect(markup).toContain('Use saved details')
    expect(markup).toContain('Enter different details')
  }
})
