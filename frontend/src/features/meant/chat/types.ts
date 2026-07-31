import type { CartItem, Order, Preference, Product, ProductId } from '../types'
import type { ProductReviewsProfile } from '../../../lib/apiClient'

export interface MiniCompareRow {
  label: string
  values: readonly string[]
  winnerIndex: number
}

export interface FoundDiscountCode {
  code: string
  title?: string | null
  description?: string | null
  sourceUrl?: string | null
  confidence?: number | null
  restrictions?: string | null
  validUntil?: string | null
  expiresAt?: string | null
  validationMessage?: string | null
}

export interface ShoppingMissionRequirement {
  id: string
  label: string
  state: 'MISSING' | 'PARTIAL' | 'COVERED' | 'OPTIONAL'
  requiredQuantity: number
  coveredQuantity: number
  optional: boolean
}

export interface VisibleProductContext {
  sourceMessageId: string
  orderedCanonicalProductKeys: readonly string[]
}

/** `null` keeps an unbindable visible batch authoritative; `undefined` unregisters an unmounted batch. */
export type VisibleProductContextChange = (
  sourceMessageId: string,
  context: VisibleProductContext | null | undefined,
) => void

export interface SimilarityAnchor {
  canonicalProductKey: string
  inventoryItemId: string | null
  label: string
  query: string
}

export type DiscoverChatBlock =
  | { type: 'text'; text: string }
  | { type: 'newsletter' }
  | {
      type: 'products'
      products: readonly Product[]
      query?: string
      qualificationId?: string
      /** Durable TOOL message whose ordered artifacts produced these cards. */
      sourceMessageId?: string
    }
  | { type: 'reviews'; product: Product; snapshot?: ProductReviewsProfile }
  | {
      type: 'code'
      product: Product
      merchant?: string
      codes?: readonly FoundDiscountCode[]
      cached?: boolean
      searchedAt?: string | null
      expiresAt?: string | null
      status?: 'found' | 'empty' | 'error'
      message?: string
    }
  | {
      type: 'similar'
      product?: Product
      products: readonly Product[]
      query?: string
      qualificationId?: string
      /** Durable TOOL message whose ordered artifacts produced these cards. */
      sourceMessageId?: string
      anchorCanonicalProductKey?: string
      /** Trusted anchor metadata returned by the similarity tool. */
      similarityAnchor?: SimilarityAnchor
    }
  | { type: 'decision'; product: Product; runnerUp: Product | null }
  | { type: 'watch'; product: Product; price: number; merchant: string }
  | { type: 'friendvote'; person: string; product: Product; vote: 'up' | 'down'; note: string }
  | {
      type: 'added'
      product: Product
      merchant: string
      synced: boolean
      offerKey?: string
      cartLineIdentity?: string
      price?: number
      count?: number
    }
  | { type: 'saved'; products: readonly Product[] }
  | { type: 'orders'; orders: readonly Order[] }
  | { type: 'prefs'; preferences: readonly Preference[] }
  | {
      type: 'mission'
      goal: string
      status: string
      assumptions: readonly string[]
      requirements: readonly ShoppingMissionRequirement[]
    }
  | { type: 'cart'; lines: readonly CartItem[]; products?: readonly Product[] }
  | {
      type: 'checkout'
      merchantCount: number
      /** Immutable server-owned snapshot used by historical agent messages. */
      lines?: readonly CartItem[]
      products?: readonly Product[]
    }
  | {
      type: 'minicompare'
      products: readonly Product[]
      rows: readonly MiniCompareRow[]
      pickIndex: number
    }
  | { type: 'system'; text: string }

export interface DiscoverChatMessage {
  id: string
  role: 'you' | 'ai'
  text?: string
  blocks?: readonly DiscoverChatBlock[]
  pending?: boolean
  pendingText?: string
  /** Product artifacts arrived before the assistant message and are not interactive yet. */
  settling?: boolean
  suggestedReplies?: readonly string[]
  /** Values submitted by suggestion chips when their display labels include extra context. */
  suggestedReplySubmissions?: readonly string[]
  query?: string
  productContext?: Product
}

export interface DiscoverChatThread {
  id: string
  title: string
  messages: readonly DiscoverChatMessage[]
  /** Active merchant row selected for direct storefront catalog search. */
  merchantId?: string
  /** Server-owned count used while a historical transcript has not been loaded yet. */
  messageCount?: number
  named?: boolean
  archived?: boolean
  createdAt?: number
  updatedAt?: number
}

export interface ProductDetailChatRequest {
  id: string
  product: Product
  question: string
}

export type DiscoverFindRequest =
  | { id: string; kind: 'message'; messageId: string }
  | { id: string; kind: 'product'; productId: ProductId }

export interface AssistantProductAction {
  product: Product
  shouldAddToCart: boolean
  shouldOpen: boolean
}

export interface AgentActivity {
  agent: string
  label: string
  state: 'active' | 'done' | 'error'
  updatedAt: number
}
