import type { CheckoutProfile } from '../../../lib/apiClient'
import type { CheckoutAssistantContext } from './checkoutTypes'

export type SavedCheckoutDetailsProfile = NonNullable<CheckoutProfile['savedCheckoutDetails']>

export function savedCheckoutDetails(profile: CheckoutProfile): SavedCheckoutDetailsProfile | null {
  return profile.savedCheckoutDetails ?? null
}

export function savedCheckoutAssistantContext(
  details: SavedCheckoutDetailsProfile,
): CheckoutAssistantContext {
  return {
    savedCheckoutDetails: {
      buyer: details.buyer,
      shippingAddress: details.shippingAddress,
    },
  }
}

export function savedCheckoutRecipient(details: SavedCheckoutDetailsProfile): string {
  return [details.buyer.firstName, details.buyer.lastName]
    .map((part) => part.trim())
    .filter(Boolean)
    .join(' ')
}

export function savedCheckoutAddressLines(details: SavedCheckoutDetailsProfile): string[] {
  const address = details.shippingAddress
  const locality = [address.addressLocality.trim(), address.addressRegion?.trim()]
    .filter(Boolean)
    .join(', ')
  const localityAndPostalCode = [locality, address.postalCode.trim()].filter(Boolean).join(' ')
  return [
    address.streetAddress.trim(),
    address.extendedAddress?.trim(),
    localityAndPostalCode,
    address.addressCountry.trim().toUpperCase(),
  ].filter((line): line is string => Boolean(line))
}
