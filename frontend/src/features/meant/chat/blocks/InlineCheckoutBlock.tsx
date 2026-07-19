import { type FormEvent, useEffect, useRef, useState } from 'react'

import type { CheckoutAssistantMessage } from '../../../../lib/apiClient'
import type {
  ActiveCheckoutSession,
  CheckoutAssistantHandler,
  CheckoutReleaseHandler,
  CheckoutReleaseOutcome,
} from '../../cart/checkoutTypes'
import { liveCheckoutGroupFor } from '../../cart/checkoutGroupResolution'
import { EmbeddedCheckout } from '../../cart/EmbeddedCheckout'
import {
  checkoutAssistantPrompt,
  checkoutNeedsAddress,
  checkoutNeedsHandoff,
  checkoutPhase,
  merchantCheckoutUrl,
  merchantHandoffReason,
} from '../../cart/checkoutSessionUi'
import { MerchantCheckoutLink } from '../../cart/MerchantCheckoutLink'
import { SparkMark } from '../../shared/ui'
import type { CartItem, CheckoutPayload, Product } from '../../types'
import { merchantCheckoutStartBlocked } from './checkoutActivationPolicy'
import {
  cartGroups,
  cartLines,
  computeSmartAlerts,
  firstUrl,
  merchantDeliveryCoverageSummary,
  money,
  type CartGroup,
} from '../../utils'

function cartItemReadyForCheckout(item: CartItem): boolean {
  return Boolean(
    item.cartId &&
    item.productVariantId &&
    (item.cartLineId || item.remoteCartLineId) &&
    item.syncing !== true &&
    !item.syncError,
  )
}

export function InlineCheckoutBlock({
  threadId,
  cart,
  products,
  actionCart,
  actionProducts,
  onCheckout,
  activeCheckout,
  checkoutBusy,
  checkoutError,
  onCheckoutAssistant,
  onRefreshCheckout,
  onReleaseCheckout,
  onOpenCart,
  onOpenOrders,
}: Readonly<{
  threadId: string
  cart: readonly CartItem[]
  products: readonly Product[]
  actionCart?: readonly CartItem[]
  actionProducts?: readonly Product[]
  onCheckout: (payload: CheckoutPayload) => Promise<void> | void
  activeCheckout: ActiveCheckoutSession | null
  checkoutBusy: boolean
  checkoutError: string | null
  onCheckoutAssistant: CheckoutAssistantHandler
  onRefreshCheckout: () => Promise<void> | void
  onReleaseCheckout?: CheckoutReleaseHandler
  onOpenCart: () => void
  onOpenOrders: () => void
}>) {
  const [payingMerchant, setPayingMerchant] = useState<string | null>(null)
  const [checkoutStartError, setCheckoutStartError] = useState<{
    merchantKey: string
    message: string
  } | null>(null)
  const [assistantMessages, setAssistantMessages] = useState<CheckoutAssistantMessage[]>([])
  const [assistantInput, setAssistantInput] = useState('')
  const [assistantBusy, setAssistantBusy] = useState(false)
  const [releasedCheckouts, setReleasedCheckouts] = useState<
    ReadonlyMap<string, CheckoutReleaseOutcome>
  >(new Map())
  const promptedPhaseRef = useRef<string | null>(null)
  const activeCartIdRef = useRef<string | null>(null)
  const assistantLogRef = useRef<HTMLDivElement | null>(null)
  const lines = cartLines(cart, products)
  const groups = cartGroups(lines)
  const liveGroups = actionCart
    ? cartGroups(cartLines(actionCart, actionProducts ?? products))
    : groups
  const alerts = computeSmartAlerts(lines, products)
  const total = groups.reduce((sum, group) => sum + group.total, 0)

  useEffect(() => {
    if (!activeCheckout) {
      promptedPhaseRef.current = null
      activeCartIdRef.current = null
      setAssistantMessages([])
      setAssistantInput('')
      return
    }
    if (activeCartIdRef.current !== activeCheckout.cartId) {
      activeCartIdRef.current = activeCheckout.cartId
      promptedPhaseRef.current = null
      setAssistantMessages([])
      setAssistantInput('')
    }
    const nextPhase = `${activeCheckout.cartId}:${checkoutPhase(activeCheckout)}`
    if (promptedPhaseRef.current === nextPhase) {
      return
    }
    promptedPhaseRef.current = nextPhase
    setAssistantMessages((current) => [
      ...current,
      { role: 'assistant', content: checkoutAssistantPrompt(activeCheckout) },
    ])
  }, [activeCheckout])

  useEffect(() => {
    assistantLogRef.current?.scrollTo({ top: assistantLogRef.current.scrollHeight })
  }, [assistantMessages, assistantBusy])

  const payGroup = async (displayGroup: CartGroup, actionGroup: CartGroup) => {
    if (checkoutBusy) return
    const groupCartId = actionGroup.items.find((item) => item.cartId)?.cartId
    if (groupCartId) {
      setReleasedCheckouts((current) => {
        if (!current.has(groupCartId)) return current
        const next = new Map(current)
        next.delete(groupCartId)
        return next
      })
    }
    setPayingMerchant(displayGroup.merchantKey)
    setCheckoutStartError(null)
    try {
      const saved = Math.max(0, actionGroup.subtotal + actionGroup.delivery - actionGroup.total)
      await onCheckout({
        merchant: actionGroup.merchant,
        merchantKey: actionGroup.merchantKey,
        chatThreadId: threadId,
        items: actionGroup.items,
        saved,
        savedNote: saved > 0 ? 'Merchant-applied savings' : 'Chat checkout',
        checkoutUrl: firstUrl(...actionGroup.items.map((item) => item.checkoutUrl)),
        continueUrl: firstUrl(...actionGroup.items.map((item) => item.continueUrl)),
      })
    } catch (error) {
      setCheckoutStartError({
        merchantKey: displayGroup.merchantKey,
        message:
          error instanceof Error && error.message.trim()
            ? error.message
            : 'Checkout failed. Please try again.',
      })
    } finally {
      setPayingMerchant(null)
    }
  }

  const releaseCheckout = (cartId: string, outcome: CheckoutReleaseOutcome) => {
    setReleasedCheckouts((current) => {
      if (current.get(cartId) === outcome) return current
      return new Map(current).set(cartId, outcome)
    })
    onReleaseCheckout?.(cartId, outcome)
  }

  const submitAssistant = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const message = assistantInput.trim()
    if (!message || assistantBusy || !activeCheckout) {
      return
    }
    const history = assistantMessages
    setAssistantMessages((current) => [...current, { role: 'user', content: message }])
    setAssistantInput('')
    setAssistantBusy(true)
    try {
      const result = await onCheckoutAssistant(message, history, {
        merchantDeliveryHint: merchantDeliveryCoverageSummary(activeCheckout.merchant),
      })
      setAssistantMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content:
            result?.reply ??
            'I could not reach the checkout agent. Try again, or continue with the merchant link if one is available.',
        },
      ])
    } catch {
      setAssistantMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content:
            'I could not reach the checkout agent. Try again, or continue with the merchant link if one is available.',
        },
      ])
    } finally {
      setAssistantBusy(false)
    }
  }

  const renderConversation = (session: ActiveCheckoutSession) => {
    const handoff = checkoutNeedsHandoff(session)
    const embedded = session.profile.nextAction === 'OPEN_EMBEDDED_CHECKOUT'
    const merchantUrl = merchantCheckoutUrl(session)
    const needsAddress = checkoutNeedsAddress(session)
    return (
      <div className="mt-ct-checkout-agent">
        <div className="mt-checkout-assistant-log" ref={assistantLogRef}>
          {assistantMessages.map((message, index) => (
            <div
              className={`mt-checkout-assistant-message ${message.role}`}
              key={`checkout-agent-${index}`}
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
        {checkoutError ? <div className="mt-cart-inline-error">{checkoutError}</div> : null}
        {embedded ? (
          <EmbeddedCheckout
            session={session}
            surface="chat"
            onReconciled={onRefreshCheckout}
            onSessionReleased={(outcome) => releaseCheckout(session.cartId, outcome)}
          />
        ) : handoff ? (
          <div className="mt-ct-checkout-handoff">
            <div className="mt-ct-checkout-handoff-copy">
              <SparkMark size={13} />
              <span>{merchantHandoffReason(session)}</span>
            </div>
            {merchantUrl ? (
              <MerchantCheckoutLink session={session} />
            ) : (
              <button
                className="mt-ct-cobtn"
                type="button"
                disabled={checkoutBusy}
                onClick={() => void onRefreshCheckout()}
              >
                {checkoutBusy ? 'Checking...' : 'Check merchant link'}
              </button>
            )}
            <button
              className="mt-ct-cobtn"
              type="button"
              onClick={() => releaseCheckout(session.cartId, 'dismissed')}
            >
              Back to merchant checkouts
            </button>
          </div>
        ) : (
          <>
            <form className="mt-checkout-assistant-input" onSubmit={submitAssistant}>
              <input
                className="mt-input"
                value={assistantInput}
                onChange={(event) => setAssistantInput(event.target.value)}
                placeholder={
                  needsAddress
                    ? 'Reply with shipping address and contact details...'
                    : 'Tell the checkout agent what to adjust...'
                }
                disabled={assistantBusy || checkoutBusy}
              />
              <button
                type="submit"
                disabled={assistantBusy || checkoutBusy || !assistantInput.trim()}
              >
                {assistantBusy ? 'Sending...' : 'Send'}
              </button>
            </form>
            <button
              className="mt-ct-cobtn"
              type="button"
              onClick={() => releaseCheckout(session.cartId, 'dismissed')}
            >
              Back to merchant checkouts
            </button>
          </>
        )}
      </div>
    )
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
            <span>Estimated total</span>
            <strong>{money(total)}</strong>
          </div>
          {alerts.some((alert) => alert.kind === 'warn') ? (
            <div className="mt-ct-checkout-warn">
              Review compatibility warnings before checkout. You can still continue from here.
            </div>
          ) : null}
          <div className="mt-ct-checkout-groups">
            {groups.map((group) => {
              const actionGroup = actionCart ? liveCheckoutGroupFor(group, liveGroups) : group
              const groupCartId = actionGroup?.items.find((item) => item.cartId)?.cartId
              const releasedOutcome = groupCartId ? releasedCheckouts.get(groupCartId) : undefined
              const groupIsActive =
                !releasedOutcome &&
                (Boolean(activeCheckout && groupCartId && activeCheckout.cartId === groupCartId) ||
                  Boolean(
                    activeCheckout &&
                    !groupCartId &&
                    activeCheckout.merchant === actionGroup?.merchant,
                  ))
              const groupReady = Boolean(
                actionGroup && actionGroup.items.every(cartItemReadyForCheckout),
              )
              const groupSyncIssues = Array.from(
                new Set(
                  (actionGroup?.items ?? [])
                    .map((item) => item.syncError?.trim())
                    .filter((message): message is string => Boolean(message)),
                ),
              )
              const checkoutBlocked = merchantCheckoutStartBlocked({
                groupReady,
                payingMerchant,
                activeCartId: activeCheckout?.cartId ?? null,
                releasedCartIds: new Set(releasedCheckouts.keys()),
                targetCartId: groupCartId ?? null,
              })
              const checkoutLabel = !actionGroup
                ? 'Cart changed — open full cart'
                : checkoutBusy
                  ? 'Checkout is starting...'
                  : !groupReady
                    ? groupSyncIssues.length > 0
                      ? 'Resolve cart issues to continue'
                      : 'Merchant cart is syncing...'
                    : payingMerchant === group.merchantKey
                      ? 'Starting checkout...'
                      : payingMerchant
                        ? 'Checkout is starting...'
                        : releasedOutcome === 'cancelled' || releasedOutcome === 'dismissed'
                          ? 'Return to checkout'
                          : releasedOutcome === 'handoff'
                            ? 'Open merchant checkout again'
                            : 'Start checkout in chat'
              return (
                <div className="mt-ct-cogroup" key={group.merchantKey}>
                  <div className="mt-ct-cogroup-head">
                    <div>
                      <div className="mt-ct-cogroup-name">{group.merchant}</div>
                      <div className="mt-mono mt-ct-cogroup-meta">
                        {group.items.reduce((sum, line) => sum + line.qty, 0)} items · Estimated{' '}
                        {money(group.total)}
                      </div>
                    </div>
                    <strong>{money(group.total)}</strong>
                  </div>
                  {checkoutStartError?.merchantKey === group.merchantKey &&
                  payingMerchant === null ? (
                    <div className="mt-cart-inline-error">{checkoutStartError.message}</div>
                  ) : null}
                  {!groupReady ? (
                    !actionGroup ? (
                      <div className="mt-cart-inline-error">
                        This merchant cart changed since this message. Open the full cart to review
                        the current items.
                      </div>
                    ) : groupSyncIssues.length > 0 ? (
                      <div className="mt-cart-inline-error">
                        {groupSyncIssues.map((message) => (
                          <div key={message}>{message}</div>
                        ))}
                      </div>
                    ) : (
                      <div className="mt-cart-inline-error">
                        Merchant cart is still syncing. Checkout can start as soon as the merchant
                        confirms these items.
                      </div>
                    )
                  ) : null}
                  {groupIsActive && activeCheckout ? (
                    renderConversation(activeCheckout)
                  ) : releasedOutcome === 'completed' ? (
                    <div className="mt-ct-checkout-handoff-copy" role="status">
                      <SparkMark size={13} />
                      <span>Checkout completed for this merchant.</span>
                    </div>
                  ) : (
                    <button
                      className="mt-ct-cobtn"
                      type="button"
                      disabled={checkoutBusy || checkoutBlocked || !actionGroup}
                      onClick={() => {
                        if (actionGroup) void payGroup(group, actionGroup)
                      }}
                    >
                      {checkoutLabel}
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        </>
      ) : (
        <div className="mt-ct-checkout-empty">
          <SparkMark size={13} />
          <span>Your cart is empty.</span>
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
