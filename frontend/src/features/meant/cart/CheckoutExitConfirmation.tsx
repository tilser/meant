import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'

import { CartIcon, CloseIcon, MeantHeartMark } from '../shared/ui'

export function CheckoutExitConfirmation({
  open,
  onKeepOpen,
  onCloseCheckout,
}: Readonly<{
  open: boolean
  onKeepOpen: () => void
  onCloseCheckout: () => void
}>) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    if (!open) return

    const previousFocus = document.activeElement as HTMLElement | null
    const frame = window.requestAnimationFrame(() => {
      dialogRef.current?.querySelector<HTMLElement>('[data-checkout-exit-primary]')?.focus()
    })
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        onKeepOpen()
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return

      const focusable = [
        ...dialogRef.current.querySelectorAll<HTMLElement>('button, a[href]'),
      ].filter((element) => !element.hasAttribute('disabled'))
      if (focusable.length === 0) return

      const first = focusable[0]
      const last = focusable.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last?.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first?.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => {
      window.cancelAnimationFrame(frame)
      document.removeEventListener('keydown', onKeyDown)
      previousFocus?.focus()
    }
  }, [onKeepOpen, open])

  if (!open) return null

  const confirmation = (
    <div
      className="mt-checkout-exit-backdrop"
      onMouseDown={(event) => {
        event.stopPropagation()
        if (event.target === event.currentTarget) onKeepOpen()
      }}
    >
      <div
        className="mt-checkout-exit-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        ref={dialogRef}
      >
        <button
          className="mt-checkout-exit-dismiss"
          type="button"
          onClick={onKeepOpen}
          aria-label="Keep checkout open"
        >
          <CloseIcon size={14} />
        </button>

        <div className="mt-checkout-exit-brand">
          <span className="mt-checkout-exit-mark" aria-hidden>
            <MeantHeartMark size={24} />
          </span>
          <span className="mt-mono">
            <em>Meant</em> for later
          </span>
        </div>

        <h2 id={titleId}>
          Not <em>Meant</em> to be — just yet?
        </h2>
        <p id={descriptionId}>
          No hard feelings. Close checkout for now and your items will stay safe in your cart. You
          can reopen it whenever you’re ready.
        </p>

        <div className="mt-checkout-exit-assurance">
          <span className="mt-checkout-exit-cart" aria-hidden>
            <CartIcon />
          </span>
          <span>
            <strong>Your cart stays right here.</strong>
            <small>Everything will be waiting when you come back.</small>
          </span>
        </div>

        <div className="mt-checkout-exit-actions">
          <button className="primary" type="button" onClick={onKeepOpen} data-checkout-exit-primary>
            Keep checkout open
          </button>
          <button type="button" onClick={onCloseCheckout}>
            Close for now
          </button>
        </div>
      </div>
    </div>
  )

  return typeof document === 'undefined' ? confirmation : createPortal(confirmation, document.body)
}
