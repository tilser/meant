export type CorePreferenceId = string
export type PreferenceId = string

export type ProductId = string

export type PortType = 'usb-c' | 'usb-a' | 'hdmi'

export type View =
  | 'discover'
  | 'saved'
  | 'compare'
  | 'inventory'
  | 'preferences'
  | 'cart'
  | 'orders'
  | 'account'

export type Theme = 'light' | 'dark'
export type AuthMode = 'signin' | 'signup' | 'reset'
export type OrderStatus = 'Delivered' | 'In transit' | 'Processing'
export type DiscountType = 'percent' | 'fixed' | 'shipping'
export type ClothingFit = 'none' | 'men' | 'women' | 'other'
export type ProductAudience = 'men' | 'women' | 'other' | 'unisex'
export type InventoryRelationship = 'NONE' | 'DUPLICATE' | 'COMPLEMENT' | 'RESTOCK'

export interface Preference {
  id: PreferenceId
  label: string
  desc: string
  category?: string
  polarity?: string
  displayOrder?: number
}

export interface Profile {
  name: string
  summary: string
}

export interface Offer {
  merchant: string
  price: number
  delivery: string
  merchantId?: string | null
  merchantDomain?: string | null
  productVariantId?: string | null
  variantTitle?: string | null
  available?: boolean | null
}

export interface ReviewSummary {
  score: number
  count: number
  insight: string
}

export interface Product {
  id: ProductId
  productHash?: string | null
  name: string
  brand: string
  category: string
  tone: string
  imageUrl?: string | null
  productUrl?: string | null
  remote?: boolean
  match: number
  priceFrom: number
  merchants: number
  satisfies: readonly PreferenceId[]
  misses: readonly PreferenceId[]
  note: string
  pros: readonly string[]
  cons: readonly string[]
  review: ReviewSummary
  offers: readonly Offer[]
  needs?: PortType
  provides?: readonly PortType[]
  audiences?: readonly ProductAudience[]
  inventoryRelationship?: InventoryRelationship
  inventoryItemId?: string | null
  inventoryItemName?: string | null
}

export interface Reply {
  text: string
  ids: readonly ProductId[]
}

export interface DiscountCode {
  code: string
  label: string
  type: DiscountType
  value: number
  min?: number
}

export interface LocationOption {
  country: string
  code: string
  cities: readonly string[]
}

export interface UserLocation {
  country: string
  code: string
  city: string
}

export interface MerchantCoverage {
  ships: 'global' | readonly string[]
  cities?: readonly string[]
}

export interface CartItem {
  id: ProductId
  merchant: string
  qty: number
  merchantId?: string | null
  merchantDomain?: string | null
  productVariantId?: string | null
  variantTitle?: string | null
  cartId?: string | null
  remoteCartId?: string | null
  checkoutUrl?: string | null
  cartLineId?: string | null
  remoteCartLineId?: string | null
  cartTotalAmount?: string | null
  cartSubtotalAmount?: string | null
  cartCurrency?: string | null
  deliveryGroups?: readonly CartDeliveryGroup[]
  syncing?: boolean
  syncError?: string | null
}

export interface CartLine extends CartItem {
  product: Product
  price: number
  delivery: string
}

export interface CartDeliveryMoney {
  amount?: string | null
  currency?: string | null
}

export interface CartDeliveryOption {
  handle?: string | null
  title?: string | null
  description?: string | null
  code?: string | null
  cost?: CartDeliveryMoney | null
  deliveryMethodType?: string | null
  deliveryEstimate?: string | null
  estimatedDeliveryTime?: string | null
  estimatedDeliveryAt?: string | null
  selected?: boolean | null
}

export interface CartDeliveryGroup {
  id?: string | null
  handle?: string | null
  deliveryOptions?: readonly CartDeliveryOption[]
  selectedDeliveryOption?: CartDeliveryOption | null
}

export interface Order {
  id: string
  date: string
  status: OrderStatus
  statusNote: string
  items: readonly CartItem[]
  saved: number
  savedNote: string
}

export interface UserAccount {
  name: string
  email: string
  avatar: string | null
  avatarPath: string | null
}

export interface CheckoutPayload {
  items: readonly CartItem[]
  saved: number
  savedNote: string
  merchant?: string
  checkoutUrl?: string
}

export interface SmartAlertFix {
  label: string
  sub: string
  id: ProductId
  merchant: string
}

export interface SmartAlert {
  id: string
  kind: 'warn' | 'good'
  title: string
  body: string
  fix?: SmartAlertFix
}
