import type { RefObject } from 'react'

import { MeantHeartMark, SparkMark } from '../shared/ui'

export type EmbeddedCheckoutPhase =
  | 'preparing'
  | 'ready'
  | 'opening'
  | 'active'
  | 'verifying'
  | 'completed'
  | 'cancelled'
  | 'fallback'
  | 'error'

function embeddedCheckoutHeading(phase: EmbeddedCheckoutPhase): string {
  if (phase === 'completed') return 'Meant to be.'
  if (phase === 'ready') return 'Your secure checkout is ready.'
  if (phase === 'opening') return 'Opening the final chapter...'
  if (phase === 'active') return 'Your match is in motion.'
  if (phase === 'verifying') return 'Making it official...'
  if (phase === 'cancelled') return 'Saved for when you’re ready.'
  if (phase === 'fallback') return 'A short detour — still Meant for you.'
  if (phase === 'error') return 'This path needs a quick reset.'
  return 'Bringing you together...'
}

export function EmbeddedCheckoutView({
  phase,
  message,
  fallbackUrl,
  actionButtonRef,
  onOpen,
  onFocus,
  onCancel,
  onPrepare,
  onReconcile,
  onFallback,
}: Readonly<{
  phase: EmbeddedCheckoutPhase
  message: string
  fallbackUrl: string | null
  actionButtonRef: RefObject<HTMLButtonElement | null>
  onOpen: () => void
  onFocus: () => void
  onCancel: () => void
  onPrepare: () => void
  onReconcile: () => void
  onFallback: () => void
}>) {
  return (
    <div className={`mt-embedded-checkout ${phase}`} aria-live="polite">
      <div className="mt-embedded-checkout-status">
        <span className="mt-embedded-checkout-mark" aria-hidden>
          {phase === 'fallback' || phase === 'error' ? (
            <SparkMark size={18} />
          ) : (
            <MeantHeartMark size={25} />
          )}
        </span>
        <div className="mt-embedded-checkout-copy">
          <small className="mt-mono">
            {phase === 'fallback' ? 'Secure Merchant checkout' : 'Checkout inside Meant'}
          </small>
          <strong>{embeddedCheckoutHeading(phase)}</strong>
          <span>
            {phase === 'fallback' && fallbackUrl
              ? 'Continue securely with the Merchant in a new tab. Your prepared cart will be waiting there.'
              : message}
          </span>
        </div>
      </div>
      <div className="mt-embedded-checkout-actions">
        {phase === 'ready' || phase === 'cancelled' ? (
          <>
            <button
              ref={actionButtonRef}
              type="button"
              onClick={phase === 'ready' ? onOpen : onPrepare}
            >
              {phase === 'ready' ? 'Open secure checkout' : 'Bring it back'}
            </button>
            {phase === 'ready' ? (
              <button type="button" className="secondary" onClick={onCancel}>
                Not just yet
              </button>
            ) : null}
          </>
        ) : null}
        {phase === 'active' || phase === 'opening' ? (
          <>
            <button type="button" onClick={onFocus}>
              Return to checkout
            </button>
            <button type="button" className="secondary" onClick={onCancel}>
              Close for now
            </button>
          </>
        ) : null}
        {phase === 'error' ? (
          <button ref={actionButtonRef} type="button" onClick={onReconcile}>
            Reconcile checkout
          </button>
        ) : null}
        {phase === 'fallback' && fallbackUrl ? (
          <a
            href={fallbackUrl}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Continue securely with the Merchant (opens in a new tab)"
            onClick={onFallback}
          >
            Continue securely
          </a>
        ) : null}
        {phase === 'fallback' || phase === 'error' ? (
          <>
            <button type="button" className="secondary" onClick={onPrepare}>
              Try inside Meant again
            </button>
            <button type="button" className="secondary" onClick={onCancel}>
              Close for now
            </button>
          </>
        ) : null}
      </div>
    </div>
  )
}
