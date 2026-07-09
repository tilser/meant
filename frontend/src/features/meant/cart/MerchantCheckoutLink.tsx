import type { ActiveCheckoutSession } from './checkoutTypes'
import { merchantCheckoutUrl } from './checkoutSessionUi'

export function MerchantCheckoutLink({
  session,
}: Readonly<{
  session: ActiveCheckoutSession
}>) {
  const merchantUrl = merchantCheckoutUrl(session)
  if (!merchantUrl) {
    return null
  }
  return (
    <a className="mt-ct-cobtn mt-ct-cobtn-link" href={merchantUrl} target="_blank" rel="noreferrer">
      Continue on merchant site
    </a>
  )
}
