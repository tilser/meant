import type { Product, ProductId } from '../types'

export interface ProductOpenProps {
  onOpen: (product: Product, products?: readonly Product[], researchQuery?: string | null) => void
}

export interface ProductSaveProps {
  savedSet: ReadonlySet<ProductId>
  savePendingSet: ReadonlySet<ProductId>
  onToggleSave: (product: Product) => void
  onDismiss?: (product: Product) => void
}
