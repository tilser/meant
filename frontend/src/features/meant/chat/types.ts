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

export type SimilarReferenceStatus = 'idle' | 'loading' | 'error'
export type SimilarMessageRole = 'request' | 'response'
export type SimilarSearchStatus = 'requested' | 'pending' | 'success' | 'empty' | 'error'

export interface SimilarProductsRehydrationResult {
  products: readonly Product[]
  unavailableCanonicalProductKeys: readonly string[]
}

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
      productResultSetId?: string
      /** Durable TOOL message whose ordered artifacts produced these cards. */
      sourceMessageId?: string
      unavailableCount?: number
      historyHydration?: 'loading' | 'loaded' | 'failed'
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
      resultCanonicalProductKeys?: readonly string[]
      /** Trusted anchor metadata returned by the similarity tool. */
      similarityAnchor?: SimilarityAnchor
    }
  | {
      type: 'similar-reference'
      anchorCanonicalProductKey: string
      resultCanonicalProductKeys: readonly string[]
      query: string
      qualificationId?: string
      status: SimilarReferenceStatus
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
  pendingOperation?: 'similar-product-search'
  suggestedReplies?: readonly string[]
  /** Values submitted by suggestion chips when their display labels include extra context. */
  suggestedReplySubmissions?: readonly string[]
  query?: string
  productContext?: Product
  similarMessageRole?: SimilarMessageRole
  similarSearchStatus?: SimilarSearchStatus
  similarAnchorCanonicalProductKey?: string
  /** Legacy runtime hint. Durable storage preserves the message and omits this flag. */
  sessionOnly?: boolean
}

export interface DiscoverChatThread {
  id: string
  title: string
  messages: readonly DiscoverChatMessage[]
  /** Active merchant row selected for direct storefront catalog search. */
  merchantId?: string
  /** Server-owned count used while a historical transcript has not been loaded yet. */
  messageCount?: number
  qualificationId?: string
  named?: boolean
  archived?: boolean
  focusProductId?: ProductId
  createdAt?: number
  updatedAt?: number
  autoTitleSource?: 'similar-product-search'
  /** Server revision this local snapshot was based on. Stored locally, never sent in the snapshot. */
  persistedRevision?: number
}

export interface DiscoverProductSearchTurnInput {
  conversationId: string
  qualificationId?: string
  merchantId?: string
  message: string
  onActivities?: (activities: readonly AgentActivity[]) => void
}

export type DiscoverProductSearchTurnResult =
  | {
      status: 'NEEDS_INPUT'
      qualificationId: string
      assistantMessage: string
      suggestedReplies: readonly string[]
      effectiveQuery: string
    }
  | {
      status: 'READY'
      qualificationId: string
      assistantMessage: string
      suggestedReplies: readonly string[]
      effectiveQuery: string
      products: readonly Product[]
      productResultSetId: string
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
