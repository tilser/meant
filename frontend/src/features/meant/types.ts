import type { CanonicalProductProfile, MerchantProductDetailsProfile } from '../../lib/apiClient'

export type CorePreferenceId = string
export type PreferenceId = string

export type ProductId = string

export type PortType = 'usb-c' | 'usb-a' | 'hdmi'

export type View =
  'discover' | 'saved' | 'compare' | 'inventory' | 'preferences' | 'cart' | 'orders' | 'account'

export type Theme = 'light' | 'dark'
export type AuthMode = 'signin' | 'signup' | 'reset'
export type OrderStatus = 'Delivered' | 'In transit' | 'Processing' | 'Canceled' | 'Refunded'
export type ClothingFit = 'none' | 'men' | 'women' | 'other'
export type ProductAudience = 'men' | 'women' | 'other' | 'unisex'
export type InventoryRelationship = 'NONE' | 'DUPLICATE' | 'COMPLEMENT' | 'RESTOCK'
export type ProductAgentStage = 'candidate' | 'curating' | 'curated' | 'enriched' | 'ranked'

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
  offerKey?: string | null
  merchant: string
  price: number
  priceMinorUnits?: number | null
  priceCurrency?: string | null
  delivery: string
  merchantId?: string | null
  merchantDomain?: string | null
  provider?: string | null
  merchantIntegrationId?: string | null
  externalMerchantId?: string | null
  merchantScopeKey?: string | null
  productVariantId?: string | null
  variantTitle?: string | null
  available?: boolean | null
}

export interface ReviewSummary {
  score: number | null
  count: number
  insight: string
}

export interface ProductMedia {
  type: string
  url: string
  altText?: string | null
}

export interface ProductCatalogCategory {
  value: string
  taxonomy?: string | null
}

export interface ProductCatalogAttribute {
  name: string
  value: string
}

export interface ProductOption {
  name: string
  values: readonly string[]
}

export interface ProductSelectedOption {
  name: string
  value: string
}

export interface Product {
  id: ProductId
  productHash?: string | null
  merchantId?: string | null
  merchantDomain?: string | null
  merchantProductId?: string | null
  name: string
  brand: string
  category: string
  tone: string
  imageUrl?: string | null
  productUrl?: string | null
  remote?: boolean
  match: number
  /** The current product was rehydrated without the original search-ranking explanation. */
  rankingUnavailable?: boolean
  priceFrom: number | null
  priceFromMinorUnits?: number | null
  priceCurrency?: string | null
  listPrice?: number | null
  merchants: number
  satisfies: readonly PreferenceId[]
  misses: readonly PreferenceId[]
  note: string
  pros: readonly string[]
  cons: readonly string[]
  review: ReviewSummary
  media?: readonly ProductMedia[]
  catalogCategories?: readonly ProductCatalogCategory[]
  certifications?: readonly string[]
  materials?: readonly string[]
  skus?: readonly string[]
  collections?: readonly string[]
  catalogAttributes?: readonly ProductCatalogAttribute[]
  detailError?: string | null
  detailDescription?: string | null
  detailOptions?: readonly ProductOption[]
  selectedOptions?: readonly ProductSelectedOption[]
  totalVariants?: number | null
  selectedVariantAvailable?: boolean | null
  offers: readonly Offer[]
  needs?: PortType
  provides?: readonly PortType[]
  audiences?: readonly ProductAudience[]
  inventoryRelationship?: InventoryRelationship
  inventoryItemId?: string | null
  inventoryItemName?: string | null
  agentStage?: ProductAgentStage
  agentUpdatedAt?: number
  commercialFactsAuthoritative?: boolean
  /** Current provider detail loaded through the durable saved-product reference. */
  rehydratedDetails?: MerchantProductDetailsProfile | null
  /** Generated grouped API payload retained as the single source of truth for exact offers. */
  canonicalProduct?: CanonicalProductProfile
}

export interface Reply {
  text: string
  ids: readonly ProductId[]
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
  merchantOrigin?: string | null
  merchantId?: string | null
  merchantDomain?: string | null
  provider?: string | null
  merchantIntegrationId?: string | null
  externalMerchantId?: string | null
  routingScopeKey?: string | null
  merchantScopeKey?: string | null
  productVariantId?: string | null
  offerKey?: string | null
  variantTitle?: string | null
  cartId?: string | null
  remoteCartId?: string | null
  checkoutUrl?: string | null
  continueUrl?: string | null
  cartLineId?: string | null
  remoteCartLineId?: string | null
  cartTotalAmount?: string | null
  cartSubtotalAmount?: string | null
  cartCurrency?: string | null
  deliveryGroups?: readonly CartDeliveryGroup[]
  productTitle?: string | null
  imageUrl?: string | null
  productUrl?: string | null
  unitPriceAmount?: string | null
  lineTotalAmount?: string | null
  orderCurrency?: string | null
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
  newsletter: boolean
}

export interface CheckoutPayload {
  items: readonly CartItem[]
  saved: number
  savedNote: string
  merchant?: string
  merchantKey?: string
  chatThreadId?: string | null
  checkoutUrl?: string | null
  continueUrl?: string | null
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
