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
        <strong>Checkout inside Meant</strong>
        <span>{message}</span>
      </div>
      <div className="mt-embedded-checkout-actions">
        {phase === 'ready' || phase === 'cancelled' ? (
          <button
            ref={actionButtonRef}
            type="button"
            onClick={phase === 'ready' ? onOpen : onPrepare}
          >
            {phase === 'ready' ? 'Open secure checkout' : 'Prepare again'}
          </button>
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
          <a href={fallbackUrl} target="_blank" rel="noopener noreferrer" onClick={onFallback}>
            Continue with merchant
          </a>
        ) : null}
        {phase === 'fallback' || phase === 'error' ? (
          <button type="button" className="secondary" onClick={onPrepare}>
            Try embedded checkout again
          </button>
        ) : null}
      </div>
    </div>
  )
}
