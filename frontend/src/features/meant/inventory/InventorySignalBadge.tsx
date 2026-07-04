import type { Product } from '../types'

const INVENTORY_RELATIONSHIP_LABELS = {
  DUPLICATE: 'Already own',
  COMPLEMENT: 'Complements',
  RESTOCK: 'Restock',
} as const

function inventoryRelationshipLabel(
  relationship: Product['inventoryRelationship'] | undefined,
): string | null {
  if (!relationship || relationship === 'NONE') {
    return null
  }
  return INVENTORY_RELATIONSHIP_LABELS[relationship] ?? null
}

export function InventorySignalBadge({
  product,
  compact = false,
}: Readonly<{
  product: Product
  compact?: boolean
}>) {
  const label = inventoryRelationshipLabel(product.inventoryRelationship)
  if (!label) {
    return null
  }
  return (
    <span
      className={`mt-inv-signal ${compact ? 'compact' : ''} ${product.inventoryRelationship?.toLowerCase()}`}
    >
      <span className="mt-inv-signal-dot" />
      {label}
      {!compact && product.inventoryItemName ? (
        <span className="mt-inv-signal-item">{product.inventoryItemName}</span>
      ) : null}
    </span>
  )
}
