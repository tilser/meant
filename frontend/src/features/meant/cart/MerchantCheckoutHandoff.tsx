import type { ActiveCheckoutSession } from './checkoutTypes'
import { MerchantCheckoutLink } from './MerchantCheckoutLink'
import { merchantContinueUrl } from './checkoutSessionUi'

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

  return (
    <div className="mt-embedded-checkout merchant-handoff" aria-live="polite">
      <div className="mt-embedded-checkout-copy">
        <strong>Checkout inside Meant is not available for this Merchant</strong>
        <span>Please continue to Merchant checkout to finish your order.</span>
      </div>
      <div className="mt-embedded-checkout-actions">
        {merchantUrl ? (
          <MerchantCheckoutLink session={session} label="Open Merchant checkout" className={null} />
        ) : (
          <button type="button" onClick={() => void onRefresh()} disabled={busy}>
            {busy ? 'Checking...' : 'Get Merchant checkout link'}
          </button>
        )}
      </div>
    </div>
  )
}
