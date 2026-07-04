import type { OrderProfile } from '../../../lib/apiClient'
import type { Order, OrderStatus } from '../types'

export function orderFromProfile(profile: OrderProfile): Order {
  return {
    id: profile.displayId || profile.remoteOrderId || profile.id,
    date: profile.date,
    status: profile.status as OrderStatus,
    statusNote: profile.statusNote,
    items: profile.lines.map((line) => ({
      id: line.productKey || line.productId || line.id,
      merchant: line.merchantName || profile.merchantName || profile.merchantDomain,
      qty: line.quantity ?? 0,
      merchantId: profile.merchantId,
      merchantDomain: profile.merchantDomain,
      productVariantId: line.productVariantId,
      variantTitle: line.variantTitle,
      productTitle: line.productTitle,
      imageUrl: line.imageUrl,
      productUrl: line.productUrl,
      unitPriceAmount: line.unitAmount,
      lineTotalAmount: line.totalAmount,
      orderCurrency: line.currency ?? profile.currency,
    })),
    saved: 0,
    savedNote: '',
  }
}
