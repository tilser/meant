import { useEffect, useState } from 'react'

import { deliveryLocationSummary } from '../shared/locations'
import { CartIcon, CloseIcon, EmptyState, ProductArtwork, SparkMark, ViewHead } from '../shared/ui'
import type { CartItem, CheckoutPayload, Product, ProductId, UserLocation } from '../types'
import {
  canMerchantShip,
  cartGroups,
  cartItemIdentity,
  cartLines,
  computeSmartAlerts,
  money,
} from '../utils'
import type {
  AppliedCartCode,
  AppliedCartCodeType,
  ApplyCartCodeInput,
  MerchantCartSnapshot,
  RemoveCartCodeInput,
} from './types'
import { cartCountSummary, formatCartCount } from './cartCounts'
import { merchantAdjacentDisplayLabel, merchantDisplayOrigin } from './merchantOrigin'
import {
  appliedCodeDisplay,
  cartMoney,
  cartSnapshotSavings,
  cartSnapshotSubtotal,
  cartSnapshotTotal,
  cartSummaryDelivery,
  deliveryGroupSummary,
} from './utils'

export function CartView({
  cart,
  products,
  cartSnapshots,
  deliveryLocations,
  onRemove,
  onQty,
  onAdd,
  onApplyCode,
  onRemoveCode,
  onCheckout,
  agentBusy,
  checkoutMerchantKey,
  checkoutError,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  cartSnapshots: Readonly<Record<string, MerchantCartSnapshot>>
  deliveryLocations: readonly UserLocation[]
  onRemove: (id: ProductId, merchant: string, identity?: string) => void
  onQty: (id: ProductId, merchant: string, qty: number, identity?: string) => void
  onAdd: (id: ProductId, merchant: string) => void
  onApplyCode: (input: ApplyCartCodeInput) => Promise<{ ok: boolean; message?: string }>
  onRemoveCode: (input: RemoveCartCodeInput) => Promise<{ ok: boolean; message?: string }>
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  agentBusy: boolean
  checkoutMerchantKey: string | null
  checkoutError: { merchant: string; merchantKey: string; message: string } | null
}>) {
  const [scanning, setScanning] = useState(true)
  const [codeEntries, setCodeEntries] = useState<
    Record<string, { discount: string; giftCard: string }>
  >({})
  const [codeBusy, setCodeBusy] = useState<Record<string, AppliedCartCodeType | 'REMOVE' | null>>(
    {},
  )
  const [codeErrors, setCodeErrors] = useState<Record<string, string | null>>({})

  useEffect(() => {
    setScanning(true)
    const timeout = window.setTimeout(() => setScanning(false), 1700)
    return () => window.clearTimeout(timeout)
  }, [cart.length])

  const lines = cartLines(cart, products)
  const alerts = computeSmartAlerts(lines, products)
  const shipWarnings =
    deliveryLocations.length > 0
      ? lines.filter((line) => !canMerchantShip(line.merchant, deliveryLocations))
      : []
  const groups = cartGroups(lines)
  const counts = cartCountSummary(lines)
  const groupSummaries = groups.map((group) => {
    const merchantKey = group.merchantKey
    const snapshot = cartSnapshots[merchantKey]
    const fallbackTotal = group.subtotal + group.delivery
    const subtotal = cartSnapshotSubtotal(snapshot, group.subtotal)
    const savings = cartSnapshotSavings(snapshot, group.subtotal, fallbackTotal)
    const total = cartSnapshotTotal(snapshot, fallbackTotal, group.subtotal)
    return {
      group,
      merchantKey,
      snapshot,
      subtotal,
      savings,
      total,
      delivery: cartSummaryDelivery(subtotal, savings, total),
      currency: snapshot?.currency ?? group.currency,
    }
  })
  const itemsTotal = groupSummaries.reduce((sum, summary) => sum + summary.subtotal, 0)
  const discountTotal = groupSummaries.reduce((sum, summary) => sum + summary.savings, 0)
  const deliveryTotal = groupSummaries.reduce((sum, summary) => sum + summary.delivery, 0)
  const grandTotal = groupSummaries.reduce((sum, summary) => sum + summary.total, 0)
  const currencies = new Set(
    groupSummaries
      .map((summary) => summary.currency)
      .filter((value): value is string => Boolean(value)),
  )
  const summaryCurrency = currencies.size === 1 ? currencies.values().next().value : null
  const totalsPending = groupSummaries.some((summary) =>
    summary.group.items.some((item) => item.syncing),
  )
  const codeCount = groupSummaries.reduce(
    (sum, summary) => sum + (summary.snapshot?.appliedCodes.length ?? 0),
    0,
  )
  const warnCount = alerts.filter((alert) => alert.kind === 'warn').length

  const updateCodeEntry = (merchantKey: string, field: 'discount' | 'giftCard', value: string) => {
    setCodeEntries((current) => ({
      ...current,
      [merchantKey]: {
        discount: current[merchantKey]?.discount ?? '',
        giftCard: current[merchantKey]?.giftCard ?? '',
        [field]: value,
      },
    }))
    setCodeErrors((current) => ({ ...current, [merchantKey]: null }))
  }

  const submitCode = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    type: AppliedCartCodeType,
  ) => {
    if (agentBusy) return
    if (!cartId) {
      setCodeErrors((current) => ({
        ...current,
        [merchantKey]: 'This merchant cart is still syncing.',
      }))
      return
    }
    const field = type === 'DISCOUNT' ? 'discount' : 'giftCard'
    const code = (codeEntries[merchantKey]?.[field] ?? '').trim()
    setCodeBusy((current) => ({ ...current, [merchantKey]: type }))
    const result = await onApplyCode({ merchantKey, merchant, cartId, code, type })
    setCodeBusy((current) => ({ ...current, [merchantKey]: null }))
    if (result.ok) {
      setCodeEntries((current) => ({
        ...current,
        [merchantKey]: {
          discount: type === 'DISCOUNT' ? '' : (current[merchantKey]?.discount ?? ''),
          giftCard: type === 'GIFT_CARD' ? '' : (current[merchantKey]?.giftCard ?? ''),
        },
      }))
      setCodeErrors((current) => ({ ...current, [merchantKey]: null }))
      return
    }
    setCodeErrors((current) => ({
      ...current,
      [merchantKey]: result.message ?? 'The merchant did not accept this code.',
    }))
  }

  const removeCode = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    code: AppliedCartCode,
  ) => {
    if (agentBusy) return
    if (!cartId) {
      return
    }
    setCodeBusy((current) => ({ ...current, [merchantKey]: 'REMOVE' }))
    const result = await onRemoveCode({ merchantKey, merchant, cartId, code })
    setCodeBusy((current) => ({ ...current, [merchantKey]: null }))
    setCodeErrors((current) => ({
      ...current,
      [merchantKey]: result.ok
        ? null
        : (result.message ?? 'The merchant could not remove this code.'),
    }))
  }

  if (lines.length === 0) {
    return (
      <main className="mt-feed mt-view">
        <ViewHead eyebrow="Smart cart" title="Your cart" />
        <EmptyState
          title="Your cart is empty"
          sub="Add products and Meant keeps merchant totals in sync."
          mark={<CartIcon />}
        />
      </main>
    )
  }

  return (
    <main className="mt-feed mt-view mt-cart">
      <ViewHead
        eyebrow="Smart cart"
        title="Your cart"
        sub={`${formatCartCount(counts.lineItemCount, 'line item')} from ${formatCartCount(counts.merchantCount, 'merchant')} - one smart cart, with checkout handled at each merchant.`}
      />
      <div className="mt-cart-grid">
        <div className="mt-cart-main">
          {alerts.length > 0 || shipWarnings.length > 0 ? (
            <div className="mt-alerts">
              {shipWarnings.map((line) => (
                <div key={`ship-${cartItemIdentity(line)}`} className="mt-alert mt-alert-warn">
                  <span className="mt-alert-ico">!</span>
                  <div className="mt-alert-body">
                    <div className="mt-alert-title">Does not ship to selected destinations</div>
                    <div className="mt-alert-text">
                      {merchantDisplayOrigin(line.merchantOrigin)} cannot deliver{' '}
                      {line.product.name} to {deliveryLocationSummary(deliveryLocations)}.
                    </div>
                  </div>
                  <button
                    className="mt-alert-fix"
                    type="button"
                    disabled={agentBusy}
                    onClick={() => onRemove(line.id, line.merchant, cartItemIdentity(line))}
                  >
                    Remove item
                    <span className="mt-alert-fix-sub mt-mono">will not ship</span>
                  </button>
                </div>
              ))}
              {alerts.map((alert) => (
                <div key={alert.id} className={`mt-alert mt-alert-${alert.kind}`}>
                  <span className="mt-alert-ico">{alert.kind === 'warn' ? '!' : 'ok'}</span>
                  <div className="mt-alert-body">
                    <div className="mt-alert-title">{alert.title}</div>
                    <div className="mt-alert-text">{alert.body}</div>
                  </div>
                  {alert.fix ? (
                    <button
                      className="mt-alert-fix"
                      type="button"
                      disabled={agentBusy}
                      onClick={() =>
                        onAdd(alert.fix?.id ?? 'adapter', alert.fix?.merchant ?? 'Lumen Store')
                      }
                    >
                      {alert.fix.label}
                      <span className="mt-alert-fix-sub mt-mono">{alert.fix.sub}</span>
                    </button>
                  ) : null}
                </div>
              ))}
            </div>
          ) : null}

          {groupSummaries.map(
            ({ group, merchantKey, snapshot, subtotal, savings, total, currency }) => {
              const groupCounts = cartCountSummary(group.items)
              const cartId = snapshot?.cartId ?? group.items.find((item) => item.cartId)?.cartId
              const merchantDisplay = merchantDisplayOrigin(
                group.merchantOrigin ?? snapshot?.merchantOrigin,
              )
              const groupCurrency =
                currency ?? group.items.find((item) => item.cartCurrency)?.cartCurrency
              const deliverySummary = deliveryGroupSummary(
                group.deliveryGroups,
                group.delivery,
                groupCurrency,
              )
              const groupSyncing = group.items.some((item) => item.syncing)
              const groupLineError = group.items.find((item) => item.syncError)?.syncError
              const groupCheckoutError =
                checkoutError?.merchantKey === merchantKey ? checkoutError.message : null
              const appliedCodes = snapshot?.appliedCodes ?? []
              const entry = codeEntries[merchantKey] ?? { discount: '', giftCard: '' }
              const busy = codeBusy[merchantKey] ?? null
              const codeError = codeErrors[merchantKey]
              const codeControlsDisabled = agentBusy || groupSyncing || !cartId || Boolean(busy)
              const groupCheckoutable = group.items.every((item) =>
                Boolean(
                  item.cartId &&
                  item.productVariantId &&
                  (item.cartLineId || item.remoteCartLineId) &&
                  !item.syncError,
                ),
              )
              const checkoutNeedsDelivery = group.hasDeliveryOptions && !group.hasSelectedDelivery
              const checkoutBusy = checkoutMerchantKey === merchantKey
              const deliveryDisplay = groupSyncing
                ? 'Delivery pending'
                : checkoutNeedsDelivery
                  ? 'Calculated at checkout'
                  : deliverySummary
              const checkoutBlocked =
                agentBusy ||
                scanning ||
                groupSyncing ||
                !groupCheckoutable ||
                Boolean(checkoutMerchantKey)
              const checkoutSub =
                (agentBusy ? 'The merchant cart is updating' : null) ??
                groupLineError ??
                groupCheckoutError ??
                (groupSyncing
                  ? 'Syncing merchant cart'
                  : !groupCheckoutable
                    ? 'Checkout needs a merchant cart-ready item'
                    : appliedCodes.length > 0
                      ? `${appliedCodes.length} applied · -${cartMoney(savings, currency)}`
                      : deliveryDisplay)

              return (
                <div className="mt-mgroup" key={group.merchantKey}>
                  <div className="mt-mgroup-head">
                    <div className="mt-mgroup-name">
                      <span className="mt-mgroup-dot" />
                      {merchantDisplay}
                      <span className="mt-mono mt-mgroup-count">
                        {formatCartCount(groupCounts.lineItemCount, 'line item')}
                      </span>
                    </div>
                    <div className="mt-mono mt-mgroup-ship">{deliveryDisplay}</div>
                  </div>
                  {group.items.map((line) => (
                    <div className="mt-citem" key={cartItemIdentity(line)}>
                      <div className="mt-citem-media">
                        <ProductArtwork
                          product={line.product}
                          label={line.product.category.toLowerCase()}
                        />
                      </div>
                      <div className="mt-citem-info">
                        <div className="mt-mono mt-citem-brand">
                          {merchantAdjacentDisplayLabel(line.product.brand, line.merchantOrigin)}
                        </div>
                        <div className="mt-citem-name">{line.product.name}</div>
                        {line.variantTitle ? (
                          <div className="mt-mono mt-citem-variant">{line.variantTitle}</div>
                        ) : null}
                        <div className="mt-mono mt-citem-deliv">
                          {line.syncing
                            ? 'Syncing cart...'
                            : `Arrives ${line.delivery.toLowerCase()}`}
                        </div>
                        {line.syncError ? (
                          <div className="mt-mono mt-citem-error">{line.syncError}</div>
                        ) : null}
                      </div>
                      <div className="mt-citem-right">
                        <div className="mt-qty">
                          <button
                            type="button"
                            onClick={() =>
                              onQty(line.id, line.merchant, line.qty - 1, cartItemIdentity(line))
                            }
                            aria-label="Decrease"
                            disabled={agentBusy || line.syncing}
                          >
                            -
                          </button>
                          <span>{line.qty}</span>
                          <button
                            type="button"
                            onClick={() =>
                              onQty(line.id, line.merchant, line.qty + 1, cartItemIdentity(line))
                            }
                            aria-label="Increase"
                            disabled={agentBusy || line.syncing}
                          >
                            +
                          </button>
                        </div>
                        <div className="mt-citem-price">
                          {money(line.price * line.qty, line.priceCurrency)}
                        </div>
                        <button
                          className="mt-citem-remove"
                          type="button"
                          onClick={() => onRemove(line.id, line.merchant, cartItemIdentity(line))}
                          aria-label="Remove"
                          disabled={agentBusy || line.syncing}
                        >
                          <CloseIcon size={13} />
                        </button>
                      </div>
                    </div>
                  ))}
                  <div className="mt-mgroup-foot">
                    <div className="mt-code-panel">
                      <div className="mt-code-forms">
                        <form
                          className="mt-code-form"
                          onSubmit={(event) => {
                            event.preventDefault()
                            void submitCode(merchantKey, group.merchant, cartId, 'DISCOUNT')
                          }}
                        >
                          <input
                            className="mt-code-input mt-mono"
                            value={entry.discount}
                            onChange={(event) =>
                              updateCodeEntry(merchantKey, 'discount', event.target.value)
                            }
                            placeholder="Discount code"
                            disabled={codeControlsDisabled}
                          />
                          <button
                            type="submit"
                            disabled={codeControlsDisabled || !entry.discount.trim()}
                          >
                            {busy === 'DISCOUNT' ? 'Applying...' : 'Apply'}
                          </button>
                        </form>
                        <form
                          className="mt-code-form"
                          onSubmit={(event) => {
                            event.preventDefault()
                            void submitCode(merchantKey, group.merchant, cartId, 'GIFT_CARD')
                          }}
                        >
                          <input
                            className="mt-code-input mt-mono"
                            value={entry.giftCard}
                            onChange={(event) =>
                              updateCodeEntry(merchantKey, 'giftCard', event.target.value)
                            }
                            placeholder="Gift card"
                            disabled={codeControlsDisabled}
                          />
                          <button
                            type="submit"
                            disabled={codeControlsDisabled || !entry.giftCard.trim()}
                          >
                            {busy === 'GIFT_CARD' ? 'Applying...' : 'Apply'}
                          </button>
                        </form>
                      </div>
                      {appliedCodes.length > 0 ? (
                        <div className="mt-applied-codes">
                          {appliedCodes.map((code, index) => (
                            <span
                              className="mt-applied-code"
                              key={`${code.type}-${code.code ?? code.displayCode ?? code.label ?? 'code'}-${index}`}
                            >
                              <span className="mt-code-val mt-mono">
                                {appliedCodeDisplay(code)}
                              </span>
                              <span className="mt-found-label">
                                {code.label ??
                                  (code.type === 'GIFT_CARD' ? 'Gift card' : 'Discount')}
                              </span>
                              {code.amount ? (
                                <span className="mt-found-save mt-mono">
                                  -{cartMoney(Math.abs(code.amount), code.currency ?? currency)}
                                </span>
                              ) : null}
                              <button
                                className="mt-code-remove"
                                type="button"
                                disabled={agentBusy || !cartId || Boolean(busy) || !code.code}
                                onClick={() =>
                                  void removeCode(merchantKey, group.merchant, cartId, code)
                                }
                                aria-label={`Remove ${appliedCodeDisplay(code)}`}
                              >
                                <CloseIcon size={11} />
                              </button>
                            </span>
                          ))}
                        </div>
                      ) : (
                        <div className="mt-found mt-found-none mt-mono">
                          No applied codes for {merchantDisplay}
                        </div>
                      )}
                      {codeError ? <div className="mt-cart-inline-error">{codeError}</div> : null}
                    </div>
                    <div className="mt-mgroup-sub">
                      Subtotal{' '}
                      <span>{groupSyncing ? 'Pending' : cartMoney(subtotal, currency)}</span>
                    </div>
                    {!groupSyncing && savings > 0 ? (
                      <div className="mt-mgroup-sub save">
                        Savings <span>-{cartMoney(savings, currency)}</span>
                      </div>
                    ) : null}
                  </div>
                  <div className="mt-mgroup-pay">
                    <div>
                      <div className="mt-mgroup-pay-total">
                        <span className="mt-mono">Merchant total</span>
                        <strong>
                          {groupSyncing ? 'Pending' : cartMoney(total, groupCurrency)}
                        </strong>
                      </div>
                      <div
                        className={`mt-mgroup-pay-sub ${groupLineError || groupCheckoutError ? 'error' : ''}`}
                      >
                        {checkoutSub}
                      </div>
                    </div>
                    <button
                      className="mt-mcheckout"
                      type="button"
                      disabled={checkoutBlocked}
                      onClick={() =>
                        void onCheckout({
                          items: group.items,
                          saved: savings,
                          savedNote:
                            appliedCodes.length > 0
                              ? appliedCodes
                                  .map((code) => `${appliedCodeDisplay(code)} applied`)
                                  .join(', ')
                              : '',
                          merchant: group.merchant,
                          merchantKey,
                          checkoutUrl: snapshot?.checkoutUrl ?? null,
                          continueUrl: snapshot?.continueUrl ?? null,
                        })
                      }
                    >
                      {checkoutBusy ? 'Starting checkout...' : `Checkout with ${merchantDisplay}`}
                    </button>
                  </div>
                </div>
              )
            },
          )}
        </div>

        <aside className="mt-summary">
          <div className="mt-summary-card">
            <div className="mt-summary-title">Order summary</div>
            <div className="mt-scan-banner">
              {codeCount > 0 ? (
                <>
                  <SparkMark size={14} /> {codeCount} merchant code{codeCount > 1 ? 's' : ''}{' '}
                  applied
                </>
              ) : (
                <>Apply discount or gift-card codes at each merchant.</>
              )}
            </div>
            <div className="mt-sum-row">
              <span>Items ({lines.reduce((sum, line) => sum + line.qty, 0)})</span>
              <span>
                {totalsPending
                  ? 'Pending'
                  : summaryCurrency
                    ? cartMoney(itemsTotal, summaryCurrency)
                    : 'Per merchant'}
              </span>
            </div>
            <div className={`mt-sum-row ${!totalsPending && discountTotal > 0 ? 'save' : 'muted'}`}>
              <span>Applied savings</span>
              <span>
                {totalsPending
                  ? 'Pending'
                  : summaryCurrency
                    ? discountTotal > 0
                      ? `-${cartMoney(discountTotal, summaryCurrency)}`
                      : cartMoney(0, summaryCurrency)
                    : 'Per merchant'}
              </span>
            </div>
            <div className="mt-sum-row">
              <span>Delivery</span>
              <span>
                {totalsPending
                  ? 'Pending'
                  : deliveryTotal === 0
                    ? 'Free'
                    : summaryCurrency
                      ? cartMoney(deliveryTotal, summaryCurrency)
                      : 'Per merchant'}
              </span>
            </div>
            <div className="mt-sum-total">
              <span>Total</span>
              <span>
                {totalsPending
                  ? 'Pending'
                  : summaryCurrency
                    ? cartMoney(grandTotal, summaryCurrency)
                    : 'Calculated per merchant'}
              </span>
            </div>
            {!totalsPending && discountTotal > 0 ? (
              <div className="mt-sum-note mt-mono">
                {summaryCurrency
                  ? `You are saving ${cartMoney(discountTotal, summaryCurrency)} with merchant-applied codes.`
                  : 'Savings are calculated per merchant currency.'}
              </div>
            ) : null}
            {warnCount > 0 ? (
              <div className="mt-sum-warn">
                <span className="mt-sum-warn-dot" /> {warnCount} compatibility issue
                {warnCount === 1 ? '' : 's'} to review above
              </div>
            ) : null}
            <div className="mt-sum-handoff">
              <SparkMark size={14} />
              <span>Checkout stays on this page unless a merchant requires escalation.</span>
            </div>
            <div className="mt-mono mt-summary-foot">
              {groups.length} merchant checkout{groups.length > 1 ? 's' : ''} needed.
            </div>
          </div>
        </aside>
      </div>
    </main>
  )
}
