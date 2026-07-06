import { useState } from 'react'

import { SparkMark } from '../../shared/ui'
import type { CartItem, CheckoutPayload, Product } from '../../types'
import { cartGroups, cartLines, computeSmartAlerts, firstUrl, money } from '../../utils'

export function InlineCheckoutBlock({
  cart,
  products,
  onCheckout,
  onOpenCart,
  onOpenOrders,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  onOpenCart: () => void
  onOpenOrders: () => void
}>) {
  const [payingMerchant, setPayingMerchant] = useState<string | null>(null)
  const [startedMerchants, setStartedMerchants] = useState<ReadonlySet<string>>(() => new Set())
  const [checkoutError, setCheckoutError] = useState<{ merchant: string; message: string } | null>(
    null,
  )
  const lines = cartLines(cart, products)
  const groups = cartGroups(lines)
  const alerts = computeSmartAlerts(lines, products)
  const total = groups.reduce((sum, group) => sum + group.total, 0)

  const payGroup = async (group: (typeof groups)[number]) => {
    if (startedMerchants.has(group.merchant)) {
      return
    }
    setPayingMerchant(group.merchant)
    setCheckoutError(null)
    try {
      const saved = Math.max(0, group.subtotal + group.delivery - group.total)
      await onCheckout({
        merchant: group.merchant,
        items: group.items,
        saved,
        savedNote: saved > 0 ? 'Merchant-applied savings' : 'Chat checkout',
        checkoutUrl: firstUrl(...group.items.map((item) => item.checkoutUrl)),
        continueUrl: firstUrl(...group.items.map((item) => item.continueUrl)),
      })
      setStartedMerchants((current) => new Set(current).add(group.merchant))
    } catch {
      setCheckoutError({
        merchant: group.merchant,
        message: 'Checkout failed. Please try again.',
      })
    } finally {
      setPayingMerchant(null)
    }
  }

  return (
    <div className="mt-ct-block mt-ct-checkout">
      <div className="mt-ct-block-head">
        <div className="mt-mono mt-ct-block-key">Checkout in chat</div>
        <span className="mt-ct-code-save mt-mono">
          {groups.length} merchant{groups.length === 1 ? '' : 's'}
        </span>
      </div>
      {groups.length > 0 ? (
        <>
          <div className="mt-ct-checkout-total">
            <span>Total ready now</span>
            <strong>{money(total)}</strong>
          </div>
          {alerts.some((alert) => alert.kind === 'warn') ? (
            <div className="mt-ct-checkout-warn">
              Review compatibility warnings before paying. You can still continue from here.
            </div>
          ) : null}
          <div className="mt-ct-checkout-groups">
            {groups.map((group) => (
              <div className="mt-ct-cogroup" key={group.merchant}>
                <div className="mt-ct-cogroup-head">
                  <div>
                    <div className="mt-ct-cogroup-name">{group.merchant}</div>
                    <div className="mt-mono mt-ct-cogroup-meta">
                      {group.items.reduce((sum, line) => sum + line.qty, 0)} items · Delivery{' '}
                      {group.delivery === 0 ? 'free' : money(group.delivery)}
                    </div>
                  </div>
                  <strong>{money(group.total)}</strong>
                </div>
                <div className="mt-ct-coframe">
                  <span>Payment</span>
                  <span>Address</span>
                  <span>Delivery</span>
                  <span>Review</span>
                </div>
                {checkoutError?.merchant === group.merchant && payingMerchant === null ? (
                  <div className="mt-cart-inline-error">{checkoutError.message}</div>
                ) : null}
                <button
                  className="mt-ct-cobtn"
                  type="button"
                  disabled={payingMerchant !== null || startedMerchants.has(group.merchant)}
                  onClick={() => void payGroup(group)}
                >
                  {payingMerchant === group.merchant
                    ? 'Starting checkout'
                    : startedMerchants.has(group.merchant)
                      ? 'Checkout open'
                      : `Pay ${money(group.total)} with Meant`}
                </button>
              </div>
            ))}
          </div>
        </>
      ) : (
        <div className="mt-ct-checkout-empty">
          <SparkMark size={13} />
          <span>
            {startedMerchants.size > 0
              ? `Checkout opened for ${Array.from(startedMerchants).join(', ')}.`
              : 'Your cart is empty.'}
          </span>
          <button className="mt-ct-mini-full" type="button" onClick={onOpenOrders}>
            Open orders
          </button>
        </div>
      )}
      <button className="mt-ct-cart-openfull" type="button" onClick={onOpenCart}>
        Open full cart
      </button>
    </div>
  )
}
