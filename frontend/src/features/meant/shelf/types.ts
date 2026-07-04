import type { ProductId } from '../types'

export const SHELF_DRAG_MIME = 'application/x-meant-shelf'

export interface ShelfThumb {
  name: string
  tone: string
  imageUrl?: string | null
}

export interface ShelfMessageSnapshot {
  side: 'you' | 'meant'
  title: string
  text: string
  thumbs: readonly ShelfThumb[]
}

export interface ShelfProductSnapshot {
  productId: ProductId
  name: string
  brand: string
  category: string
  tone: string
  priceFrom: number
  merchants: number
  imageUrl?: string | null
}

export type ShelfItem =
  | {
      uid: string
      kind: 'message'
      messageId: string
      collapsed: boolean
      snapshot: ShelfMessageSnapshot
    }
  | {
      uid: string
      kind: 'product'
      productId: ProductId
      collapsed: boolean
      snapshot: ShelfProductSnapshot
    }

export type ShelfDragPayload =
  | { kind: 'message'; messageId: string; snapshot: ShelfMessageSnapshot }
  | { kind: 'product'; snapshot: ShelfProductSnapshot }
