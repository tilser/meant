import { expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { CartPopover } from './CartPopover'
import { CartView } from './CartView'
import type { MerchantCartSnapshot } from './types'
import type { CartItem, Product } from '../types'

const transportIdentity = 'sollys-online-grocery.myshopify.com'
const merchantOrigin = 'nycfactory.com'

const product: Product = {
  id: 'product-1',
  name: 'Trail shoe',
  brand: transportIdentity,
  category: 'Shoes',
  tone: '#ffffff',
  match: 90,
  priceFrom: 92,
  priceCurrency: 'USD',
  listPrice: null,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: 'A trail shoe.',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  offers: [
    {
      offerKey: 'offer-1',
      merchant: transportIdentity,
      price: 92,
      priceCurrency: 'USD',
      delivery: 'Calculated at checkout',
      productVariantId: 'variant-1',
      available: true,
    },
  ],
}

const item: CartItem = {
  id: product.id,
  merchant: transportIdentity,
  merchantOrigin,
  merchantId: 'merchant-1',
  merchantDomain: merchantOrigin,
  qty: 1,
  offerKey: 'offer-1',
  productVariantId: 'variant-1',
  cartId: 'cart-1',
  cartLineId: 'line-1',
  unitPriceAmount: '92.00',
}

const snapshot: MerchantCartSnapshot = {
  merchantKey: 'merchant-1',
  merchant: transportIdentity,
  merchantOrigin,
  cartId: 'cart-1',
  remoteCartId: null,
  checkoutUrl: null,
  continueUrl: null,
  subtotalAmount: 92,
  totalAmount: 92,
  currency: 'USD',
  appliedCodes: [],
}

test('renders only the trusted merchant origin across full-cart surfaces', () => {
  const markup = renderToStaticMarkup(
    <>
      <CartPopover
        cart={[item]}
        products={[product]}
        cartSnapshots={{ 'merchant-1': snapshot }}
        mutationBlocked={false}
        onViewFull={() => undefined}
        onClose={() => undefined}
        onRemove={() => undefined}
      />
      <CartView
        cart={[item]}
        products={[product]}
        cartSnapshots={{ 'merchant-1': snapshot }}
        deliveryLocations={[]}
        onRemove={() => undefined}
        onQty={() => undefined}
        onAdd={() => undefined}
        onApplyCode={async () => ({ ok: true })}
        onRemoveCode={async () => ({ ok: true })}
        onCheckout={() => undefined}
        agentBusy={false}
        checkoutMerchantKey={null}
        checkoutError={null}
      />
    </>,
  )

  expect(markup).toContain(merchantOrigin)
  expect(markup).not.toContain(transportIdentity)
})

test('renders a merchant-native cart amount with its own currency instead of the account symbol', () => {
  const euroProduct: Product = {
    ...product,
    name: 'Salthouse T-Shirt Black',
    priceFrom: 32,
    priceCurrency: 'EUR',
    offers: product.offers.map((offer) => ({ ...offer, price: 32, priceCurrency: 'EUR' })),
  }
  const euroItem: CartItem = {
    ...item,
    unitPriceAmount: '32.00',
    cartCurrency: 'EUR',
  }
  const euroSnapshot: MerchantCartSnapshot = {
    ...snapshot,
    subtotalAmount: 32,
    totalAmount: 32,
    currency: 'EUR',
  }
  const markup = renderToStaticMarkup(
    <>
      <CartPopover
        cart={[euroItem]}
        products={[euroProduct]}
        cartSnapshots={{ 'merchant-1': euroSnapshot }}
        mutationBlocked={false}
        onViewFull={() => undefined}
        onClose={() => undefined}
        onRemove={() => undefined}
      />
      <CartView
        cart={[euroItem]}
        products={[euroProduct]}
        cartSnapshots={{ 'merchant-1': euroSnapshot }}
        deliveryLocations={[]}
        onRemove={() => undefined}
        onQty={() => undefined}
        onAdd={() => undefined}
        onApplyCode={async () => ({ ok: true })}
        onRemoveCode={async () => ({ ok: true })}
        onCheckout={() => undefined}
        agentBusy={false}
        checkoutMerchantKey={null}
        checkoutError={null}
      />
    </>,
  )

  expect(markup).toContain('€32.00')
  expect(markup).not.toContain('$32.00')
})
