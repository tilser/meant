import { useMemo, useState } from 'react'

import type { CheckoutCompletionProfile, CheckoutProfile } from '../../../lib/apiClient'
import type { CartItem } from '../types'

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
  if (typeof amountMinor !== 'number') {
    return null
  }
  const currencyCode = currency || 'USD'
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: currencyCode,
    }).format(amountMinor / 100)
  } catch {
    return `${(amountMinor / 100).toFixed(2)} ${currencyCode}`
  }
}

function embeddedCheckoutUrl(
  continueUrl: string | null | undefined,
  ucpVersion: string | null | undefined,
): string | null {
  if (!continueUrl?.trim()) {
    return null
  }
  try {
    const url = new URL(continueUrl)
    if (ucpVersion?.trim()) {
      url.searchParams.set('ec_version', ucpVersion.trim())
    }
    url.searchParams.set('ec_color_scheme', 'light')
    url.searchParams.set('ec_delegate', 'window.open')
    return url.toString()
  } catch {
    const separator = continueUrl.includes('?') ? '&' : '?'
    const version = ucpVersion?.trim() ? `ec_version=${encodeURIComponent(ucpVersion.trim())}&` : ''
    return `${continueUrl}${separator}${version}ec_color_scheme=light&ec_delegate=window.open`
  }
}

function completionRequiresEscalation(completion: CheckoutCompletionProfile | null): boolean {
  return completion?.status === 'SCA_REQUIRED'
}

function completionDone(completion: CheckoutCompletionProfile | null): boolean {
  return completion?.status === 'COMPLETED'
}

export function CheckoutSheet({
  session,
  busy,
  error,
  onClose,
  onRefresh,
  onComplete,
}: Readonly<{
  session: ActiveCheckoutSession | null
  busy: boolean
  error: string | null
  onClose: () => void
  onRefresh: () => Promise<void> | void
  onComplete: (input: CompleteCheckoutInput) => Promise<void> | void
}>) {
  const [handler, setHandler] = useState('card')
  const [token, setToken] = useState('')
  const [localError, setLocalError] = useState<string | null>(null)
  const profile = session?.profile
  const completion = session?.completion ?? null
  const status = completion?.status ?? profile?.status ?? null
  const normalizedStatus = profile?.status?.trim().toLowerCase() ?? ''
  const amount = amountLabel(profile?.totalAmountMinor, profile?.currency)
  const escalation =
    Boolean(profile?.requiresEscalation) ||
    normalizedStatus === 'requires_escalation' ||
    completionRequiresEscalation(completion)
  const escalationUrl = useMemo(
    () => embeddedCheckoutUrl(completion?.continueUrl ?? profile?.continueUrl, profile?.ucpVersion),
    [completion?.continueUrl, profile?.continueUrl, profile?.ucpVersion],
  )
  const canComplete =
    Boolean(
      profile?.nativeCheckoutEnabled &&
      profile?.checkoutId &&
      !escalation &&
      normalizedStatus === 'ready_for_complete',
    ) && !completionDone(completion)
  const displayError = localError ?? error

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
                className={`mt-checkout-message ${message.type === 'error' ? 'error' : ''}`}
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

        {escalation ? (
          <div className="mt-checkout-embed">
            {escalationUrl ? (
              <iframe
                title={`${session.merchant} checkout`}
                src={escalationUrl}
                sandbox="allow-forms allow-scripts allow-same-origin allow-popups"
              />
            ) : (
              <div className="mt-checkout-empty">
                Merchant escalation is required but no URL was returned.
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
          ) : (
            <button
              className="primary"
              type="button"
              onClick={submit}
              disabled={!canComplete || busy}
            >
              {busy ? 'Completing...' : 'Place order'}
            </button>
          )}
        </div>
        {!profile.nativeCheckoutEnabled && !escalation ? (
          <div className="mt-checkout-note">
            Native completion is not enabled for this merchant. Refresh to check for an updated
            session or wait for merchant escalation.
          </div>
        ) : null}
        {profile.nativeCheckoutEnabled &&
        !escalation &&
        normalizedStatus !== 'ready_for_complete' ? (
          <div className="mt-checkout-note">
            Merchant checkout is not ready for completion yet. Refresh to check the latest status.
          </div>
        ) : null}
      </div>
    </div>
  )
}
