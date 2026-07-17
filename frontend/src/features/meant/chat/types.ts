import type { CartItem, Order, Preference, Product, ProductId } from '../types'

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

export type DiscoverChatBlock =
  | { type: 'text'; text: string }
  | { type: 'newsletter' }
  | { type: 'products'; products: readonly Product[]; query?: string }
  | { type: 'reviews'; product: Product }
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
  | { type: 'similar'; product: Product; products: readonly Product[] }
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
  | { type: 'cart'; lines: readonly CartItem[]; products?: readonly Product[] }
  | { type: 'checkout'; merchantCount: number }
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
  suggestedReplies?: readonly string[]
  query?: string
  productContext?: Product
  /** Marks assistant output derived from transient catalog/product facts. Never stored durably. */
  sessionOnly?: boolean
}

export interface DiscoverChatThread {
  id: string
  title: string
  messages: readonly DiscoverChatMessage[]
  qualificationId?: string
  named?: boolean
  archived?: boolean
  focusProductId?: ProductId
  createdAt?: number
  updatedAt?: number
  /** Server revision this local snapshot was based on. Stored locally, never sent in the snapshot. */
  persistedRevision?: number
}

export interface DiscoverProductSearchTurnInput {
  conversationId: string
  qualificationId?: string
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
