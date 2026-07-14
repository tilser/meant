import type { OrderProfile } from '../../../lib/apiClient'
import type { CartItem, Order, OrderStatus, Product } from '../types'
import { cartItemUnitPrice } from '../utils'

export function orderFromProfile(profile: OrderProfile): Order {
  return {
    id: profile.displayId || profile.remoteOrderId || profile.id,
    date: profile.date,
    status: profile.status as OrderStatus,
    statusNote: profile.statusNote,
    items: (profile.lines ?? []).map((line) => ({
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

export function orderLineUnitPrice(item: CartItem, product?: Product): number {
  const unitAmount = parseOrderAmount(item.unitPriceAmount)
  if (unitAmount !== null) {
    return unitAmount
  }
  const totalAmount = parseOrderAmount(item.lineTotalAmount)
  if (totalAmount !== null && item.qty > 0) {
    return totalAmount / item.qty
  }
  return product ? (cartItemUnitPrice(item, product) ?? 0) : 0
}

export function orderLineTotal(item: CartItem, product?: Product): number {
  const totalAmount = parseOrderAmount(item.lineTotalAmount)
  return totalAmount ?? orderLineUnitPrice(item, product) * item.qty
}

function parseOrderAmount(value?: string | number | null): number | null {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  const normalized = trimmed.replace(/[^0-9.-]/g, '')
  if (!normalized || normalized === '-' || normalized === '.' || normalized === '-.') {
    return null
  }
  const amount = Number(normalized)
  return Number.isFinite(amount) ? amount : null
}
