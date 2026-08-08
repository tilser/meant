import { useCallback, useEffect, useRef, useState } from 'react'

import {
  bootstrapEmbeddedCheckout,
  cancelEmbeddedCheckout,
  completeEmbeddedCheckout,
  type CheckoutProfile,
  type EmbeddedCheckoutBootstrapProfile,
} from '../../../lib/apiClient'
import { type CheckoutLifecycleReason, trackCheckoutLifecycleEvent } from './checkoutAnalytics'
import { acknowledgeEmbeddedCheckoutOpenedWithRetry } from './embeddedCheckoutAcknowledgement'
import {
  CheckoutKitAdapterError,
  createCheckoutKitHandle,
  type CheckoutKitHandle,
} from './checkoutKitAdapter'
import {
  embeddedCheckoutEnabled,
  embeddedCheckoutSessionExpired,
  resolveEmbeddedCheckoutBootstrap,
  safeExternalCheckoutUrl,
} from './embeddedCheckoutPolicy'
import { merchantContinueUrl } from './checkoutSessionUi'
import type { ActiveCheckoutSession } from './checkoutTypes'
import { CheckoutExitConfirmation } from './CheckoutExitConfirmation'
import { EmbeddedCheckoutView, type EmbeddedCheckoutPhase } from './EmbeddedCheckoutView'

const START_TIMEOUT_MS = 30_000

function checkoutKitDebugEnabled(value = import.meta.env.VITE_CHECKOUT_KIT_DEBUG) {
  return value === 'true'
}

function fallbackReason(descriptor: EmbeddedCheckoutBootstrapProfile): CheckoutLifecycleReason {
  return descriptor.action === 'EXTERNAL_HANDOFF' ? 'MERCHANT_HANDOFF' : 'BOOTSTRAP_FAILED'
}

export function EmbeddedCheckout({
  session,
  surface,
  onReconciled,
  onSessionReleased,
}: Readonly<{
  session: ActiveCheckoutSession
  surface: 'cart' | 'chat'
  onReconciled: (checkout?: CheckoutProfile) => Promise<void> | void
  onSessionReleased?: (outcome: 'completed' | 'cancelled' | 'handoff') => void
}>) {
  const [phase, setPhase] = useState<EmbeddedCheckoutPhase>('preparing')
  const [descriptor, setDescriptor] = useState<EmbeddedCheckoutBootstrapProfile | null>(null)
  const [message, setMessage] = useState(
    'Preparing your secure Merchant checkout — what’s Meant for you is close.',
  )
  const [exitConfirmationOpen, setExitConfirmationOpen] = useState(false)
  const handleRef = useRef<CheckoutKitHandle | null>(null)
  const descriptorRef = useRef<EmbeddedCheckoutBootstrapProfile | null>(null)
  const generationRef = useRef(0)
  const startTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const prepareStartedAtRef = useRef(0)
  const terminalRef = useRef<'verifying' | 'completed' | 'cancelled' | null>(null)
  const actionButtonRef = useRef<HTMLButtonElement | null>(null)
  const onReconciledRef = useRef(onReconciled)
  const onSessionReleasedRef = useRef(onSessionReleased)
  const sessionFallbackUrl = merchantContinueUrl(session)
  const sessionFallbackUrlRef = useRef(sessionFallbackUrl)

  useEffect(() => {
    onReconciledRef.current = onReconciled
  }, [onReconciled])

  useEffect(() => {
    onSessionReleasedRef.current = onSessionReleased
  }, [onSessionReleased])

  useEffect(() => {
    sessionFallbackUrlRef.current = sessionFallbackUrl
  }, [sessionFallbackUrl])

  const clearStartTimer = useCallback(() => {
    if (startTimerRef.current) clearTimeout(startTimerRef.current)
    startTimerRef.current = null
  }, [])

  const destroyHandle = useCallback(() => {
    clearStartTimer()
    handleRef.current?.destroy()
    handleRef.current = null
  }, [clearStartTimer])

  const cancelSession = useCallback(
    async (current: EmbeddedCheckoutBootstrapProfile | null) => {
      if (!current?.sessionId || terminalRef.current === 'completed') return
      try {
        await cancelEmbeddedCheckout({
          cartId: session.cartId,
          sessionId: current.sessionId,
          expectedUserId: session.ownerId,
        })
      } catch {
        // The server session is short-lived and fail-closed; cancellation is best-effort during teardown.
      }
    },
    [session.cartId, session.ownerId],
  )

  const markFallback = useCallback(
    (current: EmbeddedCheckoutBootstrapProfile, reason: CheckoutLifecycleReason, text: string) => {
      destroyHandle()
      setDescriptor(current)
      descriptorRef.current = current
      setPhase('fallback')
      setMessage(text)
      trackCheckoutLifecycleEvent('embedded_checkout_fallback', {
        surface,
        result: current.fallbackContinueUrl ? 'succeeded' : 'failed',
        reason,
      })
    },
    [destroyHandle, surface],
  )

  const verifyCompletion = useCallback(
    async (current: EmbeddedCheckoutBootstrapProfile, generation = generationRef.current) => {
      if (!current.sessionId || terminalRef.current === 'completed') return
      terminalRef.current = 'verifying'
      clearStartTimer()
      setPhase('verifying')
      setMessage('The match looks official. Meant is confirming your order with the Merchant...')
      try {
        const checkout = await completeEmbeddedCheckout({
          cartId: session.cartId,
          sessionId: current.sessionId,
          expectedUserId: session.ownerId,
        })
        if (generation !== generationRef.current) return
        terminalRef.current = 'completed'
        destroyHandle()
        setPhase('completed')
        setMessage('Your purchase is confirmed. It really was Meant to be.')
        trackCheckoutLifecycleEvent('embedded_checkout_complete', {
          surface,
          result: 'succeeded',
          reason: 'NONE',
        })
        await onReconciledRef.current(checkout)
        onSessionReleasedRef.current?.('completed')
      } catch {
        if (generation !== generationRef.current) return
        terminalRef.current = null
        setPhase('error')
        setMessage(
          'The checkout closed, but Meant could not verify completion yet. Reconcile it before retrying.',
        )
        trackCheckoutLifecycleEvent('embedded_checkout_complete', {
          surface,
          result: 'failed',
          reason: 'VERIFICATION_FAILED',
        })
      }
    },
    [clearStartTimer, destroyHandle, session.cartId, session.ownerId, surface],
  )

  const cancelActive = useCallback(
    async (reason: CheckoutLifecycleReason = 'NONE', generation = generationRef.current) => {
      if (terminalRef.current === 'completed' || terminalRef.current === 'cancelled') return
      terminalRef.current = 'cancelled'
      const current = descriptorRef.current
      destroyHandle()
      await cancelSession(current)
      if (generation !== generationRef.current) return
      setPhase('cancelled')
      setMessage('Your cart and secure checkout will be right here when the timing feels Meant.')
      trackCheckoutLifecycleEvent('embedded_checkout_cancel', {
        surface,
        result: 'succeeded',
        reason,
      })
      onSessionReleasedRef.current?.('cancelled')
      actionButtonRef.current?.focus()
    },
    [cancelSession, destroyHandle, surface],
  )

  const prepare = useCallback(async () => {
    const generation = generationRef.current + 1
    generationRef.current = generation
    terminalRef.current = null
    destroyHandle()
    const previous = descriptorRef.current
    descriptorRef.current = null
    setDescriptor(null)
    if (previous?.sessionId) void cancelSession(previous)
    setPhase('preparing')
    setMessage('Preparing your secure Merchant checkout — what’s Meant for you is close.')
    prepareStartedAtRef.current = performance.now()
    trackCheckoutLifecycleEvent('embedded_bootstrap', {
      surface,
      result: 'attempted',
      reason: 'NONE',
    })

    let current: EmbeddedCheckoutBootstrapProfile
    try {
      current = await bootstrapEmbeddedCheckout(session.cartId, {
        expectedUserId: session.ownerId,
      })
    } catch {
      if (generation !== generationRef.current) return
      setPhase('fallback')
      setMessage('Meant could not prepare embedded checkout.')
      trackCheckoutLifecycleEvent('embedded_bootstrap', {
        surface,
        result: 'failed',
        reason: 'BOOTSTRAP_FAILED',
      })
      trackCheckoutLifecycleEvent('embedded_checkout_fallback', {
        surface,
        result: sessionFallbackUrlRef.current ? 'succeeded' : 'failed',
        reason: 'BOOTSTRAP_FAILED',
      })
      return
    }
    if (generation !== generationRef.current) {
      if (current.sessionId) void cancelSession(current)
      return
    }
    setDescriptor(current)
    descriptorRef.current = current
    trackCheckoutLifecycleEvent('embedded_bootstrap', {
      surface,
      result: 'succeeded',
      reason: 'NONE',
    })

    const decision = resolveEmbeddedCheckoutBootstrap(current, embeddedCheckoutEnabled())
    if (decision.mode === 'COMPLETED') {
      setPhase('completed')
      setMessage('This purchase is already complete. It was Meant to be.')
      await onReconciledRef.current()
      onSessionReleasedRef.current?.('completed')
      return
    }
    if (decision.mode === 'FALLBACK') {
      const text =
        decision.reason === 'KILL_SWITCH'
          ? 'Embedded checkout is paused. Continue securely with the merchant.'
          : decision.reason === 'UNSUPPORTED_PROTOCOL'
            ? 'This checkout requires an unsupported embedded protocol mode.'
            : decision.reason === 'SESSION_EXPIRED'
              ? 'The embedded session expired. Prepare a fresh checkout to continue.'
              : (current.reason ?? 'Continue with the merchant checkout.')
      markFallback(current, decision.reason, text)
      if (current.sessionId) void cancelSession(current)
      return
    }
    const checkoutUrl = current.checkoutUrl
    if (!checkoutUrl) {
      markFallback(
        current,
        'BOOTSTRAP_FAILED',
        'The merchant did not return an embedded checkout URL.',
      )
      return
    }

    try {
      const handle = await createCheckoutKitHandle(
        checkoutUrl,
        {
          onReady: () => {
            if (generation !== generationRef.current) return
            setPhase('ready')
            setMessage(
              'Open the secure Merchant checkout to pay. Your product is only one step away.',
            )
            trackCheckoutLifecycleEvent('checkout_kit_ready', {
              surface,
              result: 'succeeded',
              reason: 'NONE',
              latencyMs: performance.now() - prepareStartedAtRef.current,
            })
          },
          onStart: () => {
            if (generation !== generationRef.current) return
            clearStartTimer()
            setPhase('active')
            setMessage(
              'Complete payment in the secure Merchant window, then Meant will confirm everything here.',
            )
            trackCheckoutLifecycleEvent('embedded_checkout_start', {
              surface,
              result: 'succeeded',
              reason: 'NONE',
            })
            if (current.sessionId) {
              // A confirmed Checkout Kit start is durable business input. Keep this retry alive even
              // if close/unmount tears down the short-lived UI session immediately afterwards.
              void acknowledgeEmbeddedCheckoutOpenedWithRetry({
                cartId: session.cartId,
                sessionId: current.sessionId,
                expectedUserId: session.ownerId,
              }).then(
                () => {
                  void Promise.resolve(onReconciledRef.current()).catch(() => undefined)
                },
                () => {
                  trackCheckoutLifecycleEvent('embedded_checkout_error', {
                    surface,
                    result: 'failed',
                    reason: 'START_ACK_FAILED',
                  })
                },
              )
            }
          },
          onComplete: () => {
            if (generation === generationRef.current) void verifyCompletion(current, generation)
          },
          onClose: () => {
            if (generation === generationRef.current && terminalRef.current !== 'verifying') {
              void cancelActive('NONE', generation)
            }
          },
          onError: () => {
            if (generation !== generationRef.current) return
            trackCheckoutLifecycleEvent('embedded_checkout_error', {
              surface,
              result: 'failed',
              reason: 'SDK_ERROR',
            })
            markFallback(
              current,
              'SDK_ERROR',
              'Checkout Kit reported an error. Use the merchant fallback to continue.',
            )
            void cancelSession(current)
          },
        },
        { debug: checkoutKitDebugEnabled() },
      )
      if (generation !== generationRef.current) {
        handle.destroy()
        void cancelSession(current)
        return
      }
      handleRef.current = handle
    } catch (error) {
      const reason =
        error instanceof CheckoutKitAdapterError && error.failure === 'UNSUPPORTED_BROWSER'
          ? 'UNSUPPORTED_BROWSER'
          : 'SDK_LOAD_FAILED'
      markFallback(
        current,
        reason,
        'This browser cannot open Checkout Kit. Continue with the merchant checkout.',
      )
      void cancelSession(current)
    }
  }, [
    cancelActive,
    cancelSession,
    clearStartTimer,
    destroyHandle,
    markFallback,
    session.cartId,
    session.ownerId,
    surface,
    verifyCompletion,
  ])

  useEffect(() => {
    void prepare()
    return () => {
      generationRef.current += 1
      destroyHandle()
      const current = descriptorRef.current
      if (current?.sessionId && terminalRef.current !== 'completed') void cancelSession(current)
    }
  }, [cancelSession, destroyHandle, prepare])

  const openCheckout = () => {
    if (!descriptor || !handleRef.current) return
    if (embeddedCheckoutSessionExpired(descriptor)) {
      const generation = generationRef.current
      void cancelActive('SESSION_EXPIRED', generation).then(() => {
        if (generation === generationRef.current) void prepare()
      })
      return
    }
    setPhase('opening')
    setMessage('Opening your secure Merchant checkout...')
    trackCheckoutLifecycleEvent('embedded_checkout_start', {
      surface,
      result: 'attempted',
      reason: 'NONE',
    })
    handleRef.current.open()
    clearStartTimer()
    startTimerRef.current = setTimeout(() => {
      trackCheckoutLifecycleEvent('embedded_checkout_error', {
        surface,
        result: 'failed',
        reason: 'START_TIMEOUT',
      })
      markFallback(
        descriptor,
        'START_TIMEOUT',
        'The checkout window did not respond. Continue with the merchant fallback.',
      )
      void cancelSession(descriptor)
    }, START_TIMEOUT_MS)
  }

  const confirmCancel = () => setExitConfirmationOpen(true)

  const reconcile = async () => {
    const generation = generationRef.current
    trackCheckoutLifecycleEvent('embedded_checkout_recovery', {
      surface,
      result: 'attempted',
      reason: phase === 'error' ? 'VERIFICATION_FAILED' : 'NONE',
    })
    if (descriptor?.sessionId && phase === 'error') {
      await verifyCompletion(descriptor, generation)
      return
    }
    await onReconciledRef.current()
    if (generation !== generationRef.current) return
    await prepare()
  }

  const fallbackUrl = safeExternalCheckoutUrl(descriptor?.fallbackContinueUrl) ?? sessionFallbackUrl

  return (
    <>
      <EmbeddedCheckoutView
        phase={phase}
        message={message}
        fallbackUrl={fallbackUrl}
        actionButtonRef={actionButtonRef}
        onOpen={openCheckout}
        onFocus={() => handleRef.current?.focus()}
        onCancel={confirmCancel}
        onPrepare={() => void prepare()}
        onReconcile={() => void reconcile()}
        onFallback={() => {
          trackCheckoutLifecycleEvent('embedded_checkout_fallback', {
            surface,
            result: 'attempted',
            reason: descriptor ? fallbackReason(descriptor) : 'BOOTSTRAP_FAILED',
          })
          void cancelSession(descriptor)
          onSessionReleasedRef.current?.('handoff')
        }}
      />
      <CheckoutExitConfirmation
        open={exitConfirmationOpen}
        onKeepOpen={() => setExitConfirmationOpen(false)}
        onCloseCheckout={() => {
          setExitConfirmationOpen(false)
          void cancelActive()
        }}
      />
    </>
  )
}
