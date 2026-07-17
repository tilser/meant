import { describe, expect, test } from 'bun:test'

import type { CheckoutProfile } from '../../../lib/apiClient'
import type { ActiveCheckoutSession } from './checkoutTypes'
import {
  checkoutAssistantPrompt,
  checkoutNeedsAddress,
  checkoutNeedsHandoff,
  checkoutReadyForPayment,
  merchantHandoffReason,
} from './checkoutSessionUi'

function session(profile: Partial<CheckoutProfile>): ActiveCheckoutSession {
  return {
    ownerId: 'user-a',
    cartId: 'cart-1',
    merchant: 'MyGiftStop',
    source: 'cart',
    items: [],
    saved: 0,
    savedNote: '',
    profile: profile as CheckoutProfile,
    completion: null,
  }
}

describe('checkout session UCP actions', () => {
  test('resolves recoverable buyer details before handing off', () => {
    const checkout = session({
      status: 'requires_escalation',
      requiresEscalation: true,
      nextAction: 'UPDATE_CHECKOUT',
      messages: [
        {
          type: 'error',
          code: 'extension_interaction_required',
          severity: 'requires_buyer_input',
          content: 'An extension interaction is required to complete the checkout.',
        },
        {
          type: 'error',
          code: 'delivery_address_required',
          severity: 'recoverable',
          content: 'A destination address is required in order to continue.',
        },
      ],
      selectedRail: 'PROVIDER_CHECKOUT_SESSION',
      ineligibilityReasons: [],
    })

    expect(checkoutNeedsAddress(checkout)).toBe(true)
    expect(checkoutNeedsHandoff(checkout)).toBe(false)
  })

  test('hands off after only the buyer interaction requirement remains', () => {
    const checkout = session({
      status: 'requires_escalation',
      requiresEscalation: true,
      nextAction: 'HANDOFF',
      continueUrl: 'https://merchant.example/continue',
      messages: [
        {
          type: 'error',
          code: 'extension_interaction_required',
          severity: 'requires_buyer_input',
          content: 'An extension interaction is required to complete the checkout.',
        },
      ],
      selectedRail: 'MERCHANT_HANDOFF',
      ineligibilityReasons: ['FALLBACK_SELECTED'],
    })

    expect(checkoutNeedsAddress(checkout)).toBe(false)
    expect(checkoutNeedsHandoff(checkout)).toBe(true)
    expect(checkoutAssistantPrompt(checkout)).toContain(
      'requires additional interaction in its checkout',
    )
    expect(checkoutAssistantPrompt(checkout)).not.toContain('final secure step')
    expect(merchantHandoffReason(checkout)).toContain(
      'requires additional interaction in its checkout',
    )
  })

  test('uses the explicit direct-completion action without inferring a handoff from status', () => {
    const checkout = session({
      status: 'ready_for_complete',
      nextAction: 'COMPLETE_CHECKOUT',
      selectedRail: 'DIRECT_CHECKOUT_COMPLETION',
      ineligibilityReasons: [],
      messages: [],
    })

    expect(checkoutNeedsHandoff(checkout)).toBe(false)
    expect(checkoutAssistantPrompt(checkout)).toContain('ready for direct completion')
  })

  test('keeps an explicit unknown action authoritative instead of inferring from status', () => {
    const checkout = session({
      status: 'ready_for_complete',
      nextAction: 'UNKNOWN',
      selectedRail: 'NONE',
      ineligibilityReasons: [],
      messages: [],
    })

    expect(checkoutReadyForPayment(checkout)).toBe(false)
    expect(checkoutNeedsHandoff(checkout)).toBe(false)
  })

  test('represents embedded checkout independently from disabled direct completion', () => {
    const checkout = session({
      status: 'ready_for_complete',
      nextAction: 'OPEN_EMBEDDED_CHECKOUT',
      selectedRail: 'EMBEDDED_CHECKOUT',
      ineligibilityReasons: [],
      messages: [
        {
          type: 'error',
          code: 'extension_interaction_required',
          severity: 'requires_buyer_input',
          content: 'Cross-border checkout requires merchant interaction.',
        },
      ],
    })

    expect(checkoutNeedsHandoff(checkout)).toBe(false)
    expect(checkoutAssistantPrompt(checkout)).toContain('embedded checkout')
  })

  test('explains a scope-driven merchant fallback from typed policy reasons', () => {
    const checkout = session({
      status: 'ready_for_complete',
      nextAction: 'HANDOFF',
      selectedRail: 'MERCHANT_HANDOFF',
      ineligibilityReasons: ['MISSING_SCOPES', 'FALLBACK_SELECTED'],
      continueUrl: 'https://merchant.example/continue',
      messages: [],
    })

    expect(checkoutNeedsHandoff(checkout)).toBe(true)
    expect(merchantHandoffReason(checkout)).toContain('not authorized')
  })
})
