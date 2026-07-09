import { useState } from 'react'

import type { ActiveCheckoutSession } from './checkoutTypes'
import { merchantCheckoutUrl } from './checkoutSessionUi'

/**
 * Embedded merchant checkout, shown only when the merchant escalates (returns an
 * error or requires_escalation) and the UCP flow cannot continue natively. The
 * backend probes X-Frame-Options/CSP for the continue_url; when the merchant
 * blocks embedding we fall back to an external link.
 */
export function MerchantCheckoutFrame({
  session,
}: Readonly<{
  session: ActiveCheckoutSession
}>) {
  const [frameFailed, setFrameFailed] = useState(false)
  const merchantUrl = merchantCheckoutUrl(session)
  if (!merchantUrl) {
    return null
  }
  const embeddable = session.profile.embeddableCheckout !== false && !frameFailed
  if (!embeddable) {
    return (
      <a
        className="mt-ct-cobtn mt-ct-cobtn-link"
        href={merchantUrl}
        target="_blank"
        rel="noreferrer"
      >
        Continue on merchant site
      </a>
    )
  }
  return (
    <div className="mt-checkout-embed">
      <iframe
        className="mt-checkout-embed-frame"
        src={merchantUrl}
        title={`${session.merchant} checkout`}
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
        referrerPolicy="strict-origin-when-cross-origin"
        onError={() => setFrameFailed(true)}
      />
      <a className="mt-checkout-embed-open" href={merchantUrl} target="_blank" rel="noreferrer">
        Open in a new tab instead
      </a>
    </div>
  )
}
