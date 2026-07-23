import { describe, expect, test } from 'bun:test'

import type { CheckoutProfile } from '../../../lib/apiClient'
import type { ActiveCheckoutSession } from './checkoutTypes'
import {
  checkoutAssistantPrompt,
  checkoutNeedsAddress,
  checkoutNeedsHandoff,
  checkoutRequiresMerchantRedirect,
  checkoutReadyForPayment,
  checkoutShouldOfferSavedDetails,
  checkoutUsesEmbeddedCheckout,
  merchantCheckoutUrl,
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

  test('offers saved details without asking the buyer to type them again', () => {
    const checkout = session({
      status: 'requires_escalation',
      requiresEscalation: true,
      nextAction: 'UPDATE_CHECKOUT',
      messages: [
        {
          type: 'error',
          code: 'delivery_address_required',
          severity: 'recoverable',
          content: 'A destination address is required in order to continue.',
        },
      ],
      selectedRail: 'PROVIDER_CHECKOUT_SESSION',
      ineligibilityReasons: [],
      savedCheckoutDetails: {
        buyer: {
          email: 'ada@example.com',
          firstName: 'Ada',
          lastName: 'Lovelace',
        },
        shippingAddress: {
          streetAddress: '1 Market St',
          addressLocality: 'San Francisco',
          addressRegion: 'CA',
          postalCode: '94105',
          addressCountry: 'US',
        },
        updatedAt: '2026-07-22T10:00:00Z',
      },
    })

    expect(checkoutAssistantPrompt(checkout)).toContain('reuse your saved details')
    expect(checkoutAssistantPrompt(checkout)).not.toContain('Send them here in one message')
    expect(checkoutShouldOfferSavedDetails(checkout, false)).toBe(true)
    expect(checkoutShouldOfferSavedDetails(checkout, true)).toBe(false)
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
      ineligibilityReasons: ['MERCHANT_REDIRECT_REQUIRED'],
      continueUrl: 'https://merchant.example/continue',
      messages: [
        {
          type: 'error',
          code: 'redirect_to_checkout_required',
          severity: 'requires_buyer_input',
          content: 'Continue in the merchant checkout.',
        },
      ],
    })

    expect(checkoutRequiresMerchantRedirect(checkout.profile)).toBe(false)
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
    expect(checkoutUsesEmbeddedCheckout(checkout)).toBe(true)
    expect(checkoutAssistantPrompt(checkout)).toContain('embedded checkout')
  })

  test('uses the merchant continue URL when the provider explicitly requires redirect checkout', () => {
    const checkout = session({
      status: 'requires_escalation',
      requiresEscalation: true,
      nextAction: 'HANDOFF',
      selectedRail: 'MERCHANT_HANDOFF',
      ineligibilityReasons: ['MERCHANT_REDIRECT_REQUIRED', 'FALLBACK_SELECTED'],
      checkoutUrl: 'https://merchant.example/generic-checkout',
      continueUrl: 'https://merchant.example/continue',
      messages: [
        {
          type: 'error',
          code: 'item_unavailable',
          severity: 'recoverable',
          content: 'Item cannot be purchased',
        },
        {
          type: 'error',
          code: 'redirect_to_checkout_required',
          severity: 'requires_buyer_input',
          content: 'Cross-border checkout is not supported for this channel.',
        },
      ],
    })

    expect(checkoutNeedsAddress(checkout)).toBe(false)
    expect(checkoutNeedsHandoff(checkout)).toBe(true)
    expect(checkoutUsesEmbeddedCheckout(checkout)).toBe(false)
    expect(merchantCheckoutUrl(checkout)).toBe('https://merchant.example/continue')
    expect(checkoutAssistantPrompt(checkout)).toContain(
      'Checkout inside Meant is not available for this Merchant',
    )
    expect(checkoutAssistantPrompt(checkout)).not.toContain('I have the checkout details I need')
  })

  test('embedded checkout remains authoritative over stale provider redirect metadata', () => {
    const checkout = session({
      status: 'requires_escalation',
      nextAction: 'OPEN_EMBEDDED_CHECKOUT',
      selectedRail: 'EMBEDDED_CHECKOUT',
      ineligibilityReasons: ['MERCHANT_REDIRECT_REQUIRED'],
      continueUrl: 'https://merchant.example/continue',
      messages: [
        {
          type: 'error',
          code: 'redirect_to_checkout_required',
          severity: 'requires_buyer_input',
          content: 'Continue in the merchant checkout.',
        },
      ],
    })

    expect(checkoutRequiresMerchantRedirect(checkout.profile)).toBe(false)
    expect(checkoutNeedsHandoff(checkout)).toBe(false)
    expect(checkoutUsesEmbeddedCheckout(checkout)).toBe(true)
    expect(checkoutAssistantPrompt(checkout)).toContain('supports embedded checkout')
    expect(checkoutAssistantPrompt(checkout)).not.toContain('not available for this Merchant')
  })

  test('keeps a terminal action authoritative over redirect fallback inference', () => {
    const checkout = session({
      status: 'requires_escalation',
      nextAction: 'RESTART',
      selectedRail: 'NONE',
      ineligibilityReasons: ['MERCHANT_REDIRECT_REQUIRED'],
      continueUrl: 'https://merchant.example/continue',
      messages: [
        {
          type: 'error',
          code: 'checkout_expired',
          severity: 'unrecoverable',
          content: 'Checkout expired.',
        },
        {
          type: 'error',
          code: 'redirect_to_checkout_required',
          severity: 'requires_buyer_input',
          content: 'Continue in the merchant checkout.',
        },
      ],
    })

    expect(checkoutRequiresMerchantRedirect(checkout.profile)).toBe(false)
    expect(checkoutNeedsHandoff(checkout)).toBe(false)
    expect(checkoutAssistantPrompt(checkout)).not.toContain('not available for this Merchant')
  })

  test('does not infer merchant redirect without an explicit backend handoff decision', () => {
    const incomplete = session({
      status: 'incomplete',
      nextAction: 'OPEN_EMBEDDED_CHECKOUT',
      selectedRail: 'EMBEDDED_CHECKOUT',
      ineligibilityReasons: [],
      continueUrl: 'https://merchant.example/continue',
      messages: [
        {
          code: 'redirect_to_checkout_required',
          severity: 'requires_buyer_input',
          content: 'Continue in the merchant checkout.',
        },
      ],
    })
    const missingUrl = session({
      status: 'requires_escalation',
      nextAction: 'OPEN_EMBEDDED_CHECKOUT',
      selectedRail: 'EMBEDDED_CHECKOUT',
      ineligibilityReasons: [],
      messages: incomplete.profile.messages,
    })

    for (const checkout of [incomplete, missingUrl]) {
      expect(checkoutNeedsHandoff(checkout)).toBe(false)
      expect(checkoutUsesEmbeddedCheckout(checkout)).toBe(true)
      expect(checkoutAssistantPrompt(checkout)).toContain('supports embedded checkout')
    }
  })

  test('rejects insecure or credential-bearing merchant handoff URLs', () => {
    expect(
      merchantCheckoutUrl(
        session({
          continueUrl: 'http://merchant.example/continue',
        }),
      ),
    ).toBeNull()
    expect(
      merchantCheckoutUrl(
        session({
          continueUrl: 'https://buyer:secret@merchant.example/continue',
        }),
      ),
    ).toBeNull()
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
