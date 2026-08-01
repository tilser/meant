import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { CartItem, Product } from '../types'
import { CartView } from './CartView'
import type { MerchantCartSnapshot } from './types'

const merchantKey = 'merchant-1'

const product: Product = {
  id: 'grunt-style-shirt',
  name: 'Grunt Style shirt',
  brand: 'Grunt Style',
  category: 'Shirts',
  tone: '#ffffff',
  match: 90,
  priceFrom: 24.99,
  priceCurrency: 'USD',
  listPrice: null,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: 'Test shirt.',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: '' },
  offers: [
    {
      offerKey: 'offer-1',
      merchant: 'Grunt Style',
      price: 24.99,
      priceCurrency: 'USD',
      delivery: 'Calculated at checkout',
      productVariantId: 'variant-1',
      available: true,
    },
  ],
}

function cartItem(qty: number, confirmedQty: number, syncing: boolean): CartItem {
  const confirmedSubtotal = (24.99 * confirmedQty).toFixed(2)
  return {
    id: product.id,
    merchant: 'Grunt Style',
    merchantId: merchantKey,
    qty,
    offerKey: 'offer-1',
    productVariantId: 'variant-1',
    cartId: 'cart-1',
    cartLineId: 'line-1',
    unitPriceAmount: '24.99',
    cartSubtotalAmount: confirmedSubtotal,
    cartTotalAmount: confirmedSubtotal,
    cartCurrency: 'USD',
    syncing,
  }
}

function snapshot(confirmedQty: number): MerchantCartSnapshot {
  const confirmedSubtotal = 24.99 * confirmedQty
  return {
    merchantKey,
    merchant: 'Grunt Style',
    cartId: 'cart-1',
    remoteCartId: null,
    checkoutUrl: null,
    continueUrl: null,
    subtotalAmount: confirmedSubtotal,
    totalAmount: confirmedSubtotal,
    currency: 'USD',
    appliedCodes: [],
  }
}

function renderCart(qty: number, confirmedQty: number, syncing: boolean): string {
  return renderToStaticMarkup(
    <CartView
      cart={[cartItem(qty, confirmedQty, syncing)]}
      products={[product]}
      cartSnapshots={{ [merchantKey]: snapshot(confirmedQty) }}
      deliveryLocations={[]}
      onRemove={() => undefined}
      onQty={() => undefined}
      onAdd={() => undefined}
      onApplyCode={async () => ({ ok: true })}
      onRemoveCode={async () => ({ ok: true })}
      onDeliveryAddress={() => true}
      onDeliveryOption={() => true}
      onCheckout={() => undefined}
      agentBusy={false}
      checkoutMerchantKey={null}
      checkoutError={null}
    />,
  )
}

function expectPendingBreakdown(markup: string): void {
  expect(markup).toContain('Delivery pending')
  expect(markup).toContain('Subtotal <span>Pending</span>')
  expect(markup).toContain('<span class="mt-mono">Merchant total</span><strong>Pending</strong>')
  expect(markup).toContain('<span>Applied savings</span><span>Pending</span>')
  expect(markup).toContain('<span>Delivery</span><span>Pending</span>')
  expect(markup).toContain('<span>Total</span><span>Pending</span>')
  expect(markup).not.toContain('Savings <span>')
}

describe('cart quantity synchronization totals', () => {
  test('keeps totals pending while quantity increases, then shows the confirmed breakdown', () => {
    const pending = renderCart(2, 1, true)
    expectPendingBreakdown(pending)
    expect(pending).not.toContain('Savings <span>-$29.98</span>')
    expect(pending).not.toContain('<span>Delivery</span><span>$4.99</span>')

    const confirmed = renderCart(2, 2, false)
    expect(confirmed).toContain('Subtotal <span>$49.98</span>')
    expect(confirmed).toContain(
      '<span class="mt-mono">Merchant total</span><strong>$49.98</strong>',
    )
    expect(confirmed).toContain('<span>Applied savings</span><span>$0.00</span>')
    expect(confirmed).toContain('<span>Delivery</span><span>Free</span>')
    expect(confirmed).toContain('<span>Total</span><span>$49.98</span>')
    expect(confirmed).not.toContain('Pending')
  })

  test('keeps totals pending while quantity decreases, then shows the confirmed breakdown', () => {
    const pending = renderCart(1, 2, true)
    expectPendingBreakdown(pending)
    expect(pending).not.toContain('<span>Delivery</span><span>$24.99</span>')

    const confirmed = renderCart(1, 1, false)
    expect(confirmed).toContain('Subtotal <span>$24.99</span>')
    expect(confirmed).toContain(
      '<span class="mt-mono">Merchant total</span><strong>$24.99</strong>',
    )
    expect(confirmed).toContain('<span>Applied savings</span><span>$0.00</span>')
    expect(confirmed).toContain('<span>Delivery</span><span>Free</span>')
    expect(confirmed).toContain('<span>Total</span><span>$24.99</span>')
    expect(confirmed).not.toContain('Pending')
  })
})
