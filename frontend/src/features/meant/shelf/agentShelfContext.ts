import type { AgentShelfContextInput } from '../../../lib/apiClient'
import { merchantAdjacentDisplayLabel } from '../cart/merchantOrigin'
import { isLegacyCartShelfSnapshot, shelfBuyerFacingLabel } from './snapshots'
import type { ShelfItem } from './types'

function cartShelfContext(item: Extract<ShelfItem, { kind: 'message' }>) {
  const cart = item.snapshot.cart
  const itemLabel = cart ? `${cart.itemCount} cart item${cart.itemCount === 1 ? '' : 's'}` : null
  const merchantLabel = cart
    ? `${cart.merchantCount} ${cart.merchantCount === 1 ? 'merchant' : 'merchants'}`
    : null
  const relatedProductNames = (
    cart ? cart.lines.map((line) => line.name) : item.snapshot.thumbs.map((thumb) => thumb.name)
  )
    .map(shelfBuyerFacingLabel)
    .filter((name): name is string => name !== null)
  return {
    kind: 'MESSAGE' as const,
    title: 'Your cart',
    text:
      cart && cart.merchantCount > 0
        ? `${itemLabel} across ${merchantLabel}.`
        : cart
          ? `${itemLabel} saved from chat.`
          : 'Cart saved from chat.',
    relatedProductNames,
  }
}

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
      if (isLegacyCartShelfSnapshot(item.snapshot)) {
        return cartShelfContext(item)
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
