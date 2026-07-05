import { type FormEvent, useEffect, useState } from 'react'

import { LOCATIONS } from '../data'
import { deliveryLocationSummary } from '../shared/locations'
import { CartIcon, CloseIcon, EmptyState, ProductArtwork, SparkMark, ViewHead } from '../shared/ui'
import type {
  CartDeliveryGroup,
  CartDeliveryOption,
  CartItem,
  CheckoutPayload,
  Product,
  ProductId,
  UserLocation,
} from '../types'
import {
  canMerchantShip,
  cartDeliveryOptions,
  cartGroups,
  cartLines,
  cartMerchantKey,
  computeSmartAlerts,
  money,
  normalizedMerchantName,
  selectedCartDeliveryOption,
} from '../utils'
import type {
  AppliedCartCode,
  AppliedCartCodeType,
  ApplyCartCodeInput,
  DeliveryAddressDraft,
  DeliveryAddressPayload,
  DeliveryOptionPayload,
  MerchantCartSnapshot,
  RemoveCartCodeInput,
} from './types'
import {
  appliedCodeDisplay,
  cartMoney,
  cartSnapshotHasReliableTotal,
  cartSnapshotSavings,
  cartSnapshotSubtotal,
  cartSnapshotTotal,
  deliveryGroupSummary,
  deliveryOptionCost,
  deliveryOptionSpeed,
  deliveryOptionTitle,
  emptyDeliveryAddressDraft,
} from './utils'

function MerchantDeliveryPanel({
  merchantKey,
  merchant,
  cartId,
  deliveryGroups,
  currency,
  draft,
  busy,
  error,
  onDraft,
  onSubmitAddress,
  onSelectOption,
}: Readonly<{
  merchantKey: string
  merchant: string
  cartId?: string | null
  deliveryGroups: readonly CartDeliveryGroup[]
  currency?: string | null
  draft: DeliveryAddressDraft
  busy: boolean
  error: string | null
  onDraft: (patch: Partial<DeliveryAddressDraft>) => void
  onSubmitAddress: () => void
  onSelectOption: (group: CartDeliveryGroup, option: CartDeliveryOption) => void
}>) {
  const deliveryOptionCount = deliveryGroups.reduce(
    (sum, group) => sum + cartDeliveryOptions(group).length,
    0,
  )
  const hasOptions = deliveryOptionCount > 0
  const canSubmit = Boolean(
    cartId && draft.countryCode.trim() && draft.city.trim() && draft.postalCode.trim(),
  )

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (canSubmit && !busy) {
      onSubmitAddress()
    }
  }

  return (
    <div className="mt-delivery-panel">
      <div className="mt-delivery-head">
        <div>
          <div className="mt-delivery-title">Delivery options</div>
          <div className="mt-mono mt-delivery-privacy">City, postal code and country only</div>
        </div>
        {hasOptions ? (
          <span className="mt-mono mt-delivery-count">{deliveryOptionCount} options</span>
        ) : null}
      </div>
      <form className="mt-delivery-address" onSubmit={submit}>
        <label className="mt-field">
          <span className="mt-field-label">Country</span>
          <select
            className="mt-select"
            value={draft.countryCode}
            onChange={(event) => onDraft({ countryCode: event.target.value })}
          >
            {LOCATIONS.map((location) => (
              <option key={location.code} value={location.code}>
                {location.country}
              </option>
            ))}
          </select>
        </label>
        <label className="mt-field">
          <span className="mt-field-label">City</span>
          <input
            className="mt-input"
            value={draft.city}
            onChange={(event) => onDraft({ city: event.target.value })}
            autoComplete="address-level2"
          />
        </label>
        <label className="mt-field">
          <span className="mt-field-label">Postal code</span>
          <input
            className="mt-input"
            value={draft.postalCode}
            onChange={(event) => onDraft({ postalCode: event.target.value })}
            autoComplete="postal-code"
          />
        </label>
        <label className="mt-field">
          <span className="mt-field-label">Region</span>
          <input
            className="mt-input"
            value={draft.provinceCode}
            onChange={(event) => onDraft({ provinceCode: event.target.value })}
            autoComplete="address-level1"
          />
        </label>
        <button className="mt-delivery-refresh" type="submit" disabled={!canSubmit || busy}>
          {busy ? 'Updating...' : 'Get options'}
        </button>
      </form>
      {error ? <div className="mt-mono mt-delivery-error">{error}</div> : null}
      {hasOptions ? (
        <div className="mt-delivery-groups">
          {deliveryGroups.map((group, index) => {
            const selected = selectedCartDeliveryOption(group)
            const options = cartDeliveryOptions(group)
            return (
              <div
                className="mt-delivery-group"
                key={group.id ?? group.handle ?? `${merchantKey}-${index}`}
              >
                {deliveryGroups.length > 1 ? (
                  <div className="mt-mono mt-delivery-group-title">Shipment {index + 1}</div>
                ) : null}
                <div className="mt-delivery-options">
                  {options.length > 0 ? (
                    options.map((option, optionIndex) => {
                      const optionSelected =
                        selected?.handle && option.handle
                          ? selected.handle === option.handle
                          : option.selected === true
                      const speed = deliveryOptionSpeed(option)
                      return (
                        <button
                          className={`mt-delivery-option ${optionSelected ? 'selected' : ''}`}
                          key={
                            option.handle ??
                            option.title ??
                            `${merchantKey}-${index}-${optionIndex}`
                          }
                          type="button"
                          disabled={busy || !option.handle}
                          aria-pressed={optionSelected}
                          onClick={() => onSelectOption(group, option)}
                        >
                          <span className="mt-delivery-option-main">
                            <span className="mt-delivery-option-title">
                              {deliveryOptionTitle(option)}
                            </span>
                            {speed ? (
                              <span className="mt-mono mt-delivery-option-speed">{speed}</span>
                            ) : null}
                          </span>
                          <span className="mt-mono mt-delivery-option-cost">
                            {deliveryOptionCost(option, currency)}
                          </span>
                        </button>
                      )
                    })
                  ) : (
                    <div className="mt-mono mt-delivery-empty">
                      No options available for this shipment.
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="mt-mono mt-delivery-empty">
          {cartId ? `No delivery options loaded for ${merchant}.` : 'Merchant cart is syncing.'}
        </div>
      )}
    </div>
  )
}

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
  onDeliveryAddress,
  onDeliveryOption,
  onCheckout,
  checkoutMerchant,
  checkoutError,
}: Readonly<{
  cart: readonly CartItem[]
  products: readonly Product[]
  cartSnapshots: Readonly<Record<string, MerchantCartSnapshot>>
  deliveryLocations: readonly UserLocation[]
  onRemove: (id: ProductId, merchant: string) => void
  onQty: (id: ProductId, merchant: string, qty: number) => void
  onAdd: (id: ProductId, merchant: string) => void
  onApplyCode: (input: ApplyCartCodeInput) => Promise<{ ok: boolean; message?: string }>
  onRemoveCode: (input: RemoveCartCodeInput) => Promise<{ ok: boolean; message?: string }>
  onDeliveryAddress: (payload: DeliveryAddressPayload) => Promise<boolean> | boolean
  onDeliveryOption: (payload: DeliveryOptionPayload) => Promise<boolean> | boolean
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  checkoutMerchant: string | null
  checkoutError: { merchant: string; message: string } | null
}>) {
  const [scanning, setScanning] = useState(true)
  const [codeEntries, setCodeEntries] = useState<
    Record<string, { discount: string; giftCard: string }>
  >({})
  const [codeBusy, setCodeBusy] = useState<Record<string, AppliedCartCodeType | 'REMOVE' | null>>(
    {},
  )
  const [codeErrors, setCodeErrors] = useState<Record<string, string | null>>({})
  const [addressDrafts, setAddressDrafts] = useState<Record<string, DeliveryAddressDraft>>({})
  const [deliveryBusyByMerchant, setDeliveryBusyByMerchant] = useState<Record<string, number>>({})
  const [deliveryErrorsByMerchant, setDeliveryErrorsByMerchant] = useState<Record<string, string>>(
    {},
  )

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
  const groups = cartGroups(lines, scanning)
  const groupSummaries = groups.map((group) => {
    const merchantKey = group.items[0]
      ? cartMerchantKey(group.items[0])
      : normalizedMerchantName(group.merchant)
    const snapshot = cartSnapshots[merchantKey]
    const fallbackTotal = group.subtotal + group.delivery
    return {
      group,
      merchantKey,
      snapshot,
      subtotal: cartSnapshotSubtotal(snapshot, group.subtotal),
      savings: cartSnapshotSavings(snapshot, group.subtotal, fallbackTotal),
      total: cartSnapshotTotal(snapshot, fallbackTotal),
      hasReliableRemoteTotal: cartSnapshotHasReliableTotal(snapshot, fallbackTotal),
      currency: snapshot?.currency ?? null,
    }
  })
  const itemsTotal = groupSummaries.reduce((sum, summary) => sum + summary.subtotal, 0)
  const discountTotal = groupSummaries.reduce((sum, summary) => sum + summary.savings, 0)
  const deliveryTotal = groupSummaries.reduce(
    (sum, summary) => (summary.hasReliableRemoteTotal ? sum : sum + summary.group.delivery),
    0,
  )
  const grandTotal = groupSummaries.reduce((sum, summary) => sum + summary.total, 0)
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

  const addressDraft = (merchantKey: string) =>
    addressDrafts[merchantKey] ?? emptyDeliveryAddressDraft(deliveryLocations)

  const updateAddressDraft = (merchantKey: string, patch: Partial<DeliveryAddressDraft>) => {
    setAddressDrafts((current) => ({
      ...current,
      [merchantKey]: {
        ...(current[merchantKey] ?? emptyDeliveryAddressDraft(deliveryLocations)),
        ...patch,
      },
    }))
  }

  const beginDeliveryBusy = (merchantKey: string) => {
    setDeliveryBusyByMerchant((current) => ({
      ...current,
      [merchantKey]: (current[merchantKey] ?? 0) + 1,
    }))
  }

  const endDeliveryBusy = (merchantKey: string) => {
    setDeliveryBusyByMerchant((current) => {
      const next = { ...current }
      const count = (next[merchantKey] ?? 0) - 1
      if (count > 0) {
        next[merchantKey] = count
      } else {
        delete next[merchantKey]
      }
      return next
    })
  }

  const setDeliveryErrorForMerchant = (merchantKey: string, message: string | null) => {
    setDeliveryErrorsByMerchant((current) => {
      const next = { ...current }
      if (message) {
        next[merchantKey] = message
      } else {
        delete next[merchantKey]
      }
      return next
    })
  }

  const submitDeliveryAddress = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    draft: DeliveryAddressDraft,
  ) => {
    if (!cartId) {
      setDeliveryErrorForMerchant(merchantKey, 'Merchant cart is still syncing.')
      return
    }
    beginDeliveryBusy(merchantKey)
    setDeliveryErrorForMerchant(merchantKey, null)
    try {
      const ok = await onDeliveryAddress({ cartId, merchantKey, merchant, ...draft })
      if (!ok) {
        setDeliveryErrorForMerchant(merchantKey, 'Could not load delivery options.')
      }
    } catch {
      setDeliveryErrorForMerchant(merchantKey, 'Could not load delivery options.')
    } finally {
      endDeliveryBusy(merchantKey)
    }
  }

  const selectDeliveryOption = async (
    merchantKey: string,
    merchant: string,
    cartId: string | null | undefined,
    group: CartDeliveryGroup,
    option: CartDeliveryOption,
  ) => {
    if (!cartId) {
      setDeliveryErrorForMerchant(merchantKey, 'Merchant cart is still syncing.')
      return
    }
    beginDeliveryBusy(merchantKey)
    setDeliveryErrorForMerchant(merchantKey, null)
    try {
      const ok = await onDeliveryOption({ cartId, merchantKey, merchant, group, option })
      if (!ok) {
        setDeliveryErrorForMerchant(merchantKey, 'Could not update delivery choice.')
      }
    } catch {
      setDeliveryErrorForMerchant(merchantKey, 'Could not update delivery choice.')
    } finally {
      endDeliveryBusy(merchantKey)
    }
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
        sub={`${lines.length} items from ${groups.length} merchants - one smart cart, with checkout handled at each merchant.`}
      />
      <div className="mt-cart-grid">
        <div className="mt-cart-main">
          {alerts.length > 0 || shipWarnings.length > 0 ? (
            <div className="mt-alerts">
              {shipWarnings.map((line) => (
                <div key={`ship-${line.id}-${line.merchant}`} className="mt-alert mt-alert-warn">
                  <span className="mt-alert-ico">!</span>
                  <div className="mt-alert-body">
                    <div className="mt-alert-title">Does not ship to selected destinations</div>
                    <div className="mt-alert-text">
                      {line.merchant} cannot deliver {line.product.name} to{' '}
                      {deliveryLocationSummary(deliveryLocations)}.
                    </div>
                  </div>
                  <button
                    className="mt-alert-fix"
                    type="button"
                    onClick={() => onRemove(line.id, line.merchant)}
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
              const cartId = snapshot?.cartId ?? group.items.find((item) => item.cartId)?.cartId
              const groupCurrency =
                currency ?? group.items.find((item) => item.cartCurrency)?.cartCurrency
              const groupDraft = addressDraft(merchantKey)
              const groupDeliveryBusy = (deliveryBusyByMerchant[merchantKey] ?? 0) > 0
              const groupDeliveryError = deliveryErrorsByMerchant[merchantKey] ?? null
              const deliverySummary = deliveryGroupSummary(
                group.deliveryGroups,
                group.delivery,
                groupCurrency,
              )
              const groupSyncing = group.items.some((item) => item.syncing)
              const groupLineError = group.items.find((item) => item.syncError)?.syncError
              const groupCheckoutError =
                checkoutError?.merchant === group.merchant ? checkoutError.message : null
              const appliedCodes = snapshot?.appliedCodes ?? []
              const entry = codeEntries[merchantKey] ?? { discount: '', giftCard: '' }
              const busy = codeBusy[merchantKey] ?? null
              const codeError = codeErrors[merchantKey]
              const codeControlsDisabled = groupSyncing || !cartId || Boolean(busy)
              const groupCheckoutable = group.items.every((item) =>
                Boolean(item.cartId && item.productVariantId && !item.syncError),
              )
              const checkoutNeedsDelivery = group.hasDeliveryOptions && !group.hasSelectedDelivery
              const checkoutBusy = checkoutMerchant === group.merchant
              const deliveryDisplay = checkoutNeedsDelivery
                ? 'Choose delivery option'
                : deliverySummary
              const checkoutBlocked =
                scanning || groupSyncing || !groupCheckoutable || Boolean(checkoutMerchant)
              const checkoutSub =
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
                <div className="mt-mgroup" key={group.merchant}>
                  <div className="mt-mgroup-head">
                    <div className="mt-mgroup-name">
                      <span className="mt-mgroup-dot" />
                      {group.merchant}
                      <span className="mt-mono mt-mgroup-count">
                        {group.items.length} item{group.items.length > 1 ? 's' : ''}
                      </span>
                    </div>
                    <div className="mt-mono mt-mgroup-ship">{deliveryDisplay}</div>
                  </div>
                  {group.items.map((line) => (
                    <div className="mt-citem" key={`${line.id}-${line.merchant}`}>
                      <div className="mt-citem-media">
                        <ProductArtwork
                          product={line.product}
                          label={line.product.category.toLowerCase()}
                        />
                      </div>
                      <div className="mt-citem-info">
                        <div className="mt-mono mt-citem-brand">{line.product.brand}</div>
                        <div className="mt-citem-name">{line.product.name}</div>
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
                            onClick={() => onQty(line.id, line.merchant, line.qty - 1)}
                            aria-label="Decrease"
                            disabled={line.syncing}
                          >
                            -
                          </button>
                          <span>{line.qty}</span>
                          <button
                            type="button"
                            onClick={() => onQty(line.id, line.merchant, line.qty + 1)}
                            aria-label="Increase"
                            disabled={line.syncing}
                          >
                            +
                          </button>
                        </div>
                        <div className="mt-citem-price">{money(line.price * line.qty)}</div>
                        <button
                          className="mt-citem-remove"
                          type="button"
                          onClick={() => onRemove(line.id, line.merchant)}
                          aria-label="Remove"
                          disabled={line.syncing}
                        >
                          <CloseIcon size={13} />
                        </button>
                      </div>
                    </div>
                  ))}
                  <MerchantDeliveryPanel
                    merchantKey={merchantKey}
                    merchant={group.merchant}
                    cartId={cartId}
                    deliveryGroups={group.deliveryGroups}
                    currency={groupCurrency}
                    draft={groupDraft}
                    busy={groupDeliveryBusy}
                    error={groupDeliveryError}
                    onDraft={(patch) => updateAddressDraft(merchantKey, patch)}
                    onSubmitAddress={() => {
                      void submitDeliveryAddress(merchantKey, group.merchant, cartId, groupDraft)
                    }}
                    onSelectOption={(deliveryGroup, option) => {
                      void selectDeliveryOption(
                        merchantKey,
                        group.merchant,
                        cartId,
                        deliveryGroup,
                        option,
                      )
                    }}
                  />
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
                                disabled={!cartId || Boolean(busy) || !code.code}
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
                          No applied codes for {group.merchant}
                        </div>
                      )}
                      {codeError ? <div className="mt-cart-inline-error">{codeError}</div> : null}
                    </div>
                    <div className="mt-mgroup-sub">
                      Subtotal <span>{cartMoney(subtotal, currency)}</span>
                    </div>
                    {savings > 0 ? (
                      <div className="mt-mgroup-sub save">
                        Savings <span>-{cartMoney(savings, currency)}</span>
                      </div>
                    ) : null}
                  </div>
                  <div className="mt-mgroup-pay">
                    <div>
                      <div className="mt-mgroup-pay-total">
                        <span className="mt-mono">Merchant total</span>
                        <strong>{cartMoney(total, groupCurrency)}</strong>
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
                          checkoutUrl: snapshot?.checkoutUrl ?? null,
                          continueUrl: snapshot?.continueUrl ?? null,
                        })
                      }
                    >
                      {checkoutBusy ? 'Opening checkout...' : `Check out at ${group.merchant}`}
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
              <span>{money(itemsTotal)}</span>
            </div>
            <div className={`mt-sum-row ${discountTotal > 0 ? 'save' : 'muted'}`}>
              <span>Applied savings</span>
              <span>{discountTotal > 0 ? `-${money(discountTotal)}` : money(0)}</span>
            </div>
            <div className="mt-sum-row">
              <span>Delivery</span>
              <span>{deliveryTotal === 0 ? 'Free' : money(deliveryTotal)}</span>
            </div>
            <div className="mt-sum-total">
              <span>Total</span>
              <span>{money(grandTotal)}</span>
            </div>
            {discountTotal > 0 ? (
              <div className="mt-sum-note mt-mono">
                You are saving {money(discountTotal)} with merchant-applied codes.
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
              <span>
                Checkout happens on each merchant's site. Use the checkout button inside every
                merchant group.
              </span>
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
