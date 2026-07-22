import { describe, expect, test } from 'bun:test'

import type { CheckoutProfile } from '../../../lib/apiClient'
import {
  savedCheckoutAddressLines,
  savedCheckoutAssistantContext,
  savedCheckoutDetails,
  savedCheckoutRecipient,
  type SavedCheckoutDetailsProfile,
} from './savedCheckoutDetails'

const details: SavedCheckoutDetailsProfile = {
  buyer: {
    email: 'ada@example.com',
    firstName: ' Ada ',
    lastName: ' Lovelace ',
    phoneNumber: '+44 20 7946 0958',
  },
  shippingAddress: {
    streetAddress: ' 12 St James Square ',
    extendedAddress: ' Flat 3 ',
    addressLocality: ' London ',
    addressRegion: null,
    postalCode: ' SW1Y 4LB ',
    addressCountry: ' gb ',
  },
  updatedAt: '2026-07-22T10:00:00Z',
}

describe('saved checkout details presentation', () => {
  test('extracts the optional checkout-owned saved details contract', () => {
    const profile = { savedCheckoutDetails: details } as CheckoutProfile

    expect(savedCheckoutDetails(profile)).toBe(details)
    expect(savedCheckoutDetails({} as CheckoutProfile)).toBeNull()
  })

  test('formats a compact recipient and postal summary without empty optional fields', () => {
    expect(savedCheckoutRecipient(details)).toBe('Ada Lovelace')
    expect(savedCheckoutAddressLines(details)).toEqual([
      '12 St James Square',
      'Flat 3',
      'London SW1Y 4LB',
      'GB',
    ])
  })

  test('routes the typed saved payload through the guarded checkout handler context', () => {
    expect(savedCheckoutAssistantContext(details)).toEqual({
      savedCheckoutDetails: {
        buyer: details.buyer,
        shippingAddress: details.shippingAddress,
      },
    })
  })
})
