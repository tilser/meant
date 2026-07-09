import { CORE_PREFERENCE_IDS, LOCATIONS, MERCHANTS, PRODUCTS, REPLIES } from './data'
import { ApiError } from '../../lib/apiError'
import type { CartProfile } from '../../lib/apiClient'
import type {
  CartDeliveryGroup,
  CartDeliveryOption,
  CartItem,
  CartLine,
  CheckoutPayload,
  ClothingFit,
  CorePreferenceId,
  Offer,
  Order,
  Preference,
  PreferenceId,
  Product,
  ProductId,
  SmartAlert,
  UserLocation,
} from './types'

type DeliveryLocations = readonly UserLocation[]

export const IMPORT_ASK = `Based on everything you know about me from our past conversations, describe my shopping preferences in detail: my dietary needs, the materials and brands I care about, my values, my budget, and anything I like to avoid.

Write it as a short list of clear, specific statements (one per line) that a shopping app could use to filter products for me.`

export function money(value: number): string {
  return `$${value.toFixed(2)}`
}

export function productById(id: ProductId): Product {
  const product = PRODUCTS.find((candidate) => candidate.id === id)
  if (!product) {
    throw new Error(`Unknown product id: ${id}`)
  }
  return product
}

export function productsByIds(ids: readonly ProductId[]): Product[] {
  return ids.map((id) => productById(id))
}

export function prefLabel(preferences: readonly Preference[], id: PreferenceId): string {
  return preferences.find((preference) => preference.id === id)?.label ?? id
}

export function listJoin(values: readonly string[]): string {
  if (values.length === 0) {
    return 'nothing'
  }
  if (values.length === 1) {
    return values[0] ?? 'nothing'
  }
  return `${values.slice(0, -1).join(', ')} and ${values[values.length - 1]}`
}

const HIDDEN_PRODUCT_CATEGORY_VALUES = new Set(['n/a', 'na', 'none', 'not available', 'unknown'])

export function displayProductCategoryValue(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  if (!trimmed) {
    return null
  }
  const normalized = trimmed.toLowerCase()
  if (
    normalized.startsWith('gid://') ||
    normalized.startsWith('urn:') ||
    HIDDEN_PRODUCT_CATEGORY_VALUES.has(normalized)
  ) {
    return null
  }
  return trimmed
}

export function normalizedMerchantName(value: string | null | undefined): string {
  return value?.trim().toLowerCase() ?? ''
}

export function cartMerchantKey(input: {
  merchant: string
  merchantId?: string | null
  merchantDomain?: string | null
}): string {
  return (
    input.merchantId ||
    normalizedMerchantName(input.merchantDomain) ||
    normalizedMerchantName(input.merchant)
  )
}

export function firstUrl(...urls: Array<string | null | undefined>): string | null {
  return urls.find((url): url is string => Boolean(url?.trim()))?.trim() ?? null
}

export function isCartNotFoundError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.code === 'not_found')
}

export function cartRebuildItems(
  items: readonly CartItem[],
  merchantKey: string,
): { productVariantId: string; quantity: number }[] {
  return items
    .filter((item) => cartMerchantKey(item) === merchantKey && item.productVariantId)
    .map((item) => ({
      productVariantId: item.productVariantId as string,
      quantity: item.qty > 0 ? item.qty : 1,
    }))
}

export function canMerchantShip(merchant: string, locations: DeliveryLocations): boolean {
  if (locations.length === 0) {
    return true
  }
  const coverage = merchantCoverage(merchant)
  if (!coverage) {
    return true
  }
  return locations.some((location) => {
    const shipsToCountry = coverage.ships === 'global' || coverage.ships.includes(location.code)
    if (!shipsToCountry) {
      return false
    }
    if (coverage.cities) {
      return coverage.cities.includes(location.city)
    }
    return true
  })
}

function merchantCoverage(merchant: string) {
  const direct = MERCHANTS[merchant]
  if (direct) {
    return direct
  }
  const normalized = normalizedMerchantName(merchant).replace(/^www\./, '')
  return Object.entries(MERCHANTS).find(([name]) => {
    const normalizedName = normalizedMerchantName(name).replace(/^www\./, '')
    return normalizedName === normalized
  })?.[1]
}

function countryLabel(code: string): string {
  const normalized = code.trim().toUpperCase()
  const location =
    LOCATIONS.find((candidate) => candidate.code.toUpperCase() === normalized) ??
    (normalized === 'GB'
      ? LOCATIONS.find((candidate) => candidate.code.toUpperCase() === 'UK')
      : undefined)
  return location?.country ?? normalized
}

export function merchantDeliveryCoverageSummary(merchant: string): string {
  const displayName = merchant.trim() || 'This merchant'
  const coverage = merchantCoverage(merchant)
  if (!coverage) {
    return [
      `Known delivery coverage: ${displayName} has not returned supported shipping destinations to Meant yet.`,
      'I only know when checkout rejects a specific address.',
    ].join(' ')
  }
  if (coverage.ships === 'global') {
    return `Known delivery coverage: ${displayName} ships globally.`
  }
  const countries = coverage.ships.map(countryLabel)
  const countryText = `Known delivery coverage: ${displayName} ships to ${listJoin(countries)}.`
  if (!coverage.cities?.length) {
    return countryText
  }
  return `${countryText} Known city coverage: ${listJoin(coverage.cities)}.`
}

export function productMatchesClothingFit(product: Product, clothingFit: ClothingFit): boolean {
  if (clothingFit === 'none' || !product.audiences || product.audiences.length === 0) {
    return true
  }
  return product.audiences.includes('unisex') || product.audiences.includes(clothingFit)
}

export function productsForClothingFit(
  products: readonly Product[],
  clothingFit: ClothingFit,
): Product[] {
  return products.filter((product) => productMatchesClothingFit(product, clothingFit))
}

export function availableOffers(product: Product, locations: DeliveryLocations): Offer[] {
  return product.offers.filter((offer) => canMerchantShip(offer.merchant, locations))
}

export function productsForLocation(
  products: readonly Product[],
  locations: DeliveryLocations,
): Product[] {
  return products.filter((product) => availableOffers(product, locations).length > 0)
}

export function productsForPreferences(
  products: readonly Product[],
  preferences: readonly Preference[],
): Product[] {
  const activeIds = new Set(preferences.map((preference) => preference.id))
  if (activeIds.size === 0) {
    return [...products]
  }
  return [...products]
    .filter((product) => !product.misses.some((id) => activeIds.has(id)))
    .sort((left, right) => {
      const rightScore = preferenceScore(right, activeIds)
      const leftScore = preferenceScore(left, activeIds)
      return rightScore - leftScore || right.match - left.match
    })
}

function preferenceScore(product: Product, activeIds: ReadonlySet<PreferenceId>): number {
  return product.satisfies.filter((id) => activeIds.has(id)).length
}

export function productPriceFrom(product: Product, locations: DeliveryLocations): number {
  const offers = availableOffers(product, locations)
  const prices = offers.map((offer) => offer.price)
  return prices.length > 0 ? Math.min(...prices) : product.priceFrom
}

export function productMerchantCount(product: Product, locations: DeliveryLocations): number {
  const offers = availableOffers(product, locations)
  return offers.length > 0 ? offers.length : product.merchants
}

export function bestOffer(product: Product, locations: DeliveryLocations): Offer {
  const offers = availableOffers(product, locations)
  return (offers.length > 0 ? offers : product.offers).reduce((best, offer) =>
    offer.price < best.price ? offer : best,
  )
}

export function resolveReply(query: string) {
  const normalized = query.toLowerCase()
  if (/cereal|breakfast|oat/.test(normalized)) {
    return REPLIES.cereal
  }
  if (/shirt|tee|t-shirt|cotton|clothes|wear/.test(normalized)) {
    return REPLIES.tee
  }
  if (/coffee|machine|brew|espresso|pour/.test(normalized)) {
    return REPLIES.brewer
  }
  return REPLIES.default
}

const filterMatchers: ReadonlyArray<{
  id: CorePreferenceId
  pattern: RegExp
}> = [
  { id: 'organic', pattern: /organic/ },
  {
    id: 'natural-materials',
    pattern: /natural (material|fabric|fibre|fiber)|\b(cotton|wool|linen|merino|silk)\b/,
  },
  {
    id: 'no-polyester',
    pattern:
      /no polyester|avoid polyester|without polyester|no synthetic|anti-?synthetic|polyester/,
  },
  {
    id: 'highly-rated',
    pattern: /review|well[- ]?rated|highly[- ]?rated|good ratings?|top[- ]?rated|popular/,
  },
  {
    id: 'sustainable-brands',
    pattern: /sustainab|eco[- ]?friendly|ethical|environment|planet|carbon|recycl/,
  },
  {
    id: 'best-value',
    pattern: /budget|good value|best quality|affordable|value for money|bang for/,
  },
  {
    id: 'low-sugar',
    pattern: /low[- ]?sugar|less sugar|no added sugar|sugar[- ]?free|reduce sugar/,
  },
]

export interface DerivedFilters {
  matched: CorePreferenceId[]
  customs: Preference[]
}

export function deriveFilters(text: string): DerivedFilters {
  const normalized = text.toLowerCase()
  const matched = filterMatchers
    .filter(({ pattern }) => pattern.test(normalized))
    .map(({ id }) => id)

  const seen = new Set<string>()
  const customs: Preference[] = []

  text.split(/[,.;\n]|\band\b/i).forEach((fragment) => {
    const trimmed = fragment.trim()
    if (!trimmed) {
      return
    }
    const lower = trimmed.toLowerCase()
    if (filterMatchers.some(({ pattern }) => pattern.test(lower))) {
      return
    }
    const label = trimmed
      .replace(
        /^(i\s+)?(only\s+|really\s+)?(want|prefer|need|care about|like|love|am looking for|look for|avoid|don't want|dislike|hate|no|prioriti[sz]e)\s+/i,
        '',
      )
      .replace(/^(to|for|some|a|an|the)\s+/i, '')
      .trim()

    if (label.length < 3 || label.length > 38 || !/[a-z]/i.test(label)) {
      return
    }

    const pretty = `${label.charAt(0).toUpperCase()}${label.slice(1)}`
    const id = `custom-${pretty
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '')}` as PreferenceId

    if (seen.has(id) || id === 'custom-') {
      return
    }
    seen.add(id)
    customs.push({
      id,
      label: pretty,
      desc: 'Added from your own description.',
    })
  })

  return { matched, customs }
}

export function resolveAsk(
  question: string,
  product: Product | null,
  preferences: readonly Preference[],
): string {
  const normalized = question.toLowerCase()

  if (product) {
    const satisfied = product.satisfies.map((id) => prefLabel(preferences, id))
    const missed = product.misses.map((id) => prefLabel(preferences, id))

    if (/match|prefer|right for me|for me\b|suit|fits? me|good match/.test(normalized)) {
      const base = `It's a ${product.match}% match. It meets ${listJoin(satisfied).toLowerCase()}.`
      return missed.length > 0
        ? `${base} Where it falls short: ${listJoin(missed).toLowerCase()}.`
        : `${base} Nothing about it clashes with your profile.`
    }

    if (/cheap|price|cost|afford|budget|deal|expensive|save money/.test(normalized)) {
      const prices = product.offers.map((offer) => offer.price)
      return `Best price is ${money(product.offers[0].price)} at ${
        product.offers[0].merchant
      }. Across ${product.offers.length} stores it runs ${money(
        Math.min(...prices),
      )} to ${money(Math.max(...prices))}.`
    }

    if (/review|rating|people say|worth it|reliable|how good|quality/.test(normalized)) {
      if (product.review.count <= 0) {
        return 'No merchant review data is available for this product yet.'
      }
      if (product.review.score === null) {
        return `${product.review.count.toLocaleString()} merchant reviews are available. ${product.review.insight}`
      }
      return `${product.review.score}/5 from ${product.review.count.toLocaleString()} reviews. ${product.review.insight}`
    }

    if (/deliver|ship|arrive|how fast|when can/.test(normalized)) {
      const fastest = [...product.offers].sort((a, b) => a.delivery.localeCompare(b.delivery))[0]
      return `Fastest option is ${fastest.merchant}: ${fastest.delivery.toLowerCase()}.`
    }

    if (/material|made of|fabric|polyester|natural|organic|ingredient|synthetic/.test(normalized)) {
      if (product.misses.includes('no-polyester')) {
        return `Worth flagging: it contains polyester, which is on your avoid list. On the plus side, ${product.pros[0].toLowerCase()}.`
      }
      return `${listJoin(product.pros)}. ${product.note}`
    }

    if (/downside|trade.?off|con\b|cons\b|bad|negative|problem|catch/.test(normalized)) {
      return product.cons.length > 0
        ? `Worth knowing: ${listJoin(product.cons).toLowerCase()}.`
        : 'No real trade-offs for you on this one.'
    }

    return product.note
  }

  if (/cheap|budget|value|afford|save money/.test(normalized)) {
    return 'For value, the Precision Pour-Over Brewer and Sprouted Oat & Almond Cereal both punch above their price.'
  }
  if (/saved|shortlist|my list/.test(normalized)) {
    return 'Your shortlist is already filtered to your preferences. Want me to compare two of them?'
  }
  if (/compare|versus|vs\b|better/.test(normalized)) {
    return 'Open Compare and I will line products up against every preference you care about.'
  }
  if (/healthy|food|eat|breakfast|cereal|grocery|snack/.test(normalized)) {
    return 'For food, the cereal and olive oil are organic with excellent reviews.'
  }
  if (/cloth|wear|shirt|cotton|sweater|fabric|material/.test(normalized)) {
    return 'For clothing, the cotton tee and merino sweater are pure natural materials with no polyester.'
  }
  if (/coffee|machine|kitchen|brew/.test(normalized)) {
    return 'The Precision Pour-Over Brewer is the best-reviewed machine inside your budget.'
  }
  return 'Everything I show is already filtered to your profile. Ask me anything specific and I will point you to the right match.'
}

export function cartLines(cart: readonly CartItem[], products: readonly Product[]): CartLine[] {
  return cart.flatMap((item) => {
    const product = products.find((candidate) => candidate.id === item.id)
    if (!product) {
      return []
    }
    const offer =
      product.offers.find((candidate) => candidate.merchant === item.merchant) ?? product.offers[0]
    return [
      {
        ...item,
        product,
        price: offer.price,
        delivery: offer.delivery,
      },
    ]
  })
}

function cartLineForItem(
  snapshot: CartProfile,
  item: CartItem,
): NonNullable<CartProfile['lines']>[number] | undefined {
  const lines = snapshot.lines ?? []
  return lines.find((line) => {
    return Boolean(
      (item.cartLineId && line.cartLineId === item.cartLineId) ||
      (item.remoteCartLineId && line.remoteCartLineId === item.remoteCartLineId) ||
      (item.productVariantId && line.productVariantId === item.productVariantId),
    )
  })
}

function cartSnapshotIssueMessage(snapshot: CartProfile): string | null {
  const message = snapshot.messages?.find((candidate) => candidate.message?.trim())
  return message?.message?.trim() ?? null
}

export function mergeCartSnapshot(
  cart: readonly CartItem[],
  merchantKey: string,
  snapshot: CartProfile,
): CartItem[] {
  const snapshotDeliveryGroups = (
    snapshot.deliveryGroups as readonly (CartDeliveryGroup | null | undefined)[] | undefined
  )?.filter((group): group is CartDeliveryGroup => Boolean(group))
  return cart.map((item) => {
    if (cartMerchantKey(item) !== merchantKey) {
      return item
    }
    const line = cartLineForItem(snapshot, item)
    // The merchant confirmed the cart but did not return this line (e.g. the
    // variant sold out). Keep the item visible with the merchant's reason
    // instead of leaving it in a permanent "syncing" state.
    if (!line && item.productVariantId) {
      return {
        ...item,
        merchantId: snapshot.merchantId ?? item.merchantId,
        merchantDomain: snapshot.merchantDomain ?? item.merchantDomain,
        cartId: snapshot.cartId ?? item.cartId,
        remoteCartId: snapshot.remoteCartId ?? item.remoteCartId,
        checkoutUrl: snapshot.checkoutUrl ?? item.checkoutUrl,
        continueUrl: snapshot.continueUrl ?? item.continueUrl,
        syncing: false,
        syncError:
          cartSnapshotIssueMessage(snapshot) ??
          'The merchant could not add this item to the cart. It may be out of stock.',
      }
    }
    return {
      ...item,
      merchantId: snapshot.merchantId ?? item.merchantId,
      merchantDomain: snapshot.merchantDomain ?? item.merchantDomain,
      cartId: snapshot.cartId ?? item.cartId,
      remoteCartId: snapshot.remoteCartId ?? item.remoteCartId,
      checkoutUrl: snapshot.checkoutUrl ?? item.checkoutUrl,
      continueUrl: snapshot.continueUrl ?? item.continueUrl,
      cartLineId: line?.cartLineId ?? item.cartLineId,
      remoteCartLineId: line?.remoteCartLineId ?? item.remoteCartLineId,
      productVariantId: line?.productVariantId ?? item.productVariantId,
      variantTitle: line?.variantTitle ?? item.variantTitle,
      cartTotalAmount: snapshot.totalAmount ?? item.cartTotalAmount,
      cartSubtotalAmount: snapshot.subtotalAmount ?? item.cartSubtotalAmount,
      cartCurrency: snapshot.currency ?? item.cartCurrency,
      deliveryGroups:
        snapshotDeliveryGroups && snapshotDeliveryGroups.length > 0
          ? snapshotDeliveryGroups
          : (item.deliveryGroups ?? []),
      qty: line?.quantity ?? item.qty,
      syncing: false,
      syncError: null,
    }
  })
}

const portLabels: Readonly<Record<string, string>> = {
  'usb-c': 'USB-C',
  'usb-a': 'USB-A',
  hdmi: 'HDMI',
}

function portLabel(port: string): string {
  return portLabels[port] ?? port
}

export function computeSmartAlerts(
  lines: readonly CartLine[],
  products: readonly Product[],
): SmartAlert[] {
  const provided = new Set<string>()
  lines.forEach((line) => line.product.provides?.forEach((port) => provided.add(port)))

  const host = lines.find((line) => line.product.provides?.length && !line.product.needs)

  const alerts = lines.flatMap((line): SmartAlert[] => {
    const need = line.product.needs
    if (!need) {
      return []
    }

    if (!provided.has(need)) {
      const adapter = products.find(
        (product) =>
          product.provides?.includes(need) &&
          product.needs &&
          provided.has(product.needs) &&
          !lines.some((cartLine) => cartLine.id === product.id),
      )

      return [
        {
          id: `warn-${line.id}-${line.merchant}`,
          kind: 'warn',
          title: 'Connection mismatch',
          body: host
            ? `Your ${host.product.name} only has ${
                (host.product.provides ?? []).map(portLabel).join(' / ') ||
                'no listed compatible ports'
              }, but the ${line.product.name} connects over ${portLabel(need)}.`
            : `The ${line.product.name} needs a ${portLabel(need)} connection.`,
          fix: adapter
            ? {
                label: `Add the ${adapter.name}`,
                sub: `${money(adapter.priceFrom)} · resolves this`,
                id: adapter.id,
                merchant: adapter.offers[0].merchant,
              }
            : undefined,
        },
      ]
    }

    if (host && host.id !== line.id && !line.product.provides?.length) {
      return [
        {
          id: `ok-${line.id}-${line.merchant}`,
          kind: 'good',
          title: 'Works together',
          body: `The ${line.product.name} connects over ${portLabel(need)} and is compatible with your ${host.product.name}.`,
        },
      ]
    }

    return []
  })

  return alerts.sort((a, b) => (a.kind === 'warn' ? 0 : 1) - (b.kind === 'warn' ? 0 : 1))
}

export interface CartGroup {
  merchant: string
  items: CartLine[]
  subtotal: number
  deliveryRaw: number
  delivery: number
  total: number
  remoteTotal: number | null
  remoteSubtotal: number | null
  deliveryGroups: readonly CartDeliveryGroup[]
  hasDeliveryOptions: boolean
  hasSelectedDelivery: boolean
}

export function cartGroups(lines: readonly CartLine[]): CartGroup[] {
  const groups = new Map<string, CartLine[]>()
  lines.forEach((line) => {
    const existing = groups.get(line.merchant) ?? []
    groups.set(line.merchant, [...existing, line])
  })

  return [...groups.entries()].map(([merchant, items]) => {
    const localSubtotal = items.reduce((sum, item) => sum + item.price * item.qty, 0)
    const rawRemoteSubtotal = firstCartAmount(items, (item) => item.cartSubtotalAmount)
    const remoteSubtotal = reliableRemoteCartSubtotal(rawRemoteSubtotal, localSubtotal)
    const remoteTotal = reliableRemoteCartTotal(
      firstCartAmount(items, (item) => item.cartTotalAmount),
      localSubtotal,
      rawRemoteSubtotal !== null && remoteSubtotal === null,
    )
    const subtotal = remoteSubtotal ?? localSubtotal
    const deliveryGroups = firstDeliveryGroups(items)
    const deliveryGroupsWithOptions = deliveryGroups.filter(
      (group) => cartDeliveryOptions(group).length > 0,
    )
    const selectedDeliveryCost = selectedDeliveryGroupsCost(deliveryGroups)
    const inferredDelivery = remoteTotal !== null ? Math.max(0, remoteTotal - subtotal) : null
    const deliveryRaw = selectedDeliveryCost ?? inferredDelivery ?? (subtotal >= 50 ? 0 : 4.99)
    const delivery = deliveryRaw
    const total = Math.max(0, remoteTotal ?? subtotal + deliveryRaw)
    const hasDeliveryOptions = deliveryGroupsWithOptions.length > 0
    const hasSelectedDelivery = deliveryGroupsWithOptions.every((group) =>
      Boolean(selectedCartDeliveryOption(group)),
    )
    return {
      merchant,
      items,
      subtotal,
      deliveryRaw,
      delivery,
      total,
      remoteTotal,
      remoteSubtotal,
      deliveryGroups,
      hasDeliveryOptions,
      hasSelectedDelivery,
    }
  })
}

export function reliableRemoteCartSubtotal(
  remoteSubtotal: number | null,
  localSubtotal: number,
): number | null {
  if (remoteSubtotal === null) {
    return null
  }
  if (remoteSubtotal < 0) {
    return null
  }
  if (localSubtotal <= 0) {
    return remoteSubtotal
  }
  const tolerance = Math.max(5, localSubtotal * 0.25)
  if (Math.abs(remoteSubtotal - localSubtotal) > tolerance) {
    return null
  }
  return remoteSubtotal
}

export function reliableRemoteCartTotal(
  remoteTotal: number | null,
  baselineAmount: number,
  rejectedRemoteSubtotal = false,
): number | null {
  if (remoteTotal === null) {
    return null
  }
  if (rejectedRemoteSubtotal || remoteTotal <= 0) {
    return null
  }
  if (baselineAmount <= 0) {
    return remoteTotal
  }
  const maxPlausibleTotal = Math.max(baselineAmount + 100, baselineAmount * 3)
  if (remoteTotal > maxPlausibleTotal) {
    return null
  }
  return remoteTotal
}

function firstCartAmount(
  items: readonly CartLine[],
  selector: (item: CartLine) => string | null | undefined,
): number | null {
  for (const item of items) {
    const amount = parseCartAmount(selector(item))
    if (amount !== null) {
      return amount
    }
  }
  return null
}

function firstDeliveryGroups(items: readonly CartLine[]): readonly CartDeliveryGroup[] {
  for (const item of items) {
    const deliveryGroups = (item.deliveryGroups ?? []).filter((group): group is CartDeliveryGroup =>
      Boolean(group),
    )
    if (deliveryGroups.length > 0) {
      return deliveryGroups
    }
  }
  return []
}

function selectedDeliveryGroupsCost(groups: readonly CartDeliveryGroup[]): number | null {
  let total = 0
  const groupsWithOptions = groups.filter((group) => cartDeliveryOptions(group).length > 0)
  if (groupsWithOptions.length === 0) {
    return null
  }
  for (const group of groupsWithOptions) {
    const amount = cartDeliveryOptionAmount(selectedCartDeliveryOption(group))
    if (amount === null) {
      return null
    }
    total += amount
  }
  return total
}

export function selectedCartDeliveryOption(group: CartDeliveryGroup): CartDeliveryOption | null {
  const explicit = group.selectedDeliveryOption
  if (explicit?.handle || explicit?.title || explicit?.cost) {
    return explicit
  }
  return cartDeliveryOptions(group).find((option) => option.selected) ?? null
}

export function cartDeliveryOptions(group: CartDeliveryGroup): readonly CartDeliveryOption[] {
  return (group.deliveryOptions ?? []).filter((option): option is CartDeliveryOption =>
    Boolean(option),
  )
}

export function cartDeliveryOptionAmount(option?: CartDeliveryOption | null): number | null {
  return parseCartAmount(option?.cost?.amount)
}

function parseCartAmount(value?: string | number | null): number | null {
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
  if (trimmed === '') {
    return null
  }
  const amount = Number(trimmed)
  return Number.isFinite(amount) ? amount : null
}

export function orderTotal(order: Order): number {
  const total = order.items.reduce((sum, item) => {
    const product = productById(item.id)
    const offer =
      product.offers.find((candidate) => candidate.merchant === item.merchant) ?? product.offers[0]
    return sum + offer.price * item.qty
  }, 0)
  return total - order.saved
}

export function formatOrderDate(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`)
  if (Number.isNaN(date.getTime())) {
    return isoDate
  }
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

export function createOrder(payload: CheckoutPayload): Order {
  const id = `MNT-${Math.floor(1000 + Math.random() * 9000)}`
  return {
    id,
    date: new Date().toISOString().slice(0, 10),
    status: 'Processing',
    statusNote: 'Confirmed: Meant is preparing dispatch across your merchants.',
    items: payload.items,
    saved: Math.round(payload.saved * 100) / 100,
    savedNote: payload.savedNote,
  }
}

export function readStorage<T>(key: string, fallback: T): T {
  if (typeof window === 'undefined') {
    return fallback
  }
  try {
    const raw = window.localStorage.getItem(key)
    return raw === null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

export function writeStorage<T>(key: string, value: T): void {
  if (typeof window === 'undefined') {
    return
  }
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Local storage can be unavailable in private contexts.
  }
}

export function isCorePreferenceId(id: PreferenceId): id is CorePreferenceId {
  return CORE_PREFERENCE_IDS.includes(id as CorePreferenceId)
}
