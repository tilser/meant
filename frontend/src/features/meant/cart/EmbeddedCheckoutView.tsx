import type { RefObject } from 'react'

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
      <div className="mt-embedded-checkout-copy">
        <strong>
          {phase === 'fallback'
            ? 'Checkout inside Meant is not available for this Merchant'
            : 'Checkout inside Meant'}
        </strong>
        <span>
          {phase === 'fallback' && fallbackUrl
            ? 'Please continue to Merchant checkout to finish your order.'
            : message}
        </span>
      </div>
      <div className="mt-embedded-checkout-actions">
        {phase === 'ready' || phase === 'cancelled' ? (
          <>
            <button
              ref={actionButtonRef}
              type="button"
              onClick={phase === 'ready' ? onOpen : onPrepare}
            >
              {phase === 'ready' ? 'Open secure checkout' : 'Prepare again'}
            </button>
            {phase === 'ready' ? (
              <button type="button" className="secondary" onClick={onCancel}>
                Close checkout
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
              Close checkout
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
            aria-label="Open Merchant checkout (opens in a new tab)"
            onClick={onFallback}
          >
            Open Merchant checkout
          </a>
        ) : null}
        {phase === 'fallback' || phase === 'error' ? (
          <>
            <button type="button" className="secondary" onClick={onPrepare}>
              Try embedded checkout again
            </button>
            <button type="button" className="secondary" onClick={onCancel}>
              Close checkout
            </button>
          </>
        ) : null}
      </div>
    </div>
  )
}
