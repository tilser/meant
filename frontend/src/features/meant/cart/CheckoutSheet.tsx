import { type FormEvent, useEffect, useMemo, useState } from 'react'

import type {
  CheckoutBuyerInput,
  CheckoutCompletionProfile,
  CheckoutProfile,
  CheckoutShippingAddressInput,
} from '../../../lib/apiClient'
import type { CartItem, UserLocation } from '../types'

export interface ActiveCheckoutSession {
  cartId: string
  merchant: string
  source: 'cart' | 'chat'
  items: readonly CartItem[]
  saved: number
  savedNote: string
  profile: CheckoutProfile
  completion: CheckoutCompletionProfile | null
}

export interface CompleteCheckoutInput {
  handler: string
  token: string
}

export interface UpdateCheckoutAddressInput {
  buyer: CheckoutBuyerInput
  shippingAddress: CheckoutShippingAddressInput
}

interface CheckoutAddressDraft {
  email: string
  firstName: string
  lastName: string
  phoneNumber: string
  streetAddress: string
  extendedAddress: string
  addressLocality: string
  addressRegion: string
  postalCode: string
  addressCountry: string
}

function statusLabel(status: string | null | undefined): string {
  if (!status) {
    return 'Checkout created'
  }
  return status
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

function amountLabel(amountMinor: number | null | undefined, currency: string | null | undefined) {
  if (typeof amountMinor !== 'number' || !Number.isFinite(amountMinor)) {
    return null
  }
  const currencyCode = currency?.trim() || 'USD'
  try {
    const formatter = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: currencyCode,
    })
    const fractionDigits = formatter.resolvedOptions().maximumFractionDigits ?? 2
    return formatter.format(amountMinor / 10 ** fractionDigits)
  } catch {
    return `${(amountMinor / 100).toFixed(2)} ${currencyCode}`
  }
}

function embeddedCheckoutUrl(
  checkoutUrl: string | null | undefined,
  ucpVersion: string | null | undefined,
): string | null {
  if (!checkoutUrl?.trim()) {
    return null
  }
  const trimmed = checkoutUrl.trim()
  try {
    const url = new URL(trimmed)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      return null
    }
    if (ucpVersion?.trim()) {
      url.searchParams.set('ec_version', ucpVersion.trim())
    }
    url.searchParams.set('ec_color_scheme', 'light')
    url.searchParams.set('ec_delegate', 'window.open')
    return url.toString()
  } catch {
    return null
  }
}

function merchantCheckoutUrl(
  profile: CheckoutProfile | null | undefined,
  completion: CheckoutCompletionProfile | null,
): string | null {
  const candidate = completion?.continueUrl ?? profile?.continueUrl ?? profile?.checkoutUrl
  if (!candidate?.trim()) {
    return null
  }
  try {
    const url = new URL(candidate.trim())
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

function completionRequiresEscalation(completion: CheckoutCompletionProfile | null): boolean {
  return completion?.status === 'SCA_REQUIRED'
}

function completionDone(completion: CheckoutCompletionProfile | null): boolean {
  return completion?.status === 'COMPLETED'
}

function checkoutNeedsMerchantInput(
  profile: CheckoutProfile | null | undefined,
  completion: CheckoutCompletionProfile | null,
): boolean {
  const messages = [
    ...(profile?.messages?.map((message) => ({
      code: message.code,
      content: message.content,
      path: message.path,
    })) ?? []),
    ...(completion?.messages?.map((message) => ({
      code: null,
      content: message,
      path: null,
    })) ?? []),
  ]
  return messages.some((message) => {
    const text = [message.code, message.content, message.path]
      .filter((value): value is string => Boolean(value))
      .join(' ')
      .toLowerCase()
    return (
      text.includes('destination address') ||
      text.includes('shipping address') ||
      text.includes('delivery address') ||
      text.includes('shipping method') ||
      text.includes('delivery option') ||
      text.includes('cannot be shipped') ||
      text.includes("can't be shipped") ||
      text.includes('extension interaction') ||
      text.includes('fulfillment')
    )
  })
}

function splitName(name: string): { firstName: string; lastName: string } {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return {
    firstName: parts[0] ?? '',
    lastName: parts.slice(1).join(' '),
  }
}

function addressDraft(
  buyerDefaults: { email: string; name: string },
  deliveryLocation: UserLocation | null,
): CheckoutAddressDraft {
  const name = splitName(buyerDefaults.name)
  return {
    email: buyerDefaults.email,
    firstName: name.firstName,
    lastName: name.lastName,
    phoneNumber: '',
    streetAddress: '',
    extendedAddress: '',
    addressLocality: deliveryLocation?.city ?? '',
    addressRegion: '',
    postalCode: '',
    addressCountry: deliveryLocation?.code ?? 'US',
  }
}

function messageIsError(severity: string | null | undefined): boolean {
  const normalized = severity?.trim().toLowerCase()
  return normalized === 'error' || normalized === 'critical' || normalized === 'fatal'
}

export function CheckoutSheet({
  session,
  busy,
  error,
  buyerDefaults,
  deliveryLocation,
  onClose,
  onRefresh,
  onUpdateAddress,
  onComplete,
}: Readonly<{
  session: ActiveCheckoutSession | null
  busy: boolean
  error: string | null
  buyerDefaults: { email: string; name: string }
  deliveryLocation: UserLocation | null
  onClose: () => void
  onRefresh: () => Promise<void> | void
  onUpdateAddress: (input: UpdateCheckoutAddressInput) => Promise<void> | void
  onComplete: (input: CompleteCheckoutInput) => Promise<void> | void
}>) {
  const [handler, setHandler] = useState('card')
  const [token, setToken] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)
  const defaultBuyerEmail = buyerDefaults.email
  const defaultBuyerName = buyerDefaults.name
  const defaultCity = deliveryLocation?.city ?? ''
  const defaultCountry = deliveryLocation?.code ?? 'US'
  const defaultAddress = useMemo(() => {
    const defaults = { email: defaultBuyerEmail, name: defaultBuyerName }
    return addressDraft(defaults, {
      country: '',
      code: defaultCountry,
      city: defaultCity,
    })
  }, [defaultBuyerEmail, defaultBuyerName, defaultCity, defaultCountry])
  const [address, setAddress] = useState<CheckoutAddressDraft>(defaultAddress)
  const profile = session?.profile
  const completion = session?.completion ?? null
  const status = completion?.status ?? profile?.status ?? null
  const normalizedStatus = profile?.status?.trim().toLowerCase() ?? ''
  const amount = amountLabel(profile?.totalAmountMinor, profile?.currency)
  const escalation =
    Boolean(profile?.requiresEscalation) ||
    normalizedStatus === 'requires_escalation' ||
    completionRequiresEscalation(completion)
  const merchantCheckout = merchantCheckoutUrl(profile, completion)
  const needsMerchantInput = checkoutNeedsMerchantInput(profile, completion)
  const addressRequired = needsMerchantInput && !escalation
  const merchantHandoff = escalation || (!profile?.nativeCheckoutEnabled && !addressRequired)
  const embeddedMerchantCheckout = useMemo(
    () => (merchantHandoff ? embeddedCheckoutUrl(merchantCheckout, profile?.ucpVersion) : null),
    [merchantCheckout, merchantHandoff, profile?.ucpVersion],
  )
  const canComplete =
    Boolean(
      profile?.nativeCheckoutEnabled &&
      profile?.checkoutId &&
      !merchantHandoff &&
      normalizedStatus === 'ready_for_complete',
    ) &&
    !completionDone(completion) &&
    completion?.status !== 'PROCESSING'
  const displayError = localError ?? error
  let completeButtonLabel = 'Place order'
  if (completion?.status === 'PROCESSING') {
    completeButtonLabel = 'Processing...'
  }
  if (busy) {
    completeButtonLabel = 'Completing...'
  }

  useEffect(() => {
    setAddress(defaultAddress)
  }, [defaultAddress, session?.cartId])

  if (!session || !profile) {
    return null
  }

  const submit = () => {
    if (!token.trim()) {
      setLocalError('Enter a payment token from the selected payment handler.')
      return
    }
    setLocalError(null)
    void onComplete({ handler: handler.trim() || 'card', token: token.trim() })
  }

  const patchAddress = (patch: Partial<CheckoutAddressDraft>) => {
    setAddress((current) => ({ ...current, ...patch }))
  }

  const submitAddress = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const input: UpdateCheckoutAddressInput = {
      buyer: {
        email: address.email.trim(),
        firstName: address.firstName.trim(),
        lastName: address.lastName.trim(),
        phoneNumber: address.phoneNumber.trim() || undefined,
      },
      shippingAddress: {
        streetAddress: address.streetAddress.trim(),
        extendedAddress: address.extendedAddress.trim() || undefined,
        addressLocality: address.addressLocality.trim(),
        addressRegion: address.addressRegion.trim() || undefined,
        postalCode: address.postalCode.trim(),
        addressCountry: address.addressCountry.trim().toUpperCase(),
      },
    }
    if (
      !input.buyer.email ||
      !input.buyer.firstName ||
      !input.buyer.lastName ||
      !input.shippingAddress.streetAddress ||
      !input.shippingAddress.addressLocality ||
      !input.shippingAddress.postalCode ||
      !input.shippingAddress.addressCountry
    ) {
      setLocalError('Enter the buyer and shipping address details to continue native checkout.')
      return
    }
    setLocalError(null)
    void onUpdateAddress(input)
  }

  return (
    <div className="mt-checkout-shell" role="dialog" aria-modal="true" aria-label="Checkout">
      <div className="mt-checkout-panel">
        <div className="mt-checkout-head">
          <div>
            <div className="mt-mono mt-checkout-eyebrow">
              {session.source === 'chat' ? 'Chat checkout' : 'Cart checkout'}
            </div>
            <h2>{session.merchant}</h2>
          </div>
          <button className="mt-checkout-close" type="button" onClick={onClose}>
            Close
          </button>
        </div>

        <div className="mt-checkout-summary">
          <div>
            <span className="mt-mono">Status</span>
            <strong>{statusLabel(status)}</strong>
          </div>
          <div>
            <span className="mt-mono">Total</span>
            <strong>{amount ?? 'Pending'}</strong>
          </div>
          <div>
            <span className="mt-mono">Items</span>
            <strong>{session.items.reduce((sum, item) => sum + item.qty, 0)}</strong>
          </div>
        </div>

        {profile.messages?.length ? (
          <div className="mt-checkout-messages">
            {profile.messages.map((message, index) => (
              <div
                className={`mt-checkout-message ${messageIsError(message.severity) ? 'error' : ''}`}
                key={`${message.code ?? 'message'}-${index}`}
              >
                <span>{message.content}</span>
                {message.path ? <span className="mt-mono">{message.path}</span> : null}
              </div>
            ))}
          </div>
        ) : null}

        {completion?.messages?.length ? (
          <div className="mt-checkout-messages">
            {completion.messages.map((message, index) => (
              <div className="mt-checkout-message" key={`completion-${index}`}>
                <span>{message}</span>
              </div>
            ))}
          </div>
        ) : null}

        {displayError ? <div className="mt-checkout-error">{displayError}</div> : null}

        {addressRequired ? (
          <form className="mt-checkout-address" onSubmit={submitAddress}>
            <div className="mt-checkout-note">
              Shipping address is required before native checkout can continue.
            </div>
            <label className="mt-field">
              <span className="mt-field-label">Email</span>
              <input
                className="mt-input"
                value={address.email}
                onChange={(event) => patchAddress({ email: event.target.value })}
                autoComplete="email"
                disabled={busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">First name</span>
              <input
                className="mt-input"
                value={address.firstName}
                onChange={(event) => patchAddress({ firstName: event.target.value })}
                autoComplete="given-name"
                disabled={busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">Last name</span>
              <input
                className="mt-input"
                value={address.lastName}
                onChange={(event) => patchAddress({ lastName: event.target.value })}
                autoComplete="family-name"
                disabled={busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">Phone</span>
              <input
                className="mt-input"
                value={address.phoneNumber}
                onChange={(event) => patchAddress({ phoneNumber: event.target.value })}
                autoComplete="tel"
                disabled={busy}
              />
            </label>
            <label className="mt-field mt-checkout-address-wide">
              <span className="mt-field-label">Street address</span>
              <input
                className="mt-input"
                value={address.streetAddress}
                onChange={(event) => patchAddress({ streetAddress: event.target.value })}
                autoComplete="address-line1"
                disabled={busy}
              />
            </label>
            <label className="mt-field mt-checkout-address-wide">
              <span className="mt-field-label">Apartment, suite, etc.</span>
              <input
                className="mt-input"
                value={address.extendedAddress}
                onChange={(event) => patchAddress({ extendedAddress: event.target.value })}
                autoComplete="address-line2"
                disabled={busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">City</span>
              <input
                className="mt-input"
                value={address.addressLocality}
                onChange={(event) => patchAddress({ addressLocality: event.target.value })}
                autoComplete="address-level2"
                disabled={busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">Region</span>
              <input
                className="mt-input"
                value={address.addressRegion}
                onChange={(event) => patchAddress({ addressRegion: event.target.value })}
                autoComplete="address-level1"
                disabled={busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">Postal code</span>
              <input
                className="mt-input"
                value={address.postalCode}
                onChange={(event) => patchAddress({ postalCode: event.target.value })}
                autoComplete="postal-code"
                disabled={busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">Country</span>
              <input
                className="mt-input"
                value={address.addressCountry}
                onChange={(event) => patchAddress({ addressCountry: event.target.value })}
                autoComplete="country"
                disabled={busy}
              />
            </label>
            <button className="primary mt-checkout-address-submit" type="submit" disabled={busy}>
              {busy ? 'Updating...' : 'Update checkout'}
            </button>
          </form>
        ) : merchantHandoff ? (
          <div className="mt-checkout-handoff">
            <div className="mt-checkout-note">
              Continue in the merchant checkout only for escalated checkout steps.
            </div>
            {embeddedMerchantCheckout ? (
              <div className="mt-checkout-embed">
                <iframe
                  title={`${session.merchant} checkout`}
                  src={embeddedMerchantCheckout}
                  sandbox="allow-forms allow-scripts allow-same-origin allow-popups"
                />
              </div>
            ) : (
              <div className="mt-checkout-empty">
                Merchant checkout is not available yet. Refresh to check for an updated checkout
                session.
              </div>
            )}
          </div>
        ) : (
          <div className="mt-checkout-native">
            <label className="mt-field">
              <span className="mt-field-label">Payment handler</span>
              <input
                className="mt-input"
                value={handler}
                onChange={(event) => setHandler(event.target.value)}
                disabled={!canComplete || busy}
              />
            </label>
            <label className="mt-field">
              <span className="mt-field-label">Payment token</span>
              <input
                className="mt-input"
                value={token}
                onChange={(event) => setToken(event.target.value)}
                disabled={!canComplete || busy}
                autoComplete="off"
              />
            </label>
          </div>
        )}

        <div className="mt-checkout-actions">
          <button type="button" onClick={() => void onRefresh()} disabled={busy}>
            Refresh
          </button>
          {completionDone(completion) ? (
            <button className="primary" type="button" onClick={onClose}>
              Done
            </button>
          ) : addressRequired ? null : merchantHandoff ? (
            merchantCheckout ? (
              <a className="primary" href={merchantCheckout} target="_blank" rel="noreferrer">
                Open merchant checkout
              </a>
            ) : null
          ) : (
            <button
              className="primary"
              type="button"
              onClick={submit}
              disabled={!canComplete || busy}
            >
              {completeButtonLabel}
            </button>
          )}
        </div>
        {profile.nativeCheckoutEnabled &&
        !addressRequired &&
        !merchantHandoff &&
        normalizedStatus !== 'ready_for_complete' ? (
          <div className="mt-checkout-note">
            Merchant checkout is not ready for completion yet. Refresh to check the latest status.
          </div>
        ) : null}
      </div>
    </div>
  )
}
