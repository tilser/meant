import { useEffect, useRef } from 'react'

import { CartIcon, CloseIcon, ProductArtwork, SparkMark } from '../shared/ui'
import type { CartItem, Product, ProductId } from '../types'
import { cartGroups, cartItemIdentity, cartLines, computeSmartAlerts, money } from '../utils'
import { merchantDisplayOrigin } from './merchantOrigin'
import type { MerchantCartSnapshot } from './types'
import { cartSnapshotSavings, cartSnapshotSubtotal, cartSnapshotTotal } from './utils'

export function CartPopover({
  cart,
  products,
  cartSnapshots,
  mutationBlocked,
  onViewFull,
  onClose,
  onRemove,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  cartSnapshots: Readonly<Record<string, MerchantCartSnapshot>>
  mutationBlocked: boolean
  onViewFull: () => void
  onClose: () => void
  onRemove: (id: ProductId, merchant: string, identity?: string) => void
}>) {
  const ref = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    const onDown = (event: MouseEvent) => {
      const target = event.target as Node
      // Ignore clicks on the cart button itself (it shares the anchor with the
      // popover). Otherwise this would close the popover and the button's own
      // onClick would immediately re-open it, so it could never be toggled shut.
      const anchor = ref.current?.closest('.mt-cart-anchor')
      if (anchor && anchor.contains(target)) {
        return
      }
      if (ref.current && !ref.current.contains(target)) {
        onClose()
      }
    }
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  const lines = cartLines(cart, products)

  if (lines.length === 0) {
    return (
      <div className="mt-cart-pop" ref={ref}>
        <div className="mt-cart-pop-head">
          <div className="mt-cart-pop-title">
            <SparkMark size={15} /> Smart cart
          </div>
        </div>
        <div className="mt-cart-pop-empty">
          <div className="mt-cart-pop-empty-mark">
            <CartIcon />
          </div>
          <div className="mt-cart-pop-empty-title">Your cart is empty</div>
          <div className="mt-cart-pop-empty-sub">
            Add products and Meant keeps merchant totals in sync.
          </div>
        </div>
      </div>
    )
  }

  const alerts = computeSmartAlerts(lines, products)
  const warnCount = alerts.filter((alert) => alert.kind === 'warn').length
  const groups = cartGroups(lines)
  const groupSummaries = groups.map((group) => {
    const snapshot = cartSnapshots[group.merchantKey]
    const fallbackTotal = group.subtotal + group.delivery
    return {
      group,
      snapshot,
      subtotal: cartSnapshotSubtotal(snapshot, group.subtotal),
      savings: cartSnapshotSavings(snapshot, group.subtotal, fallbackTotal),
      total: cartSnapshotTotal(snapshot, fallbackTotal),
    }
  })
  const discountTotal = groupSummaries.reduce((sum, summary) => sum + summary.savings, 0)
  const codeCount = groupSummaries.reduce(
    (sum, summary) => sum + (summary.snapshot?.appliedCodes.length ?? 0),
    0,
  )
  const grandTotal = groupSummaries.reduce((sum, summary) => sum + summary.total, 0)
  const itemCount = lines.reduce((sum, line) => sum + line.qty, 0)

  return (
    <div className="mt-cart-pop" ref={ref}>
      <div className="mt-cart-pop-head">
        <div className="mt-cart-pop-title">
          <SparkMark size={15} /> Smart cart
        </div>
        <span className="mt-mono mt-cart-pop-count">
          {itemCount} items · {groups.length} merchants
        </span>
      </div>
      <div className="mt-cart-pop-signals">
        <div className={`mt-cart-sig ${warnCount > 0 ? 'mt-cart-sig-warn' : 'mt-cart-sig-good'}`}>
          {warnCount > 0
            ? `${warnCount} issue${warnCount === 1 ? '' : 's'} to review`
            : 'All compatible'}
        </div>
        <div className={`mt-cart-sig ${codeCount > 0 ? 'mt-cart-sig-good' : 'mt-cart-sig-muted'}`}>
          <SparkMark size={13} />
          {codeCount > 0 ? `${codeCount} applied · -${money(discountTotal)}` : 'No applied codes'}
        </div>
      </div>
      <div className="mt-cart-pop-list">
        {lines.map((line) => (
          <div className="mt-cart-pop-item" key={cartItemIdentity(line)}>
            <div className="mt-cart-pop-media">
              <ProductArtwork product={line.product} label={line.product.category.toLowerCase()} />
            </div>
            <div className="mt-cart-pop-info">
              <div className="mt-cart-pop-name">{line.product.name}</div>
              <div className="mt-mono mt-cart-pop-meta">
                {line.qty} × {money(line.price)} · {merchantDisplayOrigin(line.merchantOrigin)}
              </div>
              {line.variantTitle ? (
                <div className="mt-mono mt-cart-pop-meta">{line.variantTitle}</div>
              ) : null}
            </div>
            <div className="mt-cart-pop-price">{money(line.price * line.qty)}</div>
            <button
              className="mt-cart-pop-x"
              type="button"
              disabled={mutationBlocked}
              onClick={() => onRemove(line.id, line.merchant, cartItemIdentity(line))}
              aria-label={mutationBlocked ? 'Cart is updating' : 'Remove'}
              title={mutationBlocked ? 'Wait for the current cart update to finish' : undefined}
            >
              <CloseIcon size={12} />
            </button>
          </div>
        ))}
      </div>
      <div className="mt-cart-pop-foot">
        {discountTotal > 0 ? (
          <div className="mt-cart-pop-save mt-mono">
            You are saving {money(discountTotal)} with applied merchant codes.
          </div>
        ) : null}
        <div className="mt-cart-pop-total">
          <span>Total</span>
          <span>{money(grandTotal)}</span>
        </div>
        <button className="mt-cart-pop-detail" type="button" onClick={onViewFull}>
          View full cart
        </button>
      </div>
    </div>
  )
}
