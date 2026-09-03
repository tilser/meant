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
      ? `Your match is ready. ${merchantDisplay} handles the final secure payment step, so continue below when you’re ready.`
      : `Your match is ready, but ${merchantDisplay} has not returned its secure checkout link yet.`
  }
  if (checkoutNeedsAddress(session)) {
    if (savedCheckoutDetails(session.profile)) {
      return `We knew you and this find were Meant together. Before we continue with ${merchantDisplay}, should we send it to your saved address below?`
    }
    return [
      `We knew you two were Meant together. Now tell me where to send what’s Meant for you, along with your name, email and phone.`,
      coverage,
      'You can send everything in one message, for example: “1531 Hyde St, San Francisco, CA 94109, US — John Novak, john.novak@example.com, +1 415 555 0137”.',
    ].join(' ')
  }
  if (session.profile.nextAction === 'OPEN_EMBEDDED_CHECKOUT') {
    return 'Your details are set. What’s Meant for you is close — open the secure Merchant checkout below to complete payment.'
  }
  if (session.profile.nextAction === 'COMPLETE_CHECKOUT') {
    return 'Everything is ready. What’s Meant for you is one secure step away inside Meant.'
  }
  if (checkoutNeedsHandoff(session)) {
    return merchantUrl
      ? checkoutHasExtensionInteraction(session.profile)
        ? `Everything we can prepare is ready. ${merchantDisplay} needs one more secure interaction in its checkout; continue below.`
        : `Everything we can prepare is ready. Continue securely with ${merchantDisplay} to make this match yours.`
      : `${merchantDisplay} needs one more checkout interaction, but its secure link is not ready yet.`
  }
  return 'I have everything this match needs. Tell Meant here if you would like to change any detail.'
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
    return 'Everything is prepared. Meant will take you to the Merchant for the final secure payment step.'
  }
  if (reasons.has('ROLLOUT_DISABLED')) {
    return 'This match finishes securely with the Merchant because checkout inside Meant is not enabled here yet.'
  }
  if (session.profile.nextAction === 'HANDOFF' && checkoutReadyForPayment(session)) {
    return 'Everything is prepared — only the secure payment step remains with the Merchant.'
  }
  if (checkoutHasExtensionInteraction(session.profile)) {
    return `${merchantDisplay} needs one more secure interaction before this match can be completed.`
  }
  return `Continue securely in ${merchantDisplay}’s checkout to make this match yours.`
}
