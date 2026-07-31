import type { OrderProfile } from '../../../lib/apiClient'
import { sanitizeBuyerVisibleText } from '../agent/buyerVisibleText'
import { merchantDisplayOrigin } from '../cart/merchantOrigin'
import type { CartItem, Order, OrderStatus, Product } from '../types'
import { cartItemUnitPrice } from '../utils'

export function orderFromProfile(profile: OrderProfile): Order {
  const merchantOrigin = profile.merchantDomain?.trim() || null
  return {
    id: profile.displayId || profile.remoteOrderId || profile.id,
    date: profile.date,
    status: profile.status as OrderStatus,
    statusNote: sanitizeBuyerVisibleText(profile.statusNote, merchantOrigin),
    items: (profile.lines ?? []).map((line) => ({
      id: line.productKey || line.productId || line.id,
      merchant: merchantDisplayOrigin(merchantOrigin),
      merchantOrigin,
      qty: line.quantity ?? 0,
      merchantId: profile.merchantId,
      merchantDomain: profile.merchantDomain,
      productVariantId: line.productVariantId,
      variantTitle: line.variantTitle
        ? sanitizeBuyerVisibleText(line.variantTitle, merchantOrigin)
        : line.variantTitle,
      productTitle: line.productTitle
        ? sanitizeBuyerVisibleText(line.productTitle, merchantOrigin)
        : line.productTitle,
      imageUrl: safeOrderProductUrl(line.imageUrl, merchantOrigin),
      productUrl: safeOrderProductUrl(line.productUrl, merchantOrigin),
      unitPriceAmount: line.unitAmount,
      lineTotalAmount: line.totalAmount,
      orderCurrency: line.currency ?? profile.currency,
    })),
    saved: 0,
    savedNote: '',
  }
}

function safeOrderProductUrl(value?: string | null, merchantOrigin?: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
      return null
    }
    const host = url.hostname.toLocaleLowerCase()
    const officialHost = merchantOrigin?.trim().toLocaleLowerCase() || null
    if (
      host.startsWith('mcp.') ||
      host.includes('.mcp.') ||
      ((host === 'myshopify.com' || host.endsWith('.myshopify.com')) && host !== officialHost)
    ) {
      return null
    }
    const path = url.pathname.toLocaleLowerCase().replace(/\/+$/, '') || '/'
    if (
      ['/.well-known/ucp.json', '/.well-known/ucp', '/api/ucp/mcp', '/api/mcp', '/mcp'].some(
        (protocolPath) => path === protocolPath || path.startsWith(`${protocolPath}/`),
      )
    ) {
      return null
    }
    return url.toString()
  } catch {
    return null
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
