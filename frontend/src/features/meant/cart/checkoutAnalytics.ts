export type CheckoutLifecycleEventName =
  | 'embedded_bootstrap'
  | 'checkout_kit_ready'
  | 'embedded_checkout_start'
  | 'embedded_checkout_complete'
  | 'embedded_checkout_cancel'
  | 'embedded_checkout_error'
  | 'embedded_checkout_recovery'
  | 'embedded_checkout_fallback'

export type CheckoutLifecycleReason =
  | 'NONE'
  | 'KILL_SWITCH'
  | 'UNSUPPORTED_BROWSER'
  | 'UNSUPPORTED_PROTOCOL'
  | 'SESSION_EXPIRED'
  | 'BOOTSTRAP_FAILED'
  | 'SDK_LOAD_FAILED'
  | 'SDK_ERROR'
  | 'START_TIMEOUT'
  | 'START_ACK_FAILED'
  | 'VERIFICATION_FAILED'
  | 'POPUP_BLOCKED'
  | 'MERCHANT_HANDOFF'

export interface CheckoutLifecycleEventFields {
  surface: 'cart' | 'chat'
  result: 'attempted' | 'succeeded' | 'failed'
  reason?: CheckoutLifecycleReason
  latencyMs?: number
}

export function checkoutLifecycleEventDetail(
  name: CheckoutLifecycleEventName,
  fields: CheckoutLifecycleEventFields,
): Readonly<{ name: CheckoutLifecycleEventName; fields: CheckoutLifecycleEventFields }> {
  const latencyMs =
    fields.latencyMs == null
      ? undefined
      : Math.max(0, Math.min(120_000, Math.round(fields.latencyMs)))
  return {
    name,
    fields: {
      surface: fields.surface,
      result: fields.result,
      reason: fields.reason,
      latencyMs,
    },
  }
}

export function trackCheckoutLifecycleEvent(
  name: CheckoutLifecycleEventName,
  fields: CheckoutLifecycleEventFields,
): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(
    new CustomEvent('meant:checkout-analytics', {
      detail: checkoutLifecycleEventDetail(name, fields),
    }),
  )
}
