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
  query?: string
  productContext?: Product
}

export interface DiscoverChatThread {
  id: string
  title: string
  messages: readonly DiscoverChatMessage[]
  named?: boolean
  archived?: boolean
  focusProductId?: ProductId
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
