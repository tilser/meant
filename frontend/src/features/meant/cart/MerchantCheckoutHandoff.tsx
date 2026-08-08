import type { ActiveCheckoutSession } from './checkoutTypes'
import { MeantHeartMark } from '../shared/ui'
import { MerchantCheckoutLink } from './MerchantCheckoutLink'
import { merchantContinueUrl } from './checkoutSessionUi'
import { merchantDisplayOrigin } from './merchantOrigin'

export function MerchantCheckoutHandoff({
  session,
  busy,
  onRefresh,
}: Readonly<{
  session: ActiveCheckoutSession
  busy: boolean
  onRefresh: () => Promise<void> | void
}>) {
  const merchantUrl = merchantContinueUrl(session)
  const merchantDisplay = merchantDisplayOrigin(session.merchantOrigin)

  return (
    <div className="mt-embedded-checkout merchant-handoff" aria-live="polite">
      <div className="mt-embedded-checkout-status">
        <span className="mt-embedded-checkout-mark" aria-hidden>
          <MeantHeartMark size={25} />
        </span>
        <div className="mt-embedded-checkout-copy">
          <small className="mt-mono">Secure Merchant handoff</small>
          <strong>One last step with {merchantDisplay}.</strong>
          <span>
            We prepared your cart. {merchantDisplay} will open in a new tab so you can finish the
            secure payment step.
          </span>
        </div>
      </div>
      <div className="mt-embedded-checkout-actions">
        {merchantUrl ? (
          <MerchantCheckoutLink session={session} label="Continue securely" className={null} />
        ) : (
          <button type="button" onClick={() => void onRefresh()} disabled={busy}>
            {busy ? 'Finding the way...' : 'Prepare secure checkout'}
          </button>
        )}
      </div>
    </div>
  )
}
