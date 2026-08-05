import { type FormEvent, useEffect, useRef, useState } from 'react'

import { trackMeantEvent } from '../analytics'
import { MeantHeartMark } from '../shared/ui'
import type { AuthActions } from './useSupabaseAuth'

export type AuthSheetReason =
  'generic' | 'save-product' | 'preferences' | 'new-conversation' | 'cart' | 'checkout'

const copy: Record<AuthSheetReason, { title: string; body: string }> = {
  generic: {
    title: 'You and Meant were meant to be.',
    body: 'Make it official and keep every chat, find, cart and preference that is Meant for you—wherever you go.',
  },
  'save-product': {
    title: 'This one looks Meant for you.',
    body: 'Save this find—and why it fits—so it is right here when you come back.',
  },
  preferences: {
    title: 'Let Meant remember what makes it yours.',
    body: 'Save these rules so every future find feels Meant for you.',
  },
  'new-conversation': {
    title: 'Some conversations are Meant to continue.',
    body: 'Save this one, then start another without losing a single find.',
  },
  cart: {
    title: 'Your cart is Meant to stay.',
    body: 'Keep every pick safe here. Log in only when you are ready to check out.',
  },
  checkout: {
    title: 'This cart is Meant to be yours.',
    body: 'Log in to check out—everything in your cart will wait right here.',
  },
}

const GoogleGlyph = (
  <svg width="17" height="17" viewBox="0 0 18 18" aria-hidden="true">
    <path
      d="M17.6 9.2c0-.6-.05-1.18-.16-1.74H9v3.3h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.64-3.88 2.64-6.54z"
      fill="#4285F4"
    />
    <path
      d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18z"
      fill="#34A853"
    />
    <path d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33z" fill="#FBBC05" />
    <path
      d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.47.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58z"
      fill="#EA4335"
    />
  </svg>
)

const AppleGlyph = (
  <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" fill="currentColor">
    <path d="M11.18 8.5c-.02-1.66 1.36-2.46 1.42-2.5-.77-1.13-1.98-1.29-2.4-1.3-1.02-.1-1.99.6-2.5.6-.52 0-1.31-.59-2.16-.57-1.1.02-2.13.65-2.7 1.64-1.16 2.01-.3 4.98.83 6.6.55.8 1.21 1.69 2.07 1.66.83-.03 1.15-.54 2.15-.54 1 0 1.29.54 2.16.52.9-.01 1.46-.81 2-1.61.64-.92.9-1.82.91-1.86-.02-.01-1.75-.67-1.78-2.64zM9.6 3.62c.45-.55.76-1.31.67-2.07-.65.03-1.45.44-1.92.98-.42.48-.79 1.26-.69 2 .73.06 1.48-.37 1.94-.91z" />
  </svg>
)

export function AuthSheet({
  open,
  reason,
  auth,
  onClose,
  onExistingAccountSignIn,
}: Readonly<{
  open: boolean
  reason: AuthSheetReason
  auth: Pick<AuthActions, 'linkOAuthIdentity' | 'linkEmailIdentity'>
  onClose: () => void
  onExistingAccountSignIn: () => Promise<void> | void
}>) {
  const sheetRef = useRef<HTMLDivElement | null>(null)
  const [emailOpen, setEmailOpen] = useState(false)
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    if (!open) return
    trackMeantEvent('auth_sheet_viewed', { reason })
    const previousFocus = document.activeElement as HTMLElement | null
    const frame = window.requestAnimationFrame(() =>
      sheetRef.current?.querySelector<HTMLElement>('button')?.focus(),
    )
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab' || !sheetRef.current) return
      const focusable = [
        ...sheetRef.current.querySelectorAll<HTMLElement>('button, input, a[href]'),
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
  }, [onClose, open, reason])

  if (!open) return null
  const selected = copy[reason]

  const oauth = async (provider: 'google' | 'apple') => {
    setError(null)
    setPending(true)
    trackMeantEvent('auth_method_selected', { auth_method: provider })
    const result = await auth.linkOAuthIdentity(provider)
    setPending(false)
    if (result.error) setError(result.error)
  }

  const submitEmail = async (event: FormEvent) => {
    event.preventDefault()
    if (!email.trim() || pending) return
    setPending(true)
    setError(null)
    trackMeantEvent('auth_method_selected', { auth_method: 'email' })
    const result = await auth.linkEmailIdentity(email.trim())
    setPending(false)
    if (result.error) setError(result.error)
    else setNotice('Check your inbox—your way back to everything Meant for you is on its way.')
  }

  const existingAccount = async () => {
    if (pending) return
    setPending(true)
    setError(null)
    trackMeantEvent('auth_method_selected', { auth_method: 'existing-account' })
    try {
      await onExistingAccountSignIn()
    } catch (cause) {
      setPending(false)
      setError(cause instanceof Error ? cause.message : 'Could not prepare your current search.')
    }
  }

  return (
    <div
      className="mt-auth-sheet-backdrop"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <div
        className="mt-auth-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="mt-auth-sheet-title"
        ref={sheetRef}
      >
        <button className="mt-auth-sheet-close" type="button" onClick={onClose} aria-label="Close">
          ×
        </button>
        <div className="mt-auth-sheet-mark" aria-hidden>
          <MeantHeartMark size={22} />
        </div>
        <div className="mt-mono mt-auth-sheet-eyebrow">Make it Meant</div>
        <h2 id="mt-auth-sheet-title">{selected.title}</h2>
        <p className="mt-auth-sheet-copy">{selected.body}</p>
        <div className="mt-auth-sheet-benefit" aria-label="What stays with your account">
          <span className="mt-auth-sheet-check" aria-hidden>
            ✓
          </span>
          <span>Everything Meant for you, always within reach.</span>
        </div>
        <div className="mt-auth-sheet-actions">
          <button
            className="mt-auth-sheet-provider"
            type="button"
            disabled={pending}
            onClick={() => void oauth('google')}
          >
            {GoogleGlyph}
            <span>Continue with Google</span>
          </button>
          <button
            className="mt-auth-sheet-provider"
            type="button"
            disabled={pending}
            onClick={() => void oauth('apple')}
          >
            {AppleGlyph}
            <span>Continue with Apple</span>
          </button>
          <div className="mt-auth-sheet-divider">
            <span>or</span>
          </div>
          {!emailOpen ? (
            <button
              className="mt-auth-sheet-email"
              type="button"
              disabled={pending}
              onClick={() => setEmailOpen(true)}
            >
              Continue with email
            </button>
          ) : (
            <form onSubmit={(event) => void submitEmail(event)}>
              <label>
                <span className="mt-mono">Email</span>
                <input
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>
              <button
                className="mt-auth-sheet-email"
                type="submit"
                disabled={!email.trim() || pending}
              >
                {pending ? 'Sending…' : 'Email me a link'}
              </button>
            </form>
          )}
        </div>
        {error ? (
          <div className="mt-auth-error" role="alert">
            {error}
          </div>
        ) : null}
        {notice ? (
          <div className="mt-reset-ok" role="status">
            {notice}
          </div>
        ) : null}
        <button
          className="mt-auth-sheet-signin"
          type="button"
          disabled={pending}
          onClick={() => void existingAccount()}
        >
          Already have a Meant account? Log in
        </button>
        <small>Free. No card. Just more of what is Meant for you.</small>
      </div>
    </div>
  )
}
