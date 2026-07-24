import type { AgentShelfContextInput } from '../../../lib/apiClient'
import { merchantAdjacentDisplayLabel } from '../cart/merchantOrigin'
import type { ShelfItem } from './types'

export function agentShelfContext(items: readonly ShelfItem[]): AgentShelfContextInput | undefined {
  if (items.length === 0) return undefined
  return {
    items: items.map((item) => {
      if (item.kind === 'product') {
        const detail = [merchantAdjacentDisplayLabel(item.snapshot.brand), item.snapshot.category]
          .filter(Boolean)
          .join(' · ')
        return {
          kind: 'PRODUCT' as const,
          canonicalProductKey: item.snapshot.productId,
          title: item.snapshot.name,
          ...(detail ? { text: detail } : {}),
          relatedProductNames: [],
        }
      }
      return {
        kind: 'MESSAGE' as const,
        title: item.snapshot.title,
        ...(item.snapshot.text ? { text: item.snapshot.text } : {}),
        relatedProductNames: item.snapshot.thumbs.map((thumb) => thumb.name),
      }
    }),
  }
}
