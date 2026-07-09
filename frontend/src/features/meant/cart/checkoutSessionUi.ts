import type { CheckoutCompletionProfile, CheckoutProfile } from '../../../lib/apiClient'
import type { ActiveCheckoutSession } from './checkoutTypes'
import { merchantDeliveryCoverageSummary } from '../utils'

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

export function merchantCheckoutUrl(session: ActiveCheckoutSession): string | null {
  const candidate =
    session.completion?.continueUrl ?? session.profile.continueUrl ?? session.profile.checkoutUrl
  if (!candidate?.trim()) {
    return null
  }
  try {
    const url = new URL(candidate.trim())
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

export function checkoutNeedsAddress(session: ActiveCheckoutSession): boolean {
  return (
    checkoutHasBuyerDetailMessages(session.profile) ||
    checkoutNeedsMerchantInput(session.profile, session.completion)
  )
}

export function checkoutReadyForPayment(session: ActiveCheckoutSession): boolean {
  const normalizedStatus = session.profile.status?.trim().toLowerCase() ?? ''
  return (
    session.profile.nextAction === 'COMPLETE_CHECKOUT' ||
    normalizedStatus === 'ready_for_complete' ||
    normalizedStatus === 'ready_for_payment'
  )
}

export function checkoutNeedsHandoff(session: ActiveCheckoutSession): boolean {
  const normalizedStatus = session.profile.status?.trim().toLowerCase() ?? ''
  const nextAction = session.profile.nextAction
  if (session.completion?.status === 'SCA_REQUIRED') {
    return true
  }
  if (nextAction === 'UPDATE_CHECKOUT' && session.profile.nativeCheckoutEnabled) {
    return false
  }
  return (
    nextAction === 'HANDOFF' ||
    (!nextAction &&
      !checkoutNeedsAddress(session) &&
      (Boolean(session.profile.requiresEscalation) ||
        normalizedStatus === 'requires_escalation')) ||
    (!session.profile.nativeCheckoutEnabled && !checkoutNeedsAddress(session)) ||
    // Payment instruments are not collected inside Meant yet, so the final
    // payment step continues on the merchant's secure checkout.
    (checkoutReadyForPayment(session) && !checkoutNeedsAddress(session))
  )
}

export function checkoutPhase(session: ActiveCheckoutSession): string {
  if (checkoutNeedsAddress(session)) {
    return 'address'
  }
  if (checkoutNeedsHandoff(session)) {
    return session.profile.nativeCheckoutEnabled ? 'handoff' : 'merchant-native-missing'
  }
  return 'native'
}

export function checkoutAssistantPrompt(session: ActiveCheckoutSession): string {
  const merchantUrl = merchantCheckoutUrl(session)
  const coverage = merchantDeliveryCoverageSummary(session.merchant)
  if (checkoutNeedsAddress(session)) {
    return [
      `I need shipping and contact details before I can continue with ${session.merchant}.`,
      coverage,
      'Send them here in one message, for example: "Ship to 1531 Hyde St, San Francisco, CA 94109, US, David Test, david@test.cz, +420 731 958 653".',
    ].join(' ')
  }
  if (!session.profile.nativeCheckoutEnabled) {
    return merchantUrl
      ? 'This merchant does not support native checkout inside Meant yet. I prepared the merchant checkout with the cart details we have; finish the order below.'
      : 'This merchant does not support native checkout inside Meant yet, and it has not returned a checkout link I can open.'
  }
  if (checkoutReadyForPayment(session)) {
    return merchantUrl
      ? 'All checkout details are confirmed — items, address, and delivery. Finish the secure payment below.'
      : 'All checkout details are confirmed, but the merchant has not returned a payment link yet.'
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
  if (!session.profile.nativeCheckoutEnabled) {
    return 'Native checkout is not available for this merchant yet.'
  }
  if (checkoutReadyForPayment(session)) {
    return 'Everything is prepared — only the secure payment step remains with the merchant.'
  }
  if (checkoutHasExtensionInteraction(session.profile)) {
    return `${session.merchant} requires additional interaction in its checkout before the order can be completed.`
  }
  return `Continue in ${session.merchant}'s checkout to finish the order.`
}
