import type { CheckoutCompletionProfile, CheckoutProfile } from '../../../lib/apiClient'
import type { ActiveCheckoutSession } from './checkoutTypes'
import { safeExternalCheckoutUrl } from './embeddedCheckoutPolicy'
import { merchantDisplayOrigin } from './merchantOrigin'
import { merchantDeliveryCoverageSummary } from '../utils'
import { savedCheckoutDetails } from './savedCheckoutDetails'

// Merchant message content is localized (e.g. French for balibaris.com), so buyer-input
// detection relies on the standardized UCP message codes and paths instead of text.
const BUYER_INPUT_CODES = new Set([
  'delivery_address_required',
  'missing_shipping_address',
  'address_invalid',
  'delivery_address_invalid',
  'address_undeliverable',
  'delivery_no_delivery_available_for_merchandise_line',
  'buyer_identity_required',
  'missing_buyer_identity',
])

function messageNeedsBuyerDetails(message: {
  code?: string | null
  path?: string | null
}): boolean {
  const code = message.code?.trim().toLowerCase() ?? ''
  if (code.startsWith('buyer_identity') || BUYER_INPUT_CODES.has(code)) {
    return true
  }
  const path = message.path?.trim().toLowerCase() ?? ''
  return path.startsWith('$.buyer') || path.includes('destination') || path.includes('delivery')
}

function checkoutHasBuyerDetailMessages(profile: CheckoutProfile | null | undefined): boolean {
  return profile?.messages?.some((message) => messageNeedsBuyerDetails(message)) ?? false
}

function checkoutHasExtensionInteraction(profile: CheckoutProfile | null | undefined): boolean {
  return (
    profile?.messages?.some(
      (message) => message.code?.trim().toLowerCase() === 'extension_interaction_required',
    ) ?? false
  )
}

export function checkoutRequiresMerchantRedirect(
  profile: CheckoutProfile | null | undefined,
): boolean {
  return (
    profile?.nextAction === 'HANDOFF' &&
    profile.selectedRail === 'MERCHANT_HANDOFF' &&
    (profile.ineligibilityReasons?.includes('MERCHANT_REDIRECT_REQUIRED') ?? false)
  )
}

function checkoutNeedsMerchantInput(
  profile: CheckoutProfile | null | undefined,
  completion: CheckoutCompletionProfile | null,
): boolean {
  const messages = [
    ...(profile?.messages?.map((message) => ({
      code: message.code,
      content: message.content,
      path: message.path,
    })) ?? []),
    ...(completion?.messages?.map((message) => ({
      code: null,
      content: message,
      path: null,
    })) ?? []),
  ]
  return messages.some((message) => {
    const text = [message.code, message.content, message.path]
      .filter((value): value is string => Boolean(value))
      .join(' ')
      .toLowerCase()
    return (
      text.includes('destination address') ||
      text.includes('shipping address') ||
      text.includes('delivery address') ||
      text.includes('shipping method') ||
      text.includes('delivery option') ||
      text.includes('cannot be shipped') ||
      text.includes("can't be shipped") ||
      text.includes('fulfillment')
    )
  })
}

export function merchantContinueUrl(session: ActiveCheckoutSession): string | null {
  return (
    safeExternalCheckoutUrl(session.completion?.continueUrl) ??
    safeExternalCheckoutUrl(session.profile.continueUrl)
  )
}

export function merchantCheckoutUrl(session: ActiveCheckoutSession): string | null {
  return merchantContinueUrl(session) ?? safeExternalCheckoutUrl(session.profile.checkoutUrl)
}

export function checkoutNeedsAddress(session: ActiveCheckoutSession): boolean {
  if (checkoutRequiresMerchantRedirect(session.profile)) {
    return false
  }
  return (
    checkoutHasBuyerDetailMessages(session.profile) ||
    checkoutNeedsMerchantInput(session.profile, session.completion)
  )
}

export function checkoutShouldOfferSavedDetails(
  session: ActiveCheckoutSession,
  dismissed: boolean,
): boolean {
  return (
    !dismissed && checkoutNeedsAddress(session) && Boolean(savedCheckoutDetails(session.profile))
  )
}

export function checkoutShouldRefreshAfterDetails(
  profile: CheckoutProfile | null | undefined,
): boolean {
  if (profile?.nextAction !== 'UPDATE_CHECKOUT' && profile?.nextAction !== 'UNKNOWN') {
    return false
  }
  return !checkoutHasBuyerDetailMessages(profile) && !checkoutNeedsMerchantInput(profile, null)
}

export function checkoutReadyForPayment(session: ActiveCheckoutSession): boolean {
  const nextAction = session.profile.nextAction
  if (nextAction) {
    return nextAction === 'COMPLETE_CHECKOUT' || nextAction === 'OPEN_EMBEDDED_CHECKOUT'
  }
  const normalizedStatus = session.profile.status?.trim().toLowerCase() ?? ''
  return normalizedStatus === 'ready_for_complete' || normalizedStatus === 'ready_for_payment'
}

export function checkoutNeedsHandoff(session: ActiveCheckoutSession): boolean {
  const normalizedStatus = session.profile.status?.trim().toLowerCase() ?? ''
  const nextAction = session.profile.nextAction
  if (checkoutRequiresMerchantRedirect(session.profile)) {
    return true
  }
  if (session.completion?.status === 'SCA_REQUIRED') {
    return true
  }
  if (nextAction) {
    return nextAction === 'HANDOFF' || session.profile.selectedRail === 'MERCHANT_HANDOFF'
  }
  return (
    !checkoutNeedsAddress(session) &&
    (Boolean(session.profile.requiresEscalation) ||
      normalizedStatus === 'requires_escalation' ||
      normalizedStatus === 'ready_for_complete' ||
      normalizedStatus === 'ready_for_payment')
  )
}

export function checkoutUsesEmbeddedCheckout(session: ActiveCheckoutSession): boolean {
  return (
    session.profile.nextAction === 'OPEN_EMBEDDED_CHECKOUT' &&
    !checkoutRequiresMerchantRedirect(session.profile)
  )
}

export function checkoutPhase(session: ActiveCheckoutSession): string {
  if (checkoutNeedsAddress(session)) {
    return 'address'
  }
  if (checkoutNeedsHandoff(session)) {
    return 'merchant-handoff'
  }
  if (checkoutUsesEmbeddedCheckout(session)) {
    return 'embedded-checkout'
  }
  if (session.profile.selectedRail === 'DIRECT_CHECKOUT_COMPLETION') {
    return 'direct-checkout-completion'
  }
  return 'checkout-session'
}

export function checkoutAssistantPrompt(session: ActiveCheckoutSession): string {
  const merchantUrl = merchantCheckoutUrl(session)
  const continueUrl = merchantContinueUrl(session)
  const merchantDisplay = merchantDisplayOrigin(session.merchantOrigin)
  const coverage = merchantDeliveryCoverageSummary(merchantDisplay)
  if (checkoutRequiresMerchantRedirect(session.profile)) {
    return continueUrl
      ? 'Checkout inside Meant is not available for this Merchant. Please continue to Merchant checkout below.'
      : 'Checkout inside Meant is not available for this Merchant, but it has not returned a checkout link yet.'
  }
  if (checkoutNeedsAddress(session)) {
    if (savedCheckoutDetails(session.profile)) {
      return `I need shipping and contact details before I can continue with ${merchantDisplay}. You can reuse your saved details below or enter different details.`
    }
    return [
      `I need shipping and contact details before I can continue with ${merchantDisplay}.`,
      coverage,
      'Send them here in one message, for example: "Ship to 1531 Hyde St, San Francisco, CA 94109, US, John Novak, john.novak@gmail.com, +1 415 555 0137".',
    ].join(' ')
  }
  if (session.profile.nextAction === 'OPEN_EMBEDDED_CHECKOUT') {
    return 'This merchant supports embedded checkout. The checkout is ready to continue on the embedded rail.'
  }
  if (session.profile.nextAction === 'COMPLETE_CHECKOUT') {
    return 'This checkout is authorized and ready for direct completion inside Meant.'
  }
  if (checkoutNeedsHandoff(session)) {
    return merchantUrl
      ? checkoutHasExtensionInteraction(session.profile)
        ? 'The merchant requires additional interaction in its checkout. I prepared the details available through UCP; continue below.'
        : 'The merchant requires you to continue in its checkout. I prepared the details available through UCP; continue below.'
      : 'The merchant requires checkout interaction, but it has not returned a checkout link yet.'
  }
  return 'I have the checkout details I need. Keep replying here if anything is missing or needs to change.'
}

export function merchantHandoffReason(session: ActiveCheckoutSession): string {
  const merchantDisplay = merchantDisplayOrigin(session.merchantOrigin)
  const reasons = new Set(session.profile.ineligibilityReasons ?? [])
  if (
    reasons.has('AUTHORIZATION_REQUIRED') ||
    reasons.has('AUTHENTICATION_DISABLED') ||
    reasons.has('TIER_NOT_GRANTED') ||
    reasons.has('MISSING_SCOPES')
  ) {
    return 'Direct completion is not authorized for this checkout, so Meant selected the merchant handoff.'
  }
  if (reasons.has('ROLLOUT_DISABLED')) {
    return 'Checkout completion inside Meant is not enabled for this merchant yet.'
  }
  if (session.profile.nextAction === 'HANDOFF' && checkoutReadyForPayment(session)) {
    return 'Everything is prepared — only the secure payment step remains with the merchant.'
  }
  if (checkoutHasExtensionInteraction(session.profile)) {
    return `${merchantDisplay} requires additional interaction in its checkout before the order can be completed.`
  }
  return `Continue in ${merchantDisplay}'s checkout to finish the order.`
}
