import type { CartItem } from '../types'
import { cartMerchantKey } from '../utils'

export interface CartCountSummary {
  lineItemCount: number
  unitCount: number
  merchantCount: number
}

export function cartCountSummary(items: readonly CartItem[]): CartCountSummary {
  return {
    lineItemCount: items.length,
    unitCount: items.reduce((sum, item) => sum + item.qty, 0),
    merchantCount: new Set(items.map(cartMerchantKey)).size,
  }
}

export function formatCartCount(count: number, singular: string): string {
  return `${count} ${singular}${count === 1 ? '' : 's'}`
}
