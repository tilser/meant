import type { ProductId } from '../types'

export const SHELF_DRAG_MIME = 'application/x-meant-shelf'

export interface ShelfThumb {
  name: string
  tone: string
  imageUrl?: string | null
}

export interface ShelfCartLineSnapshot {
  name: string
  merchant: string
  quantity: number
  lineTotal: number | null
  priceCurrency: string | null
  tone: string
  imageUrl?: string | null
  delivery?: string | null
}

export interface ShelfCartSnapshot {
  lines: readonly ShelfCartLineSnapshot[]
  itemCount: number
  merchantCount: number
  total: number | null
  priceCurrency: string | null
}

export interface ShelfMessageSnapshot {
  side: 'you' | 'meant'
  title: string
  text: string
  thumbs: readonly ShelfThumb[]
  /** Optional for backwards compatibility with message snapshots saved before cart cards existed. */
  cart?: ShelfCartSnapshot
}

export interface ShelfProductSnapshot {
  productId: ProductId
  name: string
  brand: string
  category: string
  tone: string
  priceFrom: number | null
  priceCurrency?: string | null
  merchants: number
  imageUrl?: string | null
}

export type ShelfItem =
  | {
      uid: string
      kind: 'message'
      /** Optional only for shelf entries persisted before chat provenance was recorded. */
      conversationId?: string
      messageId: string
      collapsed: boolean
      snapshot: ShelfMessageSnapshot
    }
  | {
      uid: string
      kind: 'product'
      /** Optional only for shelf entries persisted before chat provenance was recorded. */
      conversationId?: string
      messageId?: string
      productId: ProductId
      collapsed: boolean
      snapshot: ShelfProductSnapshot
    }

export type ShelfDragPayload =
  | {
      kind: 'message'
      conversationId: string
      messageId: string
      snapshot: ShelfMessageSnapshot
    }
  | {
      kind: 'product'
      conversationId: string
      messageId: string
      snapshot: ShelfProductSnapshot
    }
