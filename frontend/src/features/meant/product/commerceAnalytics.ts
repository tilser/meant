export type CommerceEventName =
  'product_view' | 'offer_view' | 'offer_selection' | 'default_offer_override' | 'add_to_cart'

export interface CommerceEventFields {
  canonicalProductKey: string
  offerKey?: string
  offerRank?: number
  offerCount?: number
  checkoutExperience?: 'MEANT_MANAGED' | 'PROVIDER_HANDOFF' | 'UNKNOWN'
  availability?: string
  result?: 'attempted' | 'succeeded' | 'failed'
}

const MAX_KEY_LENGTH = 160

function boundedKey(value: string): string {
  return value.slice(0, MAX_KEY_LENGTH)
}

export function commerceEventDetail(
  name: CommerceEventName,
  fields: CommerceEventFields,
): Readonly<{ name: CommerceEventName; fields: CommerceEventFields }> {
  return {
    name,
    fields: {
      ...fields,
      canonicalProductKey: boundedKey(fields.canonicalProductKey),
      offerKey: fields.offerKey ? boundedKey(fields.offerKey) : undefined,
      offerRank:
        fields.offerRank == null ? undefined : Math.max(1, Math.min(100, fields.offerRank)),
      offerCount:
        fields.offerCount == null ? undefined : Math.max(0, Math.min(100, fields.offerCount)),
    },
  }
}

export function trackCommerceEvent(name: CommerceEventName, fields: CommerceEventFields): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(
    new CustomEvent('meant:commerce-analytics', { detail: commerceEventDetail(name, fields) }),
  )
}
