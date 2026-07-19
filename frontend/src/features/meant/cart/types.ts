import type { CartDeliveryGroup, CartDeliveryOption, CartItem } from '../types'

export type AppliedCartCodeType = 'DISCOUNT' | 'GIFT_CARD'

export interface AppliedCartCode {
  type: AppliedCartCodeType
  code: string | null
  displayCode: string | null
  label: string | null
  applicable: boolean | null
  amount: number | null
  currency: string | null
}

export interface MerchantCartSnapshot {
  merchantKey: string
  merchant: string
  cartId: string | null
  remoteCartId: string | null
  checkoutUrl: string | null
  continueUrl: string | null
  subtotalAmount: number | null
  totalAmount: number | null
  currency: string | null
  appliedCodes: AppliedCartCode[]
}

/** A complete server-owned merchant partition replacing any older local cart for that scope. */
export interface MerchantCartStateReplacement {
  merchantKey: string
  merchantId: string | null
  merchantDomain: string | null
  provider: string | null
  merchantIntegrationId: string | null
  externalMerchantId: string | null
  routingScopeKey: string | null
  snapshot: MerchantCartSnapshot
  lines: readonly CartItem[]
}

export interface ApplyCartCodeInput {
  merchantKey: string
  merchant: string
  cartId: string
  code: string
  type: AppliedCartCodeType
}

export interface RemoveCartCodeInput {
  merchantKey: string
  merchant: string
  cartId: string
  code: AppliedCartCode
}

export interface DeliveryAddressDraft {
  countryCode: string
  city: string
  postalCode: string
  provinceCode: string
}

export interface DeliveryAddressPayload extends DeliveryAddressDraft {
  cartId: string
  merchantKey: string
  merchant: string
}

export interface DeliveryOptionPayload {
  cartId: string
  merchantKey: string
  merchant: string
  group: CartDeliveryGroup
  option: CartDeliveryOption
}
