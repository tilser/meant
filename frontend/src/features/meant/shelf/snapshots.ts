import { plainAgentText } from '../agent/agentText'
import { DEFAULT_MERCHANT_DISPLAY, merchantDisplayOrigin } from '../cart/merchantOrigin'
import { productsInDiscoverMessage } from '../chat/utils'
import type { DiscoverChatMessage } from '../chat/types'
import { productImageUrl } from '../product/productSnapshots'
import type { CartItem, Offer, Product } from '../types'
import {
  cartItemUnitMoney,
  cartMerchantKey,
  monetaryAmount,
  reliableRemoteCartSubtotal,
  reliableRemoteCartTotal,
} from '../utils'
import type {
  ShelfCartLineSnapshot,
  ShelfCartSnapshot,
  ShelfMessageSnapshot,
  ShelfProductSnapshot,
  ShelfThumb,
} from './types'

const DEFAULT_PRODUCT_NAME = 'Product'
const DEFAULT_PRODUCT_TONE = '#e7ebef'
const UUID_PATTERN = /\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b/i
const TECHNICAL_REFERENCE_PATTERN = /[a-z][a-z0-9+.-]*:\/\/\S+|\b(?:gid|urn):\S+/i

function trimmed(value: string | null | undefined): string | null {
  const candidate = value?.trim()
  return candidate ? candidate : null
}

function itemIdentifiers(item: CartItem): ReadonlySet<string> {
  return new Set(
    [
      item.id,
      item.cartId,
      item.remoteCartId,
      item.cartLineId,
      item.remoteCartLineId,
      item.offerKey,
      item.productVariantId,
      item.merchantId,
      item.merchantIntegrationId,
      item.externalMerchantId,
      item.routingScopeKey,
      item.merchantScopeKey,
    ]
      .map(trimmed)
      .filter((value): value is string => Boolean(value)),
  )
}

export function shelfBuyerFacingLabel(value: string | null | undefined): string | null {
  const candidate = trimmed(value)
  return candidate && !UUID_PATTERN.test(candidate) && !TECHNICAL_REFERENCE_PATTERN.test(candidate)
    ? candidate
    : null
}

function buyerFacingLabel(
  value: string | null | undefined,
  identifiers: ReadonlySet<string>,
): string | null {
  const candidate = shelfBuyerFacingLabel(value)
  if (!candidate) return null
  const normalized = candidate.toLocaleLowerCase()
  const containsIdentifier = [...identifiers].some((identifier) => {
    const normalizedIdentifier = identifier.toLocaleLowerCase()
    return (
      normalized === normalizedIdentifier ||
      (normalizedIdentifier.length >= 8 && normalized.includes(normalizedIdentifier))
    )
  })
  return containsIdentifier ? null : candidate
}

function shelfProductName(item: CartItem, product: Product | undefined): string {
  const identifiers = itemIdentifiers(item)
  return (
    buyerFacingLabel(product?.name, identifiers) ??
    buyerFacingLabel(item.productTitle, identifiers) ??
    DEFAULT_PRODUCT_NAME
  )
}

function shelfMerchantName(item: CartItem): string {
  const origin = merchantDisplayOrigin(item.merchantOrigin)
  if (
    origin !== DEFAULT_MERCHANT_DISPLAY &&
    buyerFacingLabel(origin, itemIdentifiers(item)) !== null
  ) {
    return origin
  }
  return DEFAULT_MERCHANT_DISPLAY
}

function normalizedQuantity(value: number): number {
  return Number.isFinite(value) && value > 0 ? Math.max(1, Math.floor(value)) : 1
}

function parsedMoney(
  value: string | number | null | undefined,
  currency: string | null | undefined,
): { amount: number; currency: string } | null {
  if (value === null || value === undefined) return null
  const normalizedValue = typeof value === 'string' ? value.trim() : value
  if (normalizedValue === '') return null
  const amount = typeof normalizedValue === 'number' ? normalizedValue : Number(normalizedValue)
  return amount < 0 ? null : monetaryAmount(amount, currency)
}

function cartOffer(item: CartItem, product: Product | undefined): Offer | undefined {
  if (!product) return undefined
  const offerKey = trimmed(item.offerKey)
  const productVariantId = trimmed(item.productVariantId)
  return (
    (offerKey ? product.offers.find((offer) => trimmed(offer.offerKey) === offerKey) : undefined) ??
    (productVariantId
      ? product.offers.find((offer) => trimmed(offer.productVariantId) === productVariantId)
      : undefined) ??
    (!offerKey && !productVariantId
      ? (product.offers.find((offer) => offer.merchant === item.merchant) ?? product.offers[0])
      : undefined)
  )
}

function cartLineMoney(
  item: CartItem,
  product: Product | undefined,
  quantity: number,
): { amount: number; currency: string } | null {
  const offer = cartOffer(item, product)
  const currency =
    item.orderCurrency ?? item.cartCurrency ?? offer?.priceCurrency ?? product?.priceCurrency
  if (product) {
    const unitMoney = cartItemUnitMoney(item, product)
    if (unitMoney) return { ...unitMoney, amount: unitMoney.amount * quantity }
  }

  const unitMoney = parsedMoney(item.unitPriceAmount, currency)
  if (unitMoney) return { ...unitMoney, amount: unitMoney.amount * quantity }

  return parsedMoney(item.lineTotalAmount, currency)
}

function shelfCartLine(item: CartItem, product: Product | undefined): ShelfCartLineSnapshot {
  const quantity = normalizedQuantity(item.qty)
  const lineMoney = cartLineMoney(item, product, quantity)
  const imageUrl = (product ? productImageUrl(product) : null) ?? trimmed(item.imageUrl)
  const delivery = buyerFacingLabel(cartOffer(item, product)?.delivery, itemIdentifiers(item))
  return {
    name: shelfProductName(item, product),
    merchant: shelfMerchantName(item),
    quantity,
    lineTotal: lineMoney?.amount ?? null,
    priceCurrency: lineMoney?.currency ?? null,
    tone: trimmed(product?.tone) ?? DEFAULT_PRODUCT_TONE,
    imageUrl,
    ...(delivery ? { delivery } : {}),
  }
}

function productsById(
  message: DiscoverChatMessage,
  cartProducts: readonly Product[] | undefined,
): ReadonlyMap<string, Product> {
  const products = new Map<string, Product>()
  for (const product of productsInDiscoverMessage(message)) products.set(product.id, product)
  for (const product of cartProducts ?? []) products.set(product.id, product)
  return products
}

interface CartGroupAccumulator {
  lineTotals: Array<{ amount: number; currency: string } | null>
  remoteTotal: { amount: number; currency: string } | null
  remoteSubtotal: { amount: number; currency: string } | null
}

function cartSummary(
  items: readonly CartItem[],
  lines: readonly ShelfCartLineSnapshot[],
): Pick<ShelfCartSnapshot, 'merchantCount' | 'total' | 'priceCurrency'> {
  const groups = new Map<string, CartGroupAccumulator>()
  items.forEach((item, index) => {
    const groupKey = cartMerchantKey(item)
    const group = groups.get(groupKey) ?? {
      lineTotals: [],
      remoteTotal: null,
      remoteSubtotal: null,
    }
    const line = lines[index]
    group.lineTotals.push(
      line?.lineTotal !== null && line?.lineTotal !== undefined && line.priceCurrency
        ? { amount: line.lineTotal, currency: line.priceCurrency }
        : null,
    )
    group.remoteTotal ??= parsedMoney(
      item.cartTotalAmount,
      item.cartCurrency ?? item.orderCurrency ?? line?.priceCurrency,
    )
    group.remoteSubtotal ??= parsedMoney(
      item.cartSubtotalAmount,
      item.cartCurrency ?? item.orderCurrency ?? line?.priceCurrency,
    )
    groups.set(groupKey, group)
  })

  const totals = [...groups.values()].map((group) => {
    if (group.lineTotals.some((total) => total === null)) return null
    const known = group.lineTotals.filter(
      (total): total is { amount: number; currency: string } => total !== null,
    )
    const currencies = new Set(known.map((total) => total.currency))
    if (known.length === 0 || currencies.size !== 1) return null
    const currency = known[0]!.currency
    const localSubtotal = known.reduce((sum, total) => sum + total.amount, 0)
    const remoteSubtotalAmount =
      group.remoteSubtotal?.currency === currency ? group.remoteSubtotal.amount : null
    const trustedRemoteSubtotal = reliableRemoteCartSubtotal(remoteSubtotalAmount, localSubtotal)
    const rejectedRemoteSubtotal = remoteSubtotalAmount !== null && trustedRemoteSubtotal === null
    const remoteTotalAmount =
      group.remoteTotal?.currency === currency ? group.remoteTotal.amount : null
    const trustedRemoteTotal = reliableRemoteCartTotal(
      remoteTotalAmount,
      trustedRemoteSubtotal ?? localSubtotal,
      rejectedRemoteSubtotal,
    )
    return {
      amount: trustedRemoteTotal ?? localSubtotal,
      currency,
    }
  })
  const knownTotals = totals.filter(
    (total): total is { amount: number; currency: string } => total !== null,
  )
  const currencies = new Set(knownTotals.map((total) => total.currency))
  const complete =
    totals.length > 0 && knownTotals.length === totals.length && currencies.size === 1

  return {
    merchantCount: groups.size,
    total: complete ? knownTotals.reduce((sum, total) => sum + total.amount, 0) : null,
    priceCurrency: complete ? knownTotals[0]!.currency : null,
  }
}

function shelfCartSnapshot(
  message: DiscoverChatMessage,
  items: readonly CartItem[],
  cartProducts: readonly Product[] | undefined,
): ShelfCartSnapshot {
  const availableProducts = productsById(message, cartProducts)
  const lines = items.map((item) => shelfCartLine(item, availableProducts.get(item.id)))
  return {
    lines,
    itemCount: lines.reduce((sum, line) => sum + line.quantity, 0),
    ...cartSummary(items, lines),
  }
}

function shelfThumb(product: Product): ShelfThumb {
  return { name: product.name, tone: product.tone, imageUrl: productImageUrl(product) }
}

export function shelfProductSnapshot(product: Product): ShelfProductSnapshot {
  return {
    productId: product.id,
    name: product.name,
    brand: product.brand,
    category: product.category,
    tone: product.tone,
    priceFrom: product.priceFrom,
    priceCurrency: product.priceCurrency,
    merchants: product.merchants,
    imageUrl: productImageUrl(product),
  }
}

/**
 * Captures the buyer-facing state of a message. Supplying live cart/products replaces the cart
 * block's immutable values, which is useful for the one cart card connected to current state.
 */
export function shelfMessageSnapshot(
  message: DiscoverChatMessage,
  liveCart?: readonly CartItem[],
  cartProducts?: readonly Product[],
): ShelfMessageSnapshot {
  const products = productsInDiscoverMessage(message)
  const cartBlock = message.blocks?.find((block) => block.type === 'cart')
  if (cartBlock) {
    const cart = shelfCartSnapshot(message, liveCart ?? cartBlock.lines, cartProducts)
    const text = `${cart.itemCount} ${cart.itemCount === 1 ? 'item' : 'items'} · ${cart.merchantCount} ${cart.merchantCount === 1 ? 'merchant' : 'merchants'}`
    return {
      side: message.role === 'you' ? 'you' : 'meant',
      title: 'Your cart',
      text,
      thumbs: cart.lines.slice(0, 6).map(({ name, tone, imageUrl }) => ({
        name,
        tone,
        imageUrl,
      })),
      cart,
    }
  }

  const sourceText =
    message.text ??
    message.blocks?.find((block) => block.type === 'text' || block.type === 'system')?.text ??
    ''
  const text = message.role === 'ai' ? plainAgentText(sourceText) : sourceText
  return {
    side: message.role === 'you' ? 'you' : 'meant',
    title: message.role === 'you' ? 'Your message' : products.length ? 'Meant picks' : 'Meant',
    text,
    thumbs: products.slice(0, 6).map(shelfThumb),
  }
}

export function isLegacyCartShelfSnapshot(snapshot: ShelfMessageSnapshot): boolean {
  if (snapshot.cart) return true
  if (snapshot.side !== 'meant') return false
  return (
    /\bcart\s+id\s*:/i.test(snapshot.text) ||
    /\byou\s+have\s+\d+\s+active\s+carts?\s+with\s+items?\s*:/i.test(snapshot.text) ||
    /\bitem\s*\|\s*qty\b/i.test(snapshot.text)
  )
}
