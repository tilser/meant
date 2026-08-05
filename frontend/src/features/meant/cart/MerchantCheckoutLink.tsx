import type { ActiveCheckoutSession } from './checkoutTypes'
import { merchantCheckoutUrl } from './checkoutSessionUi'
import { trackMeantEvent } from '../analytics'

export function MerchantCheckoutLink({
  session,
  label = 'Continue on merchant site',
  className,
}: Readonly<{
  session: ActiveCheckoutSession
  label?: string
  className?: string | null
}>) {
  const merchantUrl = merchantCheckoutUrl(session)
  if (!merchantUrl) {
    return null
  }
  const resolvedClassName =
    className === undefined ? 'mt-ct-cobtn mt-ct-cobtn-link' : (className ?? undefined)
  return (
    <a
      className={resolvedClassName}
      href={merchantUrl}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={`${label} (opens in a new tab)`}
      onClick={() => trackMeantEvent('merchant_outbound_clicked')}
    >
      {label}
    </a>
  )
}
