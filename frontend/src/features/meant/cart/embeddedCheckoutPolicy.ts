import type { EmbeddedCheckoutBootstrapProfile } from '../../../lib/apiClient'
import type { CheckoutLifecycleReason } from './checkoutAnalytics'
import { CHECKOUT_KIT_ECP_VERSION } from './checkoutKitAdapter'

export type EmbeddedCheckoutBootstrapDecision =
  | { mode: 'EMBEDDED'; reason: 'NONE' }
  | { mode: 'COMPLETED'; reason: 'NONE' }
  | { mode: 'FALLBACK'; reason: CheckoutLifecycleReason }

export function embeddedCheckoutEnabled(value = import.meta.env.VITE_EMBEDDED_CHECKOUT_ENABLED) {
  return value === 'true'
}

export function embeddedCheckoutSessionExpired(
  descriptor: EmbeddedCheckoutBootstrapProfile,
  now = Date.now(),
): boolean {
  if (!descriptor.expiresAt) return true
  const expiry = Date.parse(descriptor.expiresAt)
  return !Number.isFinite(expiry) || expiry <= now
}

export function resolveEmbeddedCheckoutBootstrap(
  descriptor: EmbeddedCheckoutBootstrapProfile,
  enabled: boolean,
  now = Date.now(),
): EmbeddedCheckoutBootstrapDecision {
  if (descriptor.action === 'COMPLETED') return { mode: 'COMPLETED', reason: 'NONE' }
  if (descriptor.action !== 'EMBEDDED' || !descriptor.sessionId || !descriptor.checkoutUrl) {
    return {
      mode: 'FALLBACK',
      reason: descriptor.action === 'EXTERNAL_HANDOFF' ? 'MERCHANT_HANDOFF' : 'BOOTSTRAP_FAILED',
    }
  }
  if (!enabled) return { mode: 'FALLBACK', reason: 'KILL_SWITCH' }
  if (
    descriptor.protocolVersion !== CHECKOUT_KIT_ECP_VERSION ||
    (descriptor.allowedDelegations?.length ?? 0) > 0 ||
    descriptor.ecAuth
  ) {
    return { mode: 'FALLBACK', reason: 'UNSUPPORTED_PROTOCOL' }
  }
  if (embeddedCheckoutSessionExpired(descriptor, now)) {
    return { mode: 'FALLBACK', reason: 'SESSION_EXPIRED' }
  }
  return { mode: 'EMBEDDED', reason: 'NONE' }
}

export function safeExternalCheckoutUrl(value: string | null | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password) return null
    return url.toString()
  } catch {
    return null
  }
}
