import type { CheckoutCompletionProfile, CheckoutProfile } from '../../../lib/apiClient'
import type { ActiveCheckoutSession } from './checkoutTypes'
import { merchantDeliveryCoverageSummary } from '../utils'

function normalizedSeverity(severity: string | null | undefined): string {
  return severity?.trim().toLowerCase() ?? ''
}

function checkoutHasRecoverableMessages(profile: CheckoutProfile | null | undefined): boolean {
  return (
    profile?.messages?.some((message) => normalizedSeverity(message.severity) === 'recoverable') ??
    false
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
    checkoutHasRecoverableMessages(session.profile) ||
    checkoutNeedsMerchantInput(session.profile, session.completion)
  )
}

export function checkoutNeedsHandoff(session: ActiveCheckoutSession): boolean {
  const normalizedStatus = session.profile.status?.trim().toLowerCase() ?? ''
  return (
    session.completion?.status === 'SCA_REQUIRED' ||
    (!checkoutNeedsAddress(session) &&
      (Boolean(session.profile.requiresEscalation) || normalizedStatus === 'requires_escalation')) ||
    (!session.profile.nativeCheckoutEnabled && !checkoutNeedsAddress(session))
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
      ? 'This merchant does not support native checkout inside Meant yet. I prepared the merchant checkout with the cart details we have; use the button below to finish on the merchant site.'
      : 'This merchant does not support native checkout inside Meant yet, and it has not returned a checkout link I can open.'
  }
  if (checkoutNeedsHandoff(session)) {
    return merchantUrl
      ? 'The merchant requires the final secure step on its checkout page. I prepared what I can here; use the button below to continue.'
      : 'The merchant requires an external checkout step, but it has not returned a checkout link yet.'
  }
  return 'I have the checkout details I need. Keep replying here if anything is missing or needs to change.'
}

export function merchantHandoffReason(session: ActiveCheckoutSession): string {
  if (!session.profile.nativeCheckoutEnabled) {
    return 'Native checkout is not available for this merchant yet.'
  }
  if (session.profile.embeddableCheckout === false) {
    return 'The merchant blocks embedded checkout, so I cannot show that page safely inside Meant.'
  }
  return 'The merchant requires this final step on its own checkout page.'
}
