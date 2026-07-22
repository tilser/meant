import { type FormEvent, useCallback, useEffect, useRef, useState } from 'react'

import type { CheckoutAssistantMessage, CheckoutProfile } from '../../../lib/apiClient'
import { CloseIcon, SparkMark } from '../shared/ui'
import { MerchantCheckoutLink } from './MerchantCheckoutLink'
import { EmbeddedCheckout } from './EmbeddedCheckout'
import { SavedCheckoutDetailsPrompt } from './SavedCheckoutDetailsPrompt'
import { merchantDeliveryCoverageSummary, money } from '../utils'
import type { ActiveCheckoutSession, CheckoutAssistantHandler } from './checkoutTypes'
import { savedCheckoutDetails } from './savedCheckoutDetails'
import {
  checkoutAssistantPrompt,
  checkoutNeedsAddress,
  checkoutNeedsHandoff,
  checkoutPhase,
  checkoutShouldOfferSavedDetails,
  merchantCheckoutUrl,
  merchantHandoffReason,
} from './checkoutSessionUi'

function formatCheckoutAmount(session: ActiveCheckoutSession): string {
  if (typeof session.profile.totalAmountMinor === 'number') {
    const amount = session.profile.totalAmountMinor / 100
    const currency = session.profile.currency?.trim().toUpperCase()
    if (currency && currency !== 'USD') {
      try {
        return new Intl.NumberFormat(undefined, {
          style: 'currency',
          currency,
        }).format(amount)
      } catch {
        return `${amount.toFixed(2)} ${currency}`
      }
    }
    return money(amount)
  }
  const fallback = session.items.reduce((sum, item) => {
    const amount = Number.parseFloat(item.lineTotalAmount ?? item.cartTotalAmount ?? '')
    return Number.isFinite(amount) ? sum + amount : sum
  }, 0)
  return fallback > 0 ? money(fallback) : 'Estimated by merchant'
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
  const promptedPhaseRef = useRef<string | null>(null)
  const sessionCartIdRef = useRef<string | null>(null)
  const logRef = useRef<HTMLDivElement | null>(null)
  const merchantUrl = merchantCheckoutUrl(session)
  const handoff = checkoutNeedsHandoff(session)
  const needsAddress = checkoutNeedsAddress(session)
  const savedDetails = savedCheckoutDetails(session.profile)
  const offerSavedDetails = checkoutShouldOfferSavedDetails(session, savedDetailsDismissed)
  const embedded = session.profile.nextAction === 'OPEN_EMBEDDED_CHECKOUT'

  const requestClose = useCallback(() => {
    if (embedded && !window.confirm('Close checkout? Your merchant cart will be preserved.')) return
    onClose()
  }, [embedded, onClose])

  useEffect(() => {
    if (sessionCartIdRef.current !== session.cartId) {
      sessionCartIdRef.current = session.cartId
      promptedPhaseRef.current = null
      setMessages([])
      setInput('')
      setSavedDetailsDismissed(false)
    }
    const nextPhase = `${session.cartId}:${checkoutPhase(session)}`
    if (promptedPhaseRef.current === nextPhase) {
      return
    }
    promptedPhaseRef.current = nextPhase
    setMessages((current) => [
      ...current,
      { role: 'assistant', content: checkoutAssistantPrompt(session) },
    ])
  }, [session])

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight })
  }, [messages, assistantBusy])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        requestClose()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [requestClose])

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
        merchantDeliveryHint: merchantDeliveryCoverageSummary(session.merchant),
      })
      setMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content:
            result?.reply ??
            'I could not reach the checkout agent. Send the details again or continue with the merchant link if one is available.',
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
          <div>
            <div className="mt-checkout-eyebrow">Checkout</div>
            <h2 id="mt-cart-checkout-title">{session.merchant}</h2>
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
            <strong>{session.merchant}</strong>
          </div>
        </div>

        <div className="mt-checkout-assistant">
          <div className="mt-checkout-assistant-log" ref={logRef}>
            {messages.map((message, index) => (
              <div
                className={`mt-checkout-assistant-message ${message.role}`}
                key={`cart-checkout-message-${index}`}
              >
                <span>{message.content}</span>
              </div>
            ))}
            {assistantBusy ? (
              <div className="mt-checkout-assistant-message assistant pending">
                <span>Checking with the merchant...</span>
              </div>
            ) : null}
          </div>
          {error ? <div className="mt-checkout-error">{error}</div> : null}
          {embedded ? (
            <EmbeddedCheckout session={session} surface="cart" onReconciled={onRefresh} />
          ) : handoff ? (
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
              <input
                className="mt-input"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder={
                  needsAddress
                    ? 'Reply with shipping address and contact details...'
                    : 'Tell the checkout agent what to adjust...'
                }
                disabled={assistantBusy || busy}
              />
              <button type="submit" disabled={assistantBusy || busy || !input.trim()}>
                {assistantBusy ? 'Sending...' : 'Send'}
              </button>
            </form>
          )}
        </div>
      </section>
    </div>
  )
}
