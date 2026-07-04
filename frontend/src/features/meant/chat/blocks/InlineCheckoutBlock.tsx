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
  const [placedMerchant, setPlacedMerchant] = useState<string | null>(null)
  const lines = cartLines(cart, products)
  const groups = cartGroups(lines, false)
  const alerts = computeSmartAlerts(lines, products)
  const total = groups.reduce((sum, group) => sum + group.total, 0)

  const payGroup = async (group: (typeof groups)[number]) => {
    setPayingMerchant(group.merchant)
    setPlacedMerchant(null)
    try {
      await onCheckout({
        merchant: group.merchant,
        items: group.items,
        saved: group.itemDiscount,
        savedNote: group.found ? `${group.found.code.code} applied in chat` : 'Checked out in chat',
        checkoutUrl: firstUrl(...group.items.map((item) => item.checkoutUrl)),
        continueUrl: firstUrl(...group.items.map((item) => item.continueUrl)),
      })
      setPlacedMerchant(group.merchant)
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
                {group.found ? (
                  <div className="mt-ct-cocode">
                    <span className="mt-mono">{group.found.code.code}</span>
                    saves {money(group.found.save)}
                  </div>
                ) : null}
                <div className="mt-ct-coframe">
                  <span>Payment</span>
                  <span>Address</span>
                  <span>Delivery</span>
                  <span>Review</span>
                </div>
                <button
                  className="mt-ct-cobtn"
                  type="button"
                  disabled={payingMerchant !== null}
                  onClick={() => void payGroup(group)}
                >
                  {payingMerchant === group.merchant
                    ? 'Placing order'
                    : placedMerchant === group.merchant
                      ? 'Order placed'
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
            {placedMerchant ? `Order placed with ${placedMerchant}.` : 'Your cart is empty.'}
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
