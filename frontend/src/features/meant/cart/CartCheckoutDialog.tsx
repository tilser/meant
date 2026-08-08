import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'

import type { CheckoutAssistantMessage, CheckoutProfile } from '../../../lib/apiClient'
import { sanitizeBuyerVisibleText } from '../agent/buyerVisibleText'
import { CloseIcon, MeantHeartMark, SparkMark } from '../shared/ui'
import { CheckoutJourney } from './CheckoutJourney'
import { EmbeddedCheckout } from './EmbeddedCheckout'
import { merchantDisplayOrigin } from './merchantOrigin'
import { MerchantCheckoutHandoff } from './MerchantCheckoutHandoff'
import { MerchantCheckoutLink } from './MerchantCheckoutLink'
import { SavedCheckoutDetailsPrompt } from './SavedCheckoutDetailsPrompt'
import { merchantDeliveryCoverageSummary, minorUnitsToMajor, money } from '../utils'
import type { ActiveCheckoutSession, CheckoutAssistantHandler } from './checkoutTypes'
import { CheckoutExitConfirmation } from './CheckoutExitConfirmation'
import { savedCheckoutDetails } from './savedCheckoutDetails'
import {
  checkoutNeedsAddress,
  checkoutNeedsHandoff,
  checkoutRequiresMerchantRedirect,
  checkoutShouldOfferSavedDetails,
  checkoutUsesEmbeddedCheckout,
  merchantCheckoutUrl,
  merchantHandoffReason,
} from './checkoutSessionUi'

function formatCheckoutAmount(session: ActiveCheckoutSession): string {
  if (typeof session.profile.totalAmountMinor === 'number') {
    const amount = minorUnitsToMajor(session.profile.totalAmountMinor, session.profile.currency)
    return money(amount, session.profile.currency)
  }
  const fallback = session.items.reduce((sum, item) => {
    const amount = Number.parseFloat(item.lineTotalAmount ?? item.cartTotalAmount ?? '')
    return Number.isFinite(amount) ? sum + amount : sum
  }, 0)
  const itemCurrencies = new Set(
    session.items
      .map((item) => item.cartCurrency ?? item.orderCurrency)
      .filter((currency): currency is string => Boolean(currency?.trim())),
  )
  const fallbackCurrency = itemCurrencies.size === 1 ? itemCurrencies.values().next().value : null
  return fallback > 0 && fallbackCurrency
    ? money(fallback, fallbackCurrency)
    : 'Estimated by merchant'
}

export function CartCheckoutDialog({
  session,
  busy,
  error,
  onClose,
  onRefresh,
  onCheckoutAssistant,
}: Readonly<{
  session: ActiveCheckoutSession
  busy: boolean
  error: string | null
  onClose: () => void
  onRefresh: (checkout?: CheckoutProfile) => Promise<void> | void
  onCheckoutAssistant: CheckoutAssistantHandler
}>) {
  const [messages, setMessages] = useState<CheckoutAssistantMessage[]>([])
  const [input, setInput] = useState('')
  const [assistantBusy, setAssistantBusy] = useState(false)
  const [savedDetailsDismissed, setSavedDetailsDismissed] = useState(false)
  const [exitConfirmationOpen, setExitConfirmationOpen] = useState(false)
  const sessionCartIdRef = useRef<string | null>(null)
  const merchantUrl = merchantCheckoutUrl(session)
  const handoff = checkoutNeedsHandoff(session)
  const merchantRedirect = checkoutRequiresMerchantRedirect(session.profile)
  const needsAddress = checkoutNeedsAddress(session)
  const savedDetails = savedCheckoutDetails(session.profile)
  const offerSavedDetails = checkoutShouldOfferSavedDetails(session, savedDetailsDismissed)
  const embedded = checkoutUsesEmbeddedCheckout(session)
  const merchantDisplay = merchantDisplayOrigin(session.merchantOrigin)

  const requestClose = useCallback(() => {
    if (embedded) {
      setExitConfirmationOpen(true)
      return
    }
    onClose()
  }, [embedded, onClose])

  useEffect(() => {
    if (sessionCartIdRef.current !== session.cartId) {
      sessionCartIdRef.current = session.cartId
      setMessages([])
      setInput('')
      setSavedDetailsDismissed(false)
      setExitConfirmationOpen(false)
    }
  }, [session])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (!exitConfirmationOpen) requestClose()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [exitConfirmationOpen, requestClose])

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const message = input.trim()
    if (!message || assistantBusy || busy) {
      return
    }
    const history = messages
    if (needsAddress) {
      setSavedDetailsDismissed(true)
    }
    setMessages((current) => [...current, { role: 'user', content: message }])
    setInput('')
    setAssistantBusy(true)
    try {
      const result = await onCheckoutAssistant(message, history, {
        merchantDeliveryHint: merchantDeliveryCoverageSummary(merchantDisplay),
      })
      setMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content: sanitizeBuyerVisibleText(
            result?.reply ??
              'I could not reach the checkout agent. Send the details again or continue with the merchant link if one is available.',
            merchantDisplay,
          ),
        },
      ])
    } catch {
      setMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content:
            'I could not reach the checkout agent. Send the details again or continue with the merchant link if one is available.',
        },
      ])
    } finally {
      setAssistantBusy(false)
    }
  }

  return (
    <div className="mt-checkout-shell" role="presentation" onMouseDown={requestClose}>
      <section
        className="mt-checkout-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="mt-cart-checkout-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="mt-checkout-head">
          <div className="mt-checkout-brand">
            <span className="mt-checkout-brand-mark" aria-hidden>
              <MeantHeartMark size={21} />
            </span>
            <span className="mt-mono">
              <em>Meant</em> checkout · {merchantDisplay}
            </span>
          </div>
          <button
            className="mt-checkout-close"
            type="button"
            onClick={requestClose}
            aria-label="Close"
          >
            <CloseIcon size={14} />
          </button>
        </div>

        <CheckoutJourney
          session={session}
          stage={needsAddress ? 'delivery' : 'secure'}
          merchantDisplay={merchantDisplay}
          titleId="mt-cart-checkout-title"
        />

        <div className="mt-checkout-summary">
          <div>
            <span>Total</span>
            <strong>{formatCheckoutAmount(session)}</strong>
          </div>
          <div>
            <span>Items</span>
            <strong>{session.items.reduce((sum, item) => sum + item.qty, 0)}</strong>
          </div>
          <div>
            <span>Merchant</span>
            <strong>{merchantDisplay}</strong>
          </div>
        </div>

        <div className="mt-checkout-assistant">
          {error ? <div className="mt-checkout-error">{error}</div> : null}
          {embedded ? (
            <EmbeddedCheckout session={session} surface="cart" onReconciled={onRefresh} />
          ) : handoff ? (
            merchantRedirect ? (
              <MerchantCheckoutHandoff session={session} busy={busy} onRefresh={onRefresh} />
            ) : (
              <div className="mt-checkout-handoff">
                <div className="mt-checkout-note">
                  <SparkMark size={13} />
                  <span>{merchantHandoffReason(session)}</span>
                </div>
                <div className="mt-checkout-actions">
                  {merchantUrl ? (
                    <MerchantCheckoutLink session={session} />
                  ) : (
                    <button type="button" onClick={() => void onRefresh()} disabled={busy}>
                      {busy ? 'Checking...' : 'Get merchant checkout link'}
                    </button>
                  )}
                </div>
              </div>
            )
          ) : offerSavedDetails && savedDetails ? (
            <SavedCheckoutDetailsPrompt
              details={savedDetails}
              busy={busy || assistantBusy}
              onUse={() => setSavedDetailsDismissed(true)}
              onManual={() => setSavedDetailsDismissed(true)}
              onCheckoutAssistant={onCheckoutAssistant}
              onRefresh={onRefresh}
            />
          ) : (
            <form className="mt-checkout-assistant-input" onSubmit={submit}>
              <span className="mt-checkout-composer-spark" aria-hidden>
                <SparkMark size={15} />
              </span>
              <input
                className="mt-input"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder={
                  needsAddress
                    ? 'Address, name, email and phone — all in one message'
                    : 'Anything you’d like Meant to adjust?'
                }
                disabled={assistantBusy || busy}
              />
              <button type="submit" disabled={assistantBusy || busy || !input.trim()}>
                {assistantBusy ? 'Sending...' : 'Send to Meant'}
              </button>
            </form>
          )}
        </div>
      </section>
      <CheckoutExitConfirmation
        open={exitConfirmationOpen}
        onKeepOpen={() => setExitConfirmationOpen(false)}
        onCloseCheckout={() => {
          setExitConfirmationOpen(false)
          onClose()
        }}
      />
    </div>
  )
}
