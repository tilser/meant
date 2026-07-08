import type {
  CheckoutAssistantMessage,
  CheckoutAssistantResult,
  CheckoutBuyerInput,
  CheckoutCompletionProfile,
  CheckoutProfile,
  CheckoutShippingAddressInput,
} from '../../../lib/apiClient'
import type { CartItem } from '../types'

export interface ActiveCheckoutSession {
  cartId: string
  threadId?: string | null
  merchant: string
  source: 'cart' | 'chat'
  items: readonly CartItem[]
  saved: number
  savedNote: string
  profile: CheckoutProfile
  completion: CheckoutCompletionProfile | null
}

export interface CompleteCheckoutInput {
  handler: string
  token: string
}

export interface UpdateCheckoutAddressInput {
  buyer: CheckoutBuyerInput
  shippingAddress: CheckoutShippingAddressInput
}

export interface CheckoutAssistantContext {
  merchantDeliveryHint?: string | null
}

export type CheckoutAssistantHandler = (
  message: string,
  history: readonly CheckoutAssistantMessage[],
  context?: CheckoutAssistantContext,
) => Promise<CheckoutAssistantResult | null>
