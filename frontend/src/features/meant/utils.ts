import {
  CORE_PREFERENCE_IDS,
  DISCOUNTS,
  MERCHANTS,
  PRODUCTS,
  REPLIES,
} from './data'
import type {
  CartItem,
  CartLine,
  CheckoutPayload,
  CorePreferenceId,
  CustomPreferenceId,
  DiscountCode,
  Offer,
  Order,
  Preference,
  PreferenceId,
  Product,
  ProductId,
  SmartAlert,
  UserLocation,
} from './types'

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

export function prefLabel(
  preferences: readonly Preference[],
  id: PreferenceId,
): string {
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

export function canMerchantShip(
  merchant: string,
  location: UserLocation | null,
): boolean {
  if (!location) {
    return true
  }
  const coverage = MERCHANTS[merchant]
  if (!coverage) {
    return true
  }
  const shipsToCountry =
    coverage.ships === 'global' || coverage.ships.includes(location.code)
  if (!shipsToCountry) {
    return false
  }
  if (coverage.cities) {
    return coverage.cities.includes(location.city)
  }
  return true
}

export function availableOffers(
  product: Product,
  location: UserLocation | null,
): Offer[] {
  return product.offers.filter((offer) =>
    canMerchantShip(offer.merchant, location),
  )
}

export function productsForLocation(
  products: readonly Product[],
  location: UserLocation | null,
): Product[] {
  return products.filter((product) => availableOffers(product, location).length > 0)
}

export function productPriceFrom(
  product: Product,
  location: UserLocation | null,
): number {
  const offers = availableOffers(product, location)
  const prices = offers.map((offer) => offer.price)
  return prices.length > 0 ? Math.min(...prices) : product.priceFrom
}

export function productMerchantCount(
  product: Product,
  location: UserLocation | null,
): number {
  const offers = availableOffers(product, location)
  return offers.length > 0 ? offers.length : product.merchants
}

export function bestOffer(product: Product, location: UserLocation | null): Offer {
  const offers = availableOffers(product, location)
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
    id: 'natural',
    pattern:
      /natural (material|fabric|fibre|fiber)|\b(cotton|wool|linen|merino|silk)\b/,
  },
  {
    id: 'no-poly',
    pattern:
      /no polyester|avoid polyester|without polyester|no synthetic|anti-?synthetic|polyester/,
  },
  {
    id: 'reviews',
    pattern:
      /review|well[- ]?rated|highly[- ]?rated|good ratings?|top[- ]?rated|popular/,
  },
  {
    id: 'sustainable',
    pattern: /sustainab|eco[- ]?friendly|ethical|environment|planet|carbon|recycl/,
  },
  {
    id: 'value',
    pattern: /budget|good value|best quality|affordable|value for money|bang for/,
  },
  {
    id: 'low-sugar',
    pattern:
      /low[- ]?sugar|less sugar|no added sugar|sugar[- ]?free|reduce sugar/,
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
      .replace(/(^-|-$)/g, '')}` as CustomPreferenceId

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
      return `${product.review.score}/5 from ${product.review.count.toLocaleString()} reviews. ${product.review.insight}`
    }

    if (/deliver|ship|arrive|how fast|when can/.test(normalized)) {
      const fastest = [...product.offers].sort((a, b) =>
        a.delivery.localeCompare(b.delivery),
      )[0]
      return `Fastest option is ${fastest.merchant}: ${fastest.delivery.toLowerCase()}.`
    }

    if (/material|made of|fabric|polyester|natural|organic|ingredient|synthetic/.test(normalized)) {
      if (product.misses.includes('no-poly')) {
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

export function cartLines(
  cart: readonly CartItem[],
  products: readonly Product[],
): CartLine[] {
  return cart.flatMap((item) => {
    const product = products.find((candidate) => candidate.id === item.id)
    if (!product) {
      return []
    }
    const offer =
      product.offers.find((candidate) => candidate.merchant === item.merchant) ??
      product.offers[0]
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
  lines.forEach((line) =>
    line.product.provides?.forEach((port) => provided.add(port)),
  )

  const host = lines.find(
    (line) => line.product.provides?.length && !line.product.needs,
  )

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
            ? `Your ${host.product.name} only has ${host.product.provides
                ?.map(portLabel)
                .join(' / ')}, but the ${line.product.name} connects over ${portLabel(need)}.`
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

  return alerts.sort((a, b) =>
    (a.kind === 'warn' ? 0 : 1) - (b.kind === 'warn' ? 0 : 1),
  )
}

export interface DiscountResult {
  code: DiscountCode
  save: number
}

export function bestCode(
  codes: readonly DiscountCode[] | undefined,
  subtotal: number,
  deliveryRaw: number,
): DiscountResult | null {
  let best: DiscountResult | null = null
  for (const code of codes ?? []) {
    let save = 0
    if (code.type === 'percent') {
      save = code.min && subtotal < code.min ? 0 : (subtotal * code.value) / 100
    } else if (code.type === 'fixed') {
      save = Math.min(code.value, subtotal)
    } else {
      save = deliveryRaw
    }

    if (!best || save > best.save) {
      best = { code, save }
    }
  }
  return best && best.save > 0 ? best : null
}

export interface CartGroup {
  merchant: string
  items: CartLine[]
  subtotal: number
  deliveryRaw: number
  delivery: number
  found: DiscountResult | null
  itemDiscount: number
}

export function cartGroups(
  lines: readonly CartLine[],
  scanning: boolean,
): CartGroup[] {
  const groups = new Map<string, CartLine[]>()
  lines.forEach((line) => {
    const existing = groups.get(line.merchant) ?? []
    groups.set(line.merchant, [...existing, line])
  })

  return [...groups.entries()].map(([merchant, items]) => {
    const subtotal = items.reduce((sum, item) => sum + item.price * item.qty, 0)
    const deliveryRaw = subtotal >= 50 ? 0 : 4.99
    const found = scanning ? null : bestCode(DISCOUNTS[merchant], subtotal, deliveryRaw)
    const itemDiscount = found && found.code.type !== 'shipping' ? found.save : 0
    const delivery = found && found.code.type === 'shipping' ? 0 : deliveryRaw
    return {
      merchant,
      items,
      subtotal,
      deliveryRaw,
      delivery,
      found,
      itemDiscount,
    }
  })
}

export function orderTotal(order: Order): number {
  const total = order.items.reduce((sum, item) => {
    const product = productById(item.id)
    const offer =
      product.offers.find((candidate) => candidate.merchant === item.merchant) ??
      product.offers[0]
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
