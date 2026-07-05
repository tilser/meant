import type { CartProfile } from '../../../lib/apiClient'
import type {
  CartDeliveryGroup,
  CartDeliveryOption,
  CartItem,
  Offer,
  Product,
  UserLocation,
} from '../types'
import {
  cartDeliveryOptionAmount,
  cartMerchantKey,
  money,
  reliableRemoteCartSubtotal,
  reliableRemoteCartTotal,
  selectedCartDeliveryOption,
} from '../utils'
import type { AppliedCartCode, DeliveryAddressDraft, MerchantCartSnapshot } from './types'

export function emptyDeliveryAddressDraft(
  locations: readonly UserLocation[],
): DeliveryAddressDraft {
  const location = locations[0]
  return {
    countryCode: location?.code ?? 'US',
    city: location?.city ?? '',
    postalCode: '',
    provinceCode: '',
  }
}

export function deliveryAddressArguments(input: DeliveryAddressDraft): Record<string, unknown> {
  return {
    country_code: input.countryCode.trim().toUpperCase(),
    city: input.city.trim(),
    postal_code: input.postalCode.trim(),
    province_code: input.provinceCode.trim() || undefined,
  }
}

function selectedDeliveryOptionArguments(
  group: CartDeliveryGroup,
  option: CartDeliveryOption | null | undefined,
): Record<string, unknown> | null {
  const deliveryGroupId = group.id || group.handle
  const deliveryOptionHandle = option?.handle
  if (!deliveryGroupId || !deliveryOptionHandle) {
    return null
  }
  return {
    delivery_group_id: deliveryGroupId,
    delivery_option_handle: deliveryOptionHandle,
  }
}

function sameDeliveryGroup(left: CartDeliveryGroup, right: CartDeliveryGroup): boolean {
  if (left.id && right.id) {
    return left.id === right.id
  }
  if (left.handle && right.handle) {
    return left.handle === right.handle
  }
  return left === right
}

export function selectedDeliveryOptionsForCart(
  cart: readonly CartItem[],
  merchantKey: string,
  selectedGroup: CartDeliveryGroup,
  selectedOption: CartDeliveryOption,
): Record<string, unknown>[] {
  const groups = (
    cart.find((item) => cartMerchantKey(item) === merchantKey)?.deliveryGroups ?? []
  ).filter((group): group is CartDeliveryGroup => Boolean(group))
  const selectionGroups = groups.length > 0 ? groups : [selectedGroup]
  return selectionGroups
    .map((group) =>
      selectedDeliveryOptionArguments(
        group,
        sameDeliveryGroup(group, selectedGroup)
          ? selectedOption
          : selectedCartDeliveryOption(group),
      ),
    )
    .filter((selection): selection is Record<string, unknown> => Boolean(selection))
}

function formatCartAmount(amount: number, currency?: string | null): string {
  if (!currency || currency.toUpperCase() === 'USD') {
    return money(amount)
  }
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
    }).format(amount)
  } catch {
    return `${amount.toFixed(2)} ${currency}`
  }
}

export function deliveryOptionTitle(option: CartDeliveryOption): string {
  return option.title || option.code || option.handle || 'Delivery option'
}

export function deliveryOptionSpeed(option: CartDeliveryOption): string | null {
  return (
    option.deliveryEstimate ||
    option.estimatedDeliveryTime ||
    option.description ||
    option.estimatedDeliveryAt ||
    null
  )
}

export function deliveryOptionCost(
  option: CartDeliveryOption,
  fallbackCurrency?: string | null,
): string {
  const amount = cartDeliveryOptionAmount(option)
  if (amount === null) {
    return 'Cost at checkout'
  }
  return formatCartAmount(amount, option.cost?.currency ?? fallbackCurrency)
}

export function deliveryGroupSummary(
  deliveryGroups: readonly CartDeliveryGroup[],
  fallbackAmount: number,
  fallbackCurrency?: string | null,
): string {
  const selected = deliveryGroups
    .map((group) => selectedCartDeliveryOption(group))
    .filter((option): option is CartDeliveryOption => Boolean(option))
  if (selected.length === 0) {
    return fallbackAmount === 0
      ? 'Free delivery'
      : `${formatCartAmount(fallbackAmount, fallbackCurrency)} delivery`
  }
  return selected
    .map((option) => {
      const speed = deliveryOptionSpeed(option)
      return `${deliveryOptionTitle(option)} · ${deliveryOptionCost(option, fallbackCurrency)}${speed ? ` · ${speed}` : ''}`
    })
    .join(' + ')
}

export function offerCartable(offer: Offer): boolean {
  return Boolean(
    offer.productVariantId &&
    offer.available !== false &&
    (offer.merchantId || offer.merchantDomain),
  )
}

export function canResolveCartOffer(product: Product, offer: Offer): boolean {
  return Boolean(
    offer.available !== false &&
    (offer.merchantId || offer.merchantDomain || product.merchantId || product.merchantDomain) &&
    product.merchantProductId,
  )
}

function parseCartAmount(value?: string | number | null): number | null {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  const trimmed = value.trim()
  if (trimmed === '') {
    return null
  }
  const amount = Number(trimmed)
  return Number.isFinite(amount) ? amount : null
}

function looksLikeGiftCardSuffix(value: string | null): boolean {
  return Boolean(value && /^[a-z0-9]{1,4}$/i.test(value))
}

export function cartSnapshotFromProfile(
  snapshot: CartProfile,
  merchantKey: string,
  merchant: string,
): MerchantCartSnapshot {
  return {
    merchantKey,
    merchant,
    cartId: snapshot.cartId ?? null,
    remoteCartId: snapshot.remoteCartId ?? null,
    checkoutUrl: snapshot.checkoutUrl ?? null,
    continueUrl: snapshot.continueUrl ?? null,
    subtotalAmount: parseCartAmount(snapshot.subtotalAmount),
    totalAmount: parseCartAmount(snapshot.totalAmount),
    currency: snapshot.currency ?? null,
    appliedCodes: (snapshot.appliedCodes ?? [])
      .map((code): AppliedCartCode => {
        const type = code.type === 'GIFT_CARD' ? 'GIFT_CARD' : 'DISCOUNT'
        const displayCode = code.code?.trim() || null
        const transportCode =
          type === 'GIFT_CARD' && looksLikeGiftCardSuffix(displayCode) ? null : displayCode
        return {
          type,
          code: transportCode,
          displayCode,
          label: code.label ?? null,
          applicable: code.applicable ?? null,
          amount: parseCartAmount(code.amount),
          currency: code.currency ?? snapshot.currency ?? null,
        }
      })
      .filter((code) => code.code || code.label || code.amount !== null),
  }
}

export function cartMoney(value: number, currency?: string | null): string {
  if (!currency || currency === 'USD') {
    return money(value)
  }
  return `${currency} ${value.toFixed(2)}`
}

export function appliedCodeDisplay(code: AppliedCartCode): string {
  const codeValue = code.displayCode?.trim() || code.code?.trim()
  if (code.type === 'GIFT_CARD' && codeValue) {
    return `•••• ${codeValue.slice(-4)}`
  }
  const displayValue = code.label?.trim() || (code.type === 'GIFT_CARD' ? 'Gift card' : 'Discount')
  return displayValue
}

export function cartSnapshotSavings(
  snapshot: MerchantCartSnapshot | undefined,
  fallbackSubtotal: number,
  fallbackTotal: number,
): number {
  if (!snapshot) {
    return 0
  }
  const codeSavings = snapshot.appliedCodes.reduce(
    (sum, code) => sum + Math.abs(code.amount ?? 0),
    0,
  )
  if (codeSavings > 0) {
    return codeSavings
  }
  const subtotal = cartSnapshotSubtotal(snapshot, fallbackSubtotal)
  const total = cartSnapshotTotal(snapshot, fallbackTotal, fallbackSubtotal)
  const deliveryFee = Math.max(0, fallbackTotal - fallbackSubtotal)
  return Math.max(subtotal + deliveryFee - total, 0)
}

export function cartSnapshotTotal(
  snapshot: MerchantCartSnapshot | undefined,
  fallbackTotal: number,
  fallbackSubtotal = fallbackTotal,
): number {
  return reliableRemoteCartTotal(snapshot?.totalAmount ?? null, fallbackSubtotal) ?? fallbackTotal
}

export function cartSnapshotSubtotal(
  snapshot: MerchantCartSnapshot | undefined,
  fallbackSubtotal: number,
): number {
  return (
    reliableRemoteCartSubtotal(snapshot?.subtotalAmount ?? null, fallbackSubtotal) ??
    fallbackSubtotal
  )
}

export function cartSnapshotHasReliableTotal(
  snapshot: MerchantCartSnapshot | undefined,
  fallbackTotal: number,
  fallbackSubtotal = fallbackTotal,
): boolean {
  return reliableRemoteCartTotal(snapshot?.totalAmount ?? null, fallbackSubtotal) !== null
}

export function cartableOfferForProduct(product: Product): Offer | null {
  return product.offers.find(offerCartable) ?? null
}

export function resolvableOfferForProduct(product: Product): Offer | null {
  return (
    product.offers.find(offerCartable) ??
    product.offers.find((offer) => canResolveCartOffer(product, offer)) ??
    null
  )
}
