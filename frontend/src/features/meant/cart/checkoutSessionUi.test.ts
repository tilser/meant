import { describe, expect, test } from 'bun:test'

import type { CheckoutProfile } from '../../../lib/apiClient'
import type { ActiveCheckoutSession } from './checkoutTypes'
import {
  checkoutAssistantPrompt,
  checkoutNeedsAddress,
  checkoutNeedsHandoff,
  merchantHandoffReason,
} from './checkoutSessionUi'

function session(profile: Partial<CheckoutProfile>): ActiveCheckoutSession {
  return {
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
      nativeCheckoutEnabled: true,
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
      nativeCheckoutEnabled: true,
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
})
