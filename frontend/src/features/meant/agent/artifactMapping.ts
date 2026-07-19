import type {
  AgentArtifactProfile,
  AgentConversationDetailProfile,
  AgentMessageProfile,
  CanonicalProductProfile,
  ProductReviewsProfile,
} from '../../../lib/apiClient'
import { createMiniCompareBlock } from '../chat/utils'
import type {
  DiscoverChatBlock,
  DiscoverChatMessage,
  FoundDiscountCode,
  ShoppingMissionRequirement,
} from '../chat/types'
import { productFromCanonical } from '../product/groupedProductMapping'
import type { AppliedCartCode, MerchantCartStateReplacement } from '../cart/types'
import type {
  CartDeliveryGroup,
  CartDeliveryOption,
  CartItem,
  Product,
  UserLocation,
} from '../types'
import { cartMerchantKey, minorUnitsToMajor } from '../utils'

type JsonRecord = Record<string, unknown>

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseRecord(payloadJson: string): JsonRecord | null {
  try {
    const value = JSON.parse(payloadJson) as unknown
    return isRecord(value) ? value : null
  } catch {
    return null
  }
}

function stringValue(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

function numberValue(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function decimalValue(value: unknown): number | null {
  const amount = stringValue(value)
  if (!amount) return null
  const parsed = Number(amount)
  return Number.isFinite(parsed) ? parsed : null
}

function unitAmount(value: unknown, quantity: number): string | null {
  const amount = stringValue(value)
  if (!amount || quantity <= 0) return null
  const parsed = Number(amount)
  return Number.isFinite(parsed) ? String(parsed / quantity) : null
}

function looksLikeGiftCardSuffix(value: string | null): boolean {
  return Boolean(value && /^[a-z0-9]{1,4}$/i.test(value))
}

function parsedAppliedCodes(value: unknown, fallbackCurrency: string | null): AppliedCartCode[] {
  if (!Array.isArray(value)) return []
  return value.filter(isRecord).flatMap((item) => {
    const type = stringValue(item.type) === 'GIFT_CARD' ? 'GIFT_CARD' : 'DISCOUNT'
    const displayCode = stringValue(item.code)?.trim() || null
    const code = type === 'GIFT_CARD' && looksLikeGiftCardSuffix(displayCode) ? null : displayCode
    const label = stringValue(item.label)
    const amount = decimalValue(item.amount)
    if (!displayCode && !label && amount === null) return []
    return [
      {
        type,
        code,
        displayCode,
        label,
        applicable: typeof item.applicable === 'boolean' ? item.applicable : null,
        amount,
        currency: stringValue(item.currency) ?? fallbackCurrency,
      },
    ]
  })
}

function parsedDeliveryOption(value: unknown): CartDeliveryOption | null {
  if (!isRecord(value)) return null
  const cost = isRecord(value.cost)
    ? {
        amount: stringValue(value.cost.amount),
        currency: stringValue(value.cost.currency),
      }
    : null
  return {
    handle: stringValue(value.handle),
    title: stringValue(value.title),
    description: stringValue(value.description),
    code: stringValue(value.code),
    cost,
    deliveryMethodType: stringValue(value.deliveryMethodType),
    deliveryEstimate: stringValue(value.deliveryEstimate),
    estimatedDeliveryTime: stringValue(value.estimatedDeliveryTime),
    estimatedDeliveryAt: stringValue(value.estimatedDeliveryAt),
    selected: typeof value.selected === 'boolean' ? value.selected : null,
  }
}

function parsedDeliveryGroups(value: unknown): CartDeliveryGroup[] {
  if (!Array.isArray(value)) return []
  return value.filter(isRecord).map((group) => ({
    id: stringValue(group.id),
    handle: stringValue(group.handle),
    deliveryOptions: Array.isArray(group.deliveryOptions)
      ? group.deliveryOptions.flatMap((option) => {
          const parsed = parsedDeliveryOption(option)
          return parsed ? [parsed] : []
        })
      : [],
    selectedDeliveryOption: parsedDeliveryOption(group.selectedDeliveryOption),
  }))
}

function canonicalProductCandidate(value: JsonRecord): JsonRecord {
  return isRecord(value.product) ? value.product : value
}

function looksCanonical(value: JsonRecord): boolean {
  return (
    Boolean(stringValue(value.key)) &&
    Array.isArray(value.offers) &&
    Array.isArray(value.media) &&
    Array.isArray(value.attributes) &&
    Array.isArray(value.materials) &&
    Array.isArray(value.certifications)
  )
}

/** Keeps agent prose inside the established plain-text chat treatment, including old messages. */
export function plainAgentText(value: string): string {
  return value
    .replace(/\[([^\]]+)]\([^)]+\)/g, '$1')
    .replace(/(^|\n)\s{0,3}#{1,6}\s+/g, '$1')
    .replace(/\*\*([^*\n]+)\*\*/g, '$1')
    .replace(/__([^_\n]+)__/g, '$1')
    .replace(/`([^`\n]+)`/g, '$1')
    .replace(/(^|\s)\*\s+(?=\S)/g, '$1')
    .replace(/\*([^*\n]+)\*/g, '$1')
    .trim()
}

function catalogSearchSubject(query: string | null | undefined): string {
  let subject = plainAgentText(query ?? '')
    .replace(/[?!.,:;]+$/g, '')
    .replace(/\s+/g, ' ')
    .trim()

  const requestPrefixes = [
    /^(?:please\s+)?(?:i\s+am|i'm|im)\s+(?:looking|searching)\s+for\s+/i,
    /^(?:please\s+)?(?:looking|searching)\s+for\s+/i,
    /^(?:please\s+)?(?:can|could|would)\s+you\s+(?:find|show me|search for|look for)\s+/i,
    /^(?:please\s+)?(?:help me\s+)?(?:find|show me|search for|look for)\s+/i,
  ]
  for (const prefix of requestPrefixes) {
    subject = subject.replace(prefix, '')
  }
  subject = subject
    .replace(/^(?:(?:some|any|a|an)\s+)+/i, '')
    .replace(/^(?:(?:cool|good|nice|great|best|new)\s+)+/i, '')
    .replace(/\s+(?:under|below|for less than)\s+[$€£]?\d.*$/i, '')
    .trim()

  if (!subject || subject.length > 72) return 'products'
  return `${subject.charAt(0).toLowerCase()}${subject.slice(1)}`
}

/** Replaces model-generated product enumeration with the short lead-in used by product cards. */
export function conciseProductResultIntroduction(
  value: string | null | undefined,
  query: string | null | undefined,
  products: readonly Pick<Product, 'name' | 'category'>[] = [],
): string {
  const text = plainAgentText(value ?? '')
  const existing =
    text.match(/^I found these\s+([^:\n]{1,72}):/i)?.[1]?.trim() ??
    text.match(/^I found (?:(?:many|several|some|a few)\s+)([^:.\n]{1,72})[.:]/i)?.[1]?.trim()
  const genericSubject = existing
    ? /^(?:grounded\s+)?(?:products|options|items|matches|results)$/i.test(existing)
    : true
  const repeatsProductName = existing
    ? products.some((product) => existing.toLowerCase().includes(product.name.toLowerCase()))
    : false
  const querySubject = catalogSearchSubject(query)
  const contextlessQuery =
    /^(?:i (?:do not|don't|dont) see any|more|try again|show me (?:more|others)|other ones)$/i.test(
      querySubject,
    )
  const category = products[0]?.category?.trim()
  const subject =
    existing && !genericSubject && !repeatsProductName
      ? existing
      : !contextlessQuery && querySubject !== 'products'
        ? querySubject
        : category && !/^(?:product|products)$/i.test(category)
          ? `${category.charAt(0).toLowerCase()}${category.slice(1)}`
          : 'products'
  return `I found these ${subject}:`
}

function providerValue(value: unknown): string | null {
  if (typeof value === 'string') return stringValue(value)
  return isRecord(value) ? stringValue(value.value) : null
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(',')}]`
  }
  if (isRecord(value)) {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`
  }
  return JSON.stringify(value) ?? 'null'
}

function normalizedLegacyProvenance(value: unknown): JsonRecord | null {
  if (!isRecord(value)) return null
  const provider = providerValue(value.provider)
  const discoverySource = isRecord(value.discoverySource) ? value.discoverySource : null
  const discoveryProvider = providerValue(discoverySource?.provider) ?? provider
  if (!provider || !discoverySource || !discoveryProvider) return null
  const localRouting = isRecord(value.localRouting) ? value.localRouting : null
  return {
    ...value,
    provider,
    merchantIntegrationId: stringValue(localRouting?.merchantIntegrationId),
    discoverySource: { ...discoverySource, provider: discoveryProvider },
  }
}

function normalizedLegacyIdentity(value: unknown): JsonRecord | null {
  if (!isRecord(value)) return null
  const provider = providerValue(value.provider)
  const merchantScope = isRecord(value.merchantScope) ? value.merchantScope : null
  const externalMerchantIdentity = isRecord(merchantScope?.externalMerchantIdentity)
    ? merchantScope.externalMerchantIdentity
    : null
  const merchantIntegrationFallbackId = stringValue(merchantScope?.merchantIntegrationFallbackId)
  if (!provider || !merchantScope || !isRecord(value.externalProductIdentity)) return null
  if (!externalMerchantIdentity && !merchantIntegrationFallbackId) return null
  return {
    provider,
    merchantIntegrationId: merchantIntegrationFallbackId,
    externalMerchantIdentity,
    merchantScope: {
      ...merchantScope,
      type: externalMerchantIdentity ? 'EXTERNAL_MERCHANT' : 'LOCAL_MERCHANT_INTEGRATION_FALLBACK',
    },
    externalProductIdentity: value.externalProductIdentity,
    externalVariantIdentity: value.externalVariantIdentity,
    components: Array.isArray(value.components) ? value.components : [],
    sellingPlanIdentity: value.sellingPlanIdentity,
  }
}

function legacyOfferArtifacts(
  artifact: AgentArtifactProfile,
  artifacts: readonly AgentArtifactProfile[],
): AgentArtifactProfile[] {
  const sameProduct = artifacts.filter(
    (candidate) =>
      candidate.type === 'OFFER' &&
      candidate.canonicalProductKey === artifact.canonicalProductKey &&
      Boolean(candidate.offerKey),
  )
  const sameMessage = sameProduct.filter((candidate) => candidate.messageId === artifact.messageId)
  return sameMessage.length > 0 ? sameMessage : sameProduct
}

function normalizedLegacyCanonicalProduct(
  payload: JsonRecord,
  artifact: AgentArtifactProfile,
  artifacts: readonly AgentArtifactProfile[],
): JsonRecord | null {
  const product = canonicalProductCandidate(payload)
  if (!looksCanonical(product)) return null
  const offerKeysByIdentity = new Map<string, string>()
  for (const candidate of legacyOfferArtifacts(artifact, artifacts)) {
    const offer = parseRecord(candidate.payloadJson)
    if (offer?.identity && candidate.offerKey) {
      offerKeysByIdentity.set(stableJson(offer.identity), candidate.offerKey)
    }
  }
  const commercialStates = isRecord(payload.commercialStates) ? payload.commercialStates : null
  const offerRankingExplanations = isRecord(payload.offerRankingExplanations)
    ? payload.offerRankingExplanations
    : null
  const normalizedOffers = (product.offers as unknown[]).flatMap((value, index) => {
    if (!isRecord(value) || !isRecord(value.identity)) return []
    const identity = normalizedLegacyIdentity(value.identity)
    const key =
      offerKeysByIdentity.get(stableJson(value.identity)) ??
      (index === 0 ? artifact.offerKey : null)
    if (!identity || !key) return []
    const provenance = Array.isArray(value.provenance)
      ? value.provenance.flatMap((item) => {
          const normalized = normalizedLegacyProvenance(item)
          return normalized ? [normalized] : []
        })
      : []
    const rankingEvidence = isRecord(value.rankingEvidence) ? value.rankingEvidence : null
    const checkoutExperience = provenance.some((item) => isRecord(item.localRouting))
      ? rankingEvidence?.checkoutCapable === true
        ? 'MEANT_MANAGED'
        : stringValue(value.checkoutUrl)
          ? 'PROVIDER_HANDOFF'
          : 'UNKNOWN'
      : stringValue(value.checkoutUrl)
        ? 'PROVIDER_HANDOFF'
        : 'UNKNOWN'
    return [
      {
        key,
        identity,
        merchantName: value.merchantName,
        variantTitle: value.variantTitle,
        price: value.price,
        listPrice: value.listPrice,
        availability: value.availability,
        delivery: Array.isArray(value.delivery) ? value.delivery : [],
        checkoutUrl: value.checkoutUrl,
        selectedOptions: Array.isArray(value.identity.selectedOptions)
          ? value.identity.selectedOptions
          : [],
        checkoutExperience,
        commercialState: isRecord(commercialStates?.[key])
          ? commercialStates[key]
          : { authority: 'DISCOVERY_OBSERVATION' },
        rankingExplanation: isRecord(offerRankingExplanations?.[key])
          ? offerRankingExplanations[key]
          : undefined,
        provenance,
      },
    ]
  })
  if (normalizedOffers.length === 0) return null
  const normalizedOfferKeys = new Set(
    normalizedOffers.flatMap((offer) => stringValue(offer.key) ?? []),
  )
  const wrappedRecommendedOfferKey = stringValue(payload.recommendedOfferKey)
  const recommendedOfferKey =
    (wrappedRecommendedOfferKey && normalizedOfferKeys.has(wrappedRecommendedOfferKey)
      ? wrappedRecommendedOfferKey
      : null) ??
    (artifact.offerKey && normalizedOfferKeys.has(artifact.offerKey) ? artifact.offerKey : null) ??
    stringValue(normalizedOffers[0]?.key)
  if (!recommendedOfferKey) return null
  const provenance = Array.isArray(product.provenance)
    ? product.provenance.flatMap((item) => {
        const normalized = normalizedLegacyProvenance(item)
        return normalized ? [normalized] : []
      })
    : []
  return {
    ...product,
    provenance,
    rankingExplanation: isRecord(payload.productRankingExplanation)
      ? payload.productRankingExplanation
      : undefined,
    personalization: isRecord(payload.personalization)
      ? payload.personalization
      : {
          whyMeantForYou:
            'This looks relevant to your search based on the available product details.',
          matchedFilterIds: [],
          missedFilterIds: [],
        },
    recommendedOfferKey,
    offers: normalizedOffers,
  }
}

/** Maps the durable PRODUCT artifact, tolerating detail tools that wrap it in `{ product }`. */
export function productFromAgentArtifact(
  artifact: AgentArtifactProfile,
  siblingArtifacts: readonly AgentArtifactProfile[] = [artifact],
): Product | null {
  if (artifact.type === 'SAVED_PRODUCT') {
    return savedProductFromArtifact(artifact)
  }
  if (artifact.type !== 'PRODUCT') {
    return null
  }
  const payload = parseRecord(artifact.payloadJson)
  if (!payload) {
    return null
  }
  const candidate = canonicalProductCandidate(payload)
  if (!looksCanonical(candidate)) {
    return null
  }
  try {
    return productFromCanonical(candidate as unknown as CanonicalProductProfile)
  } catch {
    const legacy = normalizedLegacyCanonicalProduct(payload, artifact, siblingArtifacts)
    if (!legacy) return null
    try {
      return productFromCanonical(legacy as unknown as CanonicalProductProfile)
    } catch {
      return null
    }
  }
}

function savedProductFromArtifact(artifact: AgentArtifactProfile): Product | null {
  const value = parseRecord(artifact.payloadJson)
  const id = stringValue(value?.id) ?? artifact.canonicalProductKey
  const name = stringValue(value?.name) ?? artifact.label
  if (!value || !id || !name) {
    return null
  }
  const offerValues = Array.isArray(value.offers) ? value.offers.filter(isRecord) : []
  const offers = offerValues.flatMap((offer) => {
    const merchant = stringValue(offer.merchant)
    const price = numberValue(offer.price)
    if (!merchant || price === null) {
      return []
    }
    return [
      {
        offerKey: stringValue(offer.offerKey),
        merchant,
        price,
        priceMinorUnits: numberValue(offer.priceMinorUnits),
        priceCurrency: stringValue(offer.priceCurrency),
        delivery: stringValue(offer.delivery) ?? 'Delivery calculated by merchant',
        merchantId: stringValue(offer.merchantId),
        merchantDomain: stringValue(offer.merchantDomain),
        productVariantId: stringValue(offer.productVariantId),
        variantTitle: stringValue(offer.variantTitle),
        available: typeof offer.available === 'boolean' ? offer.available : null,
      },
    ]
  })
  const review = isRecord(value.review) ? value.review : null
  return {
    id,
    productHash: stringValue(value.productHash),
    name,
    brand: stringValue(value.brand) ?? 'Merchant',
    category: stringValue(value.category) ?? 'Product',
    tone: stringValue(value.tone) ?? '#e7ebef',
    imageUrl: stringValue(value.imageUrl),
    productUrl: stringValue(value.productUrl),
    remote: value.remote !== false,
    match: Math.max(0, Math.min(100, numberValue(value.match) ?? 0)),
    priceFrom:
      numberValue(value.priceFrom) ??
      minorUnitsToMajor(numberValue(value.priceFromMinorUnits), stringValue(value.priceCurrency)),
    priceFromMinorUnits: numberValue(value.priceFromMinorUnits),
    priceCurrency: stringValue(value.priceCurrency),
    listPrice: null,
    merchants: numberValue(value.merchants) ?? new Set(offers.map((offer) => offer.merchant)).size,
    satisfies: Array.isArray(value.satisfies)
      ? value.satisfies.filter((item): item is string => typeof item === 'string')
      : [],
    misses: Array.isArray(value.misses)
      ? value.misses.filter((item): item is string => typeof item === 'string')
      : [],
    note: stringValue(value.note) ?? 'Saved for later.',
    pros: Array.isArray(value.pros)
      ? value.pros.filter((item): item is string => typeof item === 'string')
      : [],
    cons: Array.isArray(value.cons)
      ? value.cons.filter((item): item is string => typeof item === 'string')
      : [],
    review: {
      score: numberValue(review?.score),
      count: numberValue(review?.count) ?? 0,
      insight: stringValue(review?.insight) ?? 'Review data varies by merchant.',
    },
    offers,
  }
}

export interface AgentProductSnapshot {
  product: Product
  createdAt: string
}

const ISO_INSTANT_PATTERN = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})$/

function instantNanoseconds(value: string): bigint | null {
  const match = ISO_INSTANT_PATTERN.exec(value)
  const base = match?.[1]
  const zone = match?.[3]
  if (!base || !zone) return null
  const milliseconds = Date.parse(`${base}${zone}`)
  if (!Number.isFinite(milliseconds)) return null
  const fraction = (match?.[2] ?? '').slice(0, 9).padEnd(9, '0')
  return BigInt(milliseconds) * 1_000_000n + BigInt(fraction || '0')
}

function compareArtifactTimestamps(left: string, right: string): number {
  const leftNanoseconds = instantNanoseconds(left)
  const rightNanoseconds = instantNanoseconds(right)
  if (leftNanoseconds !== null && rightNanoseconds !== null) {
    return leftNanoseconds < rightNanoseconds ? -1 : leftNanoseconds > rightNanoseconds ? 1 : 0
  }
  const leftMilliseconds = Date.parse(left)
  const rightMilliseconds = Date.parse(right)
  if (Number.isFinite(leftMilliseconds) && Number.isFinite(rightMilliseconds)) {
    return leftMilliseconds - rightMilliseconds
  }
  if (Number.isFinite(leftMilliseconds) !== Number.isFinite(rightMilliseconds)) {
    return Number.isFinite(leftMilliseconds) ? 1 : -1
  }
  return left.localeCompare(right)
}

function selectedAgentProductSnapshots(
  artifacts: readonly AgentArtifactProfile[],
): AgentProductSnapshot[] {
  const byId = new Map<string, AgentProductSnapshot>()
  for (const artifact of artifacts) {
    const product = productFromAgentArtifact(artifact, artifacts)
    if (product) {
      const existing = byId.get(product.id)
      if (!existing || compareArtifactTimestamps(existing.createdAt, artifact.createdAt) <= 0) {
        byId.set(product.id, { product, createdAt: artifact.createdAt })
      }
    }
  }
  for (const artifact of artifacts) {
    const product = cartLineProductFromArtifact(artifact, artifacts)
    const selectedProducts = [...byId.values()].map((snapshot) => snapshot.product)
    if (!product || productForOffer(selectedProducts, product.offers[0]?.offerKey ?? null)) {
      continue
    }
    const existing = byId.get(product.id)
    byId.set(product.id, {
      product: existing
        ? {
            ...existing.product,
            offers: [
              ...existing.product.offers,
              ...product.offers.filter(
                (offer) =>
                  !existing.product.offers.some(
                    (candidate) => candidate.offerKey && candidate.offerKey === offer.offerKey,
                  ),
              ),
            ],
          }
        : product,
      createdAt:
        existing && compareArtifactTimestamps(existing.createdAt, artifact.createdAt) > 0
          ? existing.createdAt
          : artifact.createdAt,
    })
  }
  return [...byId.values()]
}

export function productsFromAgentArtifacts(artifacts: readonly AgentArtifactProfile[]): Product[] {
  return selectedAgentProductSnapshots(artifacts).map((snapshot) => snapshot.product)
}

export function mergeAgentProductSnapshots(
  current: readonly AgentProductSnapshot[],
  incoming: readonly AgentProductSnapshot[],
): AgentProductSnapshot[] {
  const byId = new Map(current.map((snapshot) => [snapshot.product.id, snapshot]))
  incoming.forEach((snapshot) => {
    const existing = byId.get(snapshot.product.id)
    if (!existing || compareArtifactTimestamps(existing.createdAt, snapshot.createdAt) <= 0) {
      byId.set(snapshot.product.id, snapshot)
    }
  })
  return [...byId.values()]
}

/** Product state safe to promote outside one transcript: typed products plus current carts only. */
export function currentAgentProductSnapshots(
  artifacts: readonly AgentArtifactProfile[],
): AgentProductSnapshot[] {
  const currentArtifacts = [
    ...artifacts.filter(
      (artifact) =>
        artifact.type === 'PRODUCT' ||
        artifact.type === 'OFFER' ||
        artifact.type === 'SAVED_PRODUCT',
    ),
    ...latestCartSnapshotArtifacts(artifacts),
  ]
  return selectedAgentProductSnapshots(currentArtifacts)
}

export function latestAgentArtifacts(
  artifacts: readonly AgentArtifactProfile[],
): AgentArtifactProfile[] {
  const latest = new Map<string, AgentArtifactProfile>()
  for (const artifact of artifacts) {
    const current = latest.get(artifact.stableKey)
    if (!current || compareArtifactTimestamps(current.createdAt, artifact.createdAt) <= 0) {
      latest.set(artifact.stableKey, artifact)
    }
  }
  return [...latest.values()].sort(
    (left, right) =>
      compareArtifactTimestamps(left.createdAt, right.createdAt) || left.ordinal - right.ordinal,
  )
}

function cartSnapshotKey(artifact: AgentArtifactProfile): string {
  return `${artifact.messageId}\u0000${artifact.cartId ?? ''}`
}

function isNewerArtifact(candidate: AgentArtifactProfile, current: AgentArtifactProfile): boolean {
  return (
    compareArtifactTimestamps(candidate.createdAt, current.createdAt) > 0 ||
    (compareArtifactTimestamps(candidate.createdAt, current.createdAt) === 0 &&
      candidate.ordinal >= current.ordinal)
  )
}

function isPreferredCartSnapshot(
  candidate: AgentArtifactProfile,
  current: AgentArtifactProfile,
): boolean {
  const timestampComparison = compareArtifactTimestamps(candidate.createdAt, current.createdAt)
  if (timestampComparison !== 0) return timestampComparison > 0
  if (candidate.messageId === current.messageId) return candidate.ordinal < current.ordinal
  return candidate.ordinal >= current.ordinal
}

function cartPartitionKey(cart: ParsedCartArtifact): string {
  const provider = cart.provider?.trim().toLowerCase() ?? ''
  if (cart.routingScopeKey) return `routing:${cart.routingScopeKey.trim().toLowerCase()}`
  if (cart.merchantIntegrationId) {
    return `integration:${cart.merchantIntegrationId.trim().toLowerCase()}`
  }
  if (cart.merchantId) return `merchant:${cart.merchantId.trim().toLowerCase()}`
  if (cart.externalMerchantId) {
    return `external:${provider}:${cart.externalMerchantId.trim().toLowerCase()}`
  }
  if (cart.merchantDomain) {
    return `domain:${provider}:${cart.merchantDomain.trim().toLowerCase()}`
  }
  return `cart:${cart.cartId}`
}

/** Chooses complete cart snapshots (CART plus its sibling CART_LINE rows), not stale lines. */
export function latestCartSnapshotArtifacts(
  artifacts: readonly AgentArtifactProfile[],
  beforeOrAt?: string,
): AgentArtifactProfile[] {
  const selectedByCartId = new Map<string, AgentArtifactProfile>()
  for (const artifact of artifacts) {
    if (
      artifact.type !== 'CART' ||
      !artifact.cartId ||
      (beforeOrAt && compareArtifactTimestamps(artifact.createdAt, beforeOrAt) > 0)
    ) {
      continue
    }
    const cart = parsedCartArtifact(artifact)
    if (!cart) continue
    const current = selectedByCartId.get(cart.cartId)
    if (!current || isPreferredCartSnapshot(artifact, current)) {
      selectedByCartId.set(cart.cartId, artifact)
    }
  }

  const selectedCarts = new Map<string, AgentArtifactProfile>()
  for (const artifact of selectedByCartId.values()) {
    const cart = parsedCartArtifact(artifact)
    if (!cart) continue
    const partitionKey = cartPartitionKey(cart)
    const current = selectedCarts.get(partitionKey)
    if (!current || isPreferredCartSnapshot(artifact, current)) {
      selectedCarts.set(partitionKey, artifact)
    }
  }
  const selectedArtifactIds = new Set(
    [...selectedCarts.values()].map((artifact) => artifact.artifactId),
  )
  const selectedSnapshots = new Set([...selectedCarts.values()].map(cartSnapshotKey))
  return artifacts.filter(
    (artifact) =>
      (artifact.type === 'CART' && selectedArtifactIds.has(artifact.artifactId)) ||
      (artifact.type === 'CART_LINE' && selectedSnapshots.has(cartSnapshotKey(artifact))),
  )
}

export interface AgentProductInteractionState {
  pinned: ReadonlySet<string>
  watched: ReadonlySet<string>
}

export function productInteractionState(
  artifacts: readonly AgentArtifactProfile[],
): AgentProductInteractionState {
  const latest = new Map<string, { createdAt: string; value: JsonRecord }>()
  for (const artifact of artifacts) {
    if (artifact.type !== 'PRODUCT_STATE' || !artifact.canonicalProductKey) {
      continue
    }
    const value = parseRecord(artifact.payloadJson)
    const current = latest.get(artifact.canonicalProductKey)
    if (
      value &&
      (!current || compareArtifactTimestamps(current.createdAt, artifact.createdAt) <= 0)
    ) {
      latest.set(artifact.canonicalProductKey, { createdAt: artifact.createdAt, value })
    }
  }
  const pinned = new Set<string>()
  const watched = new Set<string>()
  for (const [key, state] of latest) {
    if (state.value.pinned === true) pinned.add(key)
    if (state.value.watched === true) watched.add(key)
  }
  return { pinned, watched }
}

function productForOffer(products: readonly Product[], offerKey: string | null): Product | null {
  if (!offerKey) return null
  return (
    products.find((product) =>
      product.canonicalProduct?.offers.some((offer) => offer.key === offerKey),
    ) ??
    products.find((product) => product.offers.some((offer) => offer.offerKey === offerKey)) ??
    null
  )
}

interface ParsedCartArtifact {
  cartId: string
  merchant: string
  merchantKey: string
  merchantId: string | null
  merchantDomain: string | null
  provider: string | null
  merchantIntegrationId: string | null
  externalMerchantId: string | null
  routingScopeKey: string | null
  remoteCartId: string | null
  checkoutUrl: string | null
  continueUrl: string | null
  totalAmount: string | null
  subtotalAmount: string | null
  currency: string | null
  appliedCodes: AppliedCartCode[]
  deliveryGroups: CartDeliveryGroup[]
}

function parsedCartArtifact(artifact: AgentArtifactProfile): ParsedCartArtifact | null {
  if (artifact.type !== 'CART' || !artifact.cartId) return null
  const value = parseRecord(artifact.payloadJson)
  const merchant =
    stringValue(value?.merchantDomain) ??
    stringValue(value?.provider) ??
    artifact.label ??
    'Merchant'
  const merchantId = stringValue(value?.merchantId)
  const merchantDomain = stringValue(value?.merchantDomain)
  const routingScopeKey = stringValue(value?.routingScopeKey)
  const currency = stringValue(value?.currency)
  return {
    cartId: artifact.cartId,
    merchant,
    merchantKey: cartMerchantKey({
      merchant,
      merchantId,
      merchantDomain,
      merchantScopeKey: routingScopeKey,
    }),
    merchantId,
    merchantDomain,
    provider: stringValue(value?.provider),
    merchantIntegrationId: stringValue(value?.merchantIntegrationId),
    externalMerchantId: stringValue(value?.externalMerchantId),
    routingScopeKey,
    remoteCartId: stringValue(value?.remoteCartId),
    checkoutUrl: stringValue(value?.checkoutUrl),
    continueUrl: stringValue(value?.continueUrl),
    totalAmount: stringValue(value?.totalAmount),
    subtotalAmount: stringValue(value?.subtotalAmount),
    currency,
    appliedCodes: parsedAppliedCodes(value?.appliedCodes, currency),
    deliveryGroups: parsedDeliveryGroups(value?.deliveryGroups),
  }
}

function cartLineProductFromArtifact(
  artifact: AgentArtifactProfile,
  artifacts: readonly AgentArtifactProfile[],
): Product | null {
  if (artifact.type !== 'CART_LINE' || !artifact.cartId) return null
  const line = parseRecord(artifact.payloadJson)
  const offerKey = stringValue(line?.offerKey) ?? artifact.offerKey
  const name = stringValue(line?.productTitle) ?? stringValue(artifact.label)
  if (!line || !offerKey || !name) return null

  const cartArtifacts = artifacts.filter(
    (candidate) => candidate.type === 'CART' && candidate.cartId === artifact.cartId,
  )
  const siblingCarts = cartArtifacts.filter(
    (candidate) => candidate.messageId === artifact.messageId,
  )
  const cartArtifact = (siblingCarts.length > 0 ? siblingCarts : cartArtifacts).reduce<
    AgentArtifactProfile | undefined
  >(
    (current, candidate) => (!current || isNewerArtifact(candidate, current) ? candidate : current),
    undefined,
  )
  const cart = cartArtifact ? parsedCartArtifact(cartArtifact) : null
  const quantity = Math.max(1, numberValue(line.quantity) ?? 1)
  const unitPriceAmount =
    unitAmount(line.subtotalAmount, quantity) ?? unitAmount(line.totalAmount, quantity)
  const price = decimalValue(unitPriceAmount)
  const currency = stringValue(line.currency) ?? cart?.currency ?? null
  const provider = stringValue(line.provider) ?? cart?.provider ?? null
  const merchant = cart?.merchant ?? provider ?? 'Merchant'
  const merchantIntegrationId =
    stringValue(line.merchantIntegrationId) ?? cart?.merchantIntegrationId ?? null
  const externalMerchantId =
    stringValue(line.externalMerchantId) ?? cart?.externalMerchantId ?? null

  return {
    id: stringValue(line.productId) ?? artifact.canonicalProductKey ?? `agent-cart:${offerKey}`,
    merchantId: cart?.merchantId ?? null,
    merchantDomain: cart?.merchantDomain ?? null,
    name,
    brand: merchant,
    category: 'Product',
    tone: '#e7ebef',
    imageUrl: stringValue(line.imageUrl),
    productUrl: stringValue(line.productUrl),
    remote: true,
    match: 0,
    rankingUnavailable: true,
    priceFrom: price,
    priceFromMinorUnits: null,
    priceCurrency: currency,
    listPrice: null,
    merchants: 1,
    satisfies: [],
    misses: [],
    note: 'Current cart item.',
    pros: [],
    cons: [],
    review: {
      score: null,
      count: 0,
      insight: 'Review data varies by merchant.',
    },
    offers: [
      {
        offerKey,
        merchant,
        price: price ?? 0,
        priceMinorUnits: null,
        priceCurrency: currency,
        delivery: 'Delivery calculated by merchant',
        merchantId: cart?.merchantId ?? null,
        merchantDomain: cart?.merchantDomain ?? null,
        provider,
        merchantIntegrationId,
        externalMerchantId,
        merchantScopeKey: cart?.routingScopeKey ?? cart?.merchantKey ?? null,
        productVariantId: stringValue(line.productVariantId),
        variantTitle: stringValue(line.variantTitle),
        available: null,
      },
    ],
  }
}

/** Reconstructs immutable cart lines from CART/CART_LINE artifacts for inline history rendering. */
export function cartItemsFromAgentArtifacts(
  artifacts: readonly AgentArtifactProfile[],
  products: readonly Product[],
): CartItem[] {
  const resolvedProducts = new Map(products.map((product) => [product.id, product]))
  for (const product of productsFromAgentArtifacts(artifacts)) {
    const existing = resolvedProducts.get(product.id)
    resolvedProducts.set(
      product.id,
      existing
        ? {
            ...existing,
            offers: [
              ...existing.offers,
              ...product.offers.filter(
                (offer) =>
                  !existing.offers.some(
                    (candidate) => candidate.offerKey && candidate.offerKey === offer.offerKey,
                  ),
              ),
            ],
          }
        : product,
    )
  }
  const availableProducts = [...resolvedProducts.values()]
  const carts = new Map<string, ParsedCartArtifact>()
  for (const artifact of artifacts) {
    const parsed = parsedCartArtifact(artifact)
    if (parsed) carts.set(parsed.cartId, parsed)
  }
  return artifacts.flatMap((artifact) => {
    if (artifact.type !== 'CART_LINE' || !artifact.cartId || !artifact.cartLineId) return []
    const line = parseRecord(artifact.payloadJson)
    const offerKey = stringValue(line?.offerKey) ?? artifact.offerKey
    const product = productForOffer(availableProducts, offerKey)
    const quantity = numberValue(line?.quantity) ?? 1
    const cart = carts.get(artifact.cartId)
    if (!product || !offerKey) return []
    return [
      {
        id: product.id,
        merchant: cart?.merchant ?? 'Merchant',
        qty: Math.max(1, quantity),
        merchantId: cart?.merchantId,
        merchantDomain: cart?.merchantDomain,
        provider: stringValue(line?.provider) ?? cart?.provider,
        merchantIntegrationId:
          stringValue(line?.merchantIntegrationId) ?? cart?.merchantIntegrationId,
        externalMerchantId: stringValue(line?.externalMerchantId) ?? cart?.externalMerchantId,
        routingScopeKey: cart?.routingScopeKey,
        merchantScopeKey: cart?.merchantKey,
        productVariantId: stringValue(line?.productVariantId),
        cartId: artifact.cartId,
        remoteCartId: cart?.remoteCartId,
        checkoutUrl: cart?.checkoutUrl,
        continueUrl: cart?.continueUrl,
        cartLineId: artifact.cartLineId,
        remoteCartLineId: stringValue(line?.remoteCartLineId),
        offerKey,
        cartTotalAmount: cart?.totalAmount,
        cartSubtotalAmount: cart?.subtotalAmount,
        cartCurrency: cart?.currency,
        deliveryGroups: cart?.deliveryGroups ?? [],
        productTitle: stringValue(line?.productTitle) ?? product.name,
        variantTitle: stringValue(line?.variantTitle),
        unitPriceAmount:
          unitAmount(line?.subtotalAmount, Math.max(1, quantity)) ??
          unitAmount(line?.totalAmount, Math.max(1, quantity)),
        lineTotalAmount: stringValue(line?.totalAmount),
        orderCurrency: stringValue(line?.currency),
      },
    ]
  })
}

/** Projects complete agent cart artifacts into the shared cart controller's replacement seam. */
export function cartStateReplacementsFromAgentArtifacts(
  artifacts: readonly AgentArtifactProfile[],
  products: readonly Product[],
): MerchantCartStateReplacement[] {
  const snapshotArtifacts = latestCartSnapshotArtifacts(artifacts)
  const lines = cartItemsFromAgentArtifacts(snapshotArtifacts, products)
  return snapshotArtifacts.flatMap((artifact) => {
    const cart = parsedCartArtifact(artifact)
    if (!cart) return []
    const rawLines = snapshotArtifacts.filter(
      (candidate) =>
        candidate.type === 'CART_LINE' &&
        candidate.messageId === artifact.messageId &&
        candidate.cartId === cart.cartId,
    )
    const cartLines = lines.filter((line) => line.cartId === cart.cartId)
    if (cartLines.length !== rawLines.length) return []
    return [
      {
        merchantKey: cart.merchantKey,
        merchantId: cart.merchantId,
        merchantDomain: cart.merchantDomain,
        provider: cart.provider,
        merchantIntegrationId: cart.merchantIntegrationId,
        externalMerchantId: cart.externalMerchantId,
        routingScopeKey: cart.routingScopeKey,
        snapshot: {
          merchantKey: cart.merchantKey,
          merchant: cart.merchant,
          cartId: cart.cartId,
          remoteCartId: cart.remoteCartId,
          checkoutUrl: cart.checkoutUrl,
          continueUrl: cart.continueUrl,
          subtotalAmount: decimalValue(cart.subtotalAmount),
          totalAmount: decimalValue(cart.totalAmount),
          currency: cart.currency,
          appliedCodes: cart.appliedCodes,
        },
        lines: cartLines,
      },
    ]
  })
}

function discountCodes(artifact: AgentArtifactProfile): FoundDiscountCode[] {
  const value = parseRecord(artifact.payloadJson)
  const codes = Array.isArray(value?.codes) ? value.codes.filter(isRecord) : []
  return codes.flatMap((item) => {
    const code = stringValue(item.code)
    if (!code) return []
    return [
      {
        code,
        title: stringValue(item.title),
        description: stringValue(item.description),
        sourceUrl: stringValue(item.sourceUrl),
        confidence: numberValue(item.confidence),
        restrictions: stringValue(item.restrictions),
        validUntil: stringValue(item.validUntil),
        expiresAt: stringValue(item.expiresAt),
        validationMessage: stringValue(item.validationMessage),
      },
    ]
  })
}

function productByCanonicalKey(products: readonly Product[], key: string | null): Product | null {
  return key ? (products.find((product) => product.id === key) ?? null) : null
}

function productReviewsSnapshot(artifact: AgentArtifactProfile): ProductReviewsProfile | undefined {
  const value = parseRecord(artifact.payloadJson)
  if (
    !value ||
    !stringValue(value.merchantId) ||
    !stringValue(value.productId) ||
    !stringValue(value.provider) ||
    !Array.isArray(value.reviews) ||
    typeof value.hasMore !== 'boolean' ||
    typeof value.cached !== 'boolean' ||
    typeof value.supported !== 'boolean'
  ) {
    return undefined
  }
  return value as unknown as ProductReviewsProfile
}

function missionBlock(artifact: AgentArtifactProfile): DiscoverChatBlock | null {
  const value = parseRecord(artifact.payloadJson)
  const goal = stringValue(value?.goal) ?? artifact.label
  const status = stringValue(value?.status)
  if (!value || !goal || !status) return null
  const coverageByRequirement = new Map<string, JsonRecord>()
  if (Array.isArray(value.coverage)) {
    for (const item of value.coverage.filter(isRecord)) {
      const requirementId = stringValue(item.requirementId)
      if (requirementId) coverageByRequirement.set(requirementId, item)
    }
  }
  const requirements = Array.isArray(value.requirements)
    ? value.requirements.filter(isRecord).flatMap((item) => {
        const id = stringValue(item.id)
        const label = stringValue(item.label)
        if (!id || !label) return []
        const coverage = coverageByRequirement.get(id)
        const rawState = stringValue(coverage?.state)
        const state: ShoppingMissionRequirement['state'] =
          rawState === 'MISSING' ||
          rawState === 'PARTIAL' ||
          rawState === 'COVERED' ||
          rawState === 'OPTIONAL'
            ? rawState
            : item.optional === true
              ? 'OPTIONAL'
              : 'MISSING'
        return [
          {
            id,
            label,
            state,
            requiredQuantity: Math.max(
              1,
              numberValue(coverage?.requiredQuantity) ?? numberValue(item.requiredQuantity) ?? 1,
            ),
            coveredQuantity: Math.max(0, numberValue(coverage?.coveredQuantity) ?? 0),
            optional: item.optional === true,
          },
        ]
      })
    : []
  const assumptions = Array.isArray(value.assumptions)
    ? value.assumptions.filter(isRecord).flatMap((item) => {
        const assumption = stringValue(item.value)
        return assumption ? [assumption] : []
      })
    : []
  return { type: 'mission', goal, status, assumptions, requirements }
}

/** Produces the existing typed chat blocks from immutable artifacts owned by one ledger message. */
export function blocksForAgentMessage(
  message: AgentMessageProfile,
  messageArtifacts: readonly AgentArtifactProfile[],
  allArtifacts: readonly AgentArtifactProfile[],
  deliveryLocations: readonly UserLocation[],
): DiscoverChatBlock[] {
  const allProducts = productsFromAgentArtifacts(allArtifacts)
  const products = productsFromAgentArtifacts(
    messageArtifacts.filter(
      (artifact) =>
        artifact.type === 'PRODUCT' ||
        artifact.type === 'OFFER' ||
        artifact.type === 'SAVED_PRODUCT',
    ),
  )
  const blocks: DiscoverChatBlock[] = []
  const toolName = message.correlationId?.split(':').at(-1) ?? ''
  const comparison = messageArtifacts.find((artifact) => artifact.type === 'COMPARISON')
  const mission = messageArtifacts.find((artifact) => artifact.type === 'MISSION')
  if (mission) {
    const block = missionBlock(mission)
    if (block) blocks.push(block)
  }
  if (comparison && products.length >= 2) {
    const block = createMiniCompareBlock(products, deliveryLocations)
    if (block) blocks.push(block)
  } else if (products.length > 0) {
    if (toolName === 'find_similar_products') {
      blocks.push({ type: 'similar', products })
    } else if (toolName === 'pick_recommended_product' && products[0]) {
      blocks.push({ type: 'decision', product: products[0], runnerUp: products[1] ?? null })
    } else if (messageArtifacts.some((artifact) => artifact.type === 'SAVED_PRODUCT')) {
      blocks.push({ type: 'saved', products })
    } else {
      blocks.push({ type: 'products', products })
    }
  }

  for (const artifact of messageArtifacts) {
    const product = productByCanonicalKey(allProducts, artifact.canonicalProductKey)
    if (artifact.type === 'REVIEWS' && product) {
      blocks.push({ type: 'reviews', product, snapshot: productReviewsSnapshot(artifact) })
    }
    if (artifact.type === 'DISCOUNT_CODES' && product) {
      const payload = parseRecord(artifact.payloadJson)
      const codes = discountCodes(artifact)
      blocks.push({
        type: 'code',
        product,
        codes,
        cached: payload?.cached === true,
        searchedAt: stringValue(payload?.searchedAt),
        expiresAt: stringValue(payload?.expiresAt),
        status: codes.length > 0 ? 'found' : 'empty',
      })
    }
  }

  const cartArtifacts = latestCartSnapshotArtifacts(
    messageArtifacts.filter(
      (artifact) => artifact.type === 'CART' || artifact.type === 'CART_LINE',
    ),
  )
  const lines = cartItemsFromAgentArtifacts(cartArtifacts, allProducts)
  if (cartArtifacts.length > 0) {
    blocks.push({ type: 'cart', lines, products: allProducts })
  }
  const checkoutCount = messageArtifacts.filter((artifact) => artifact.type === 'CHECKOUT').length
  if (checkoutCount > 0) {
    const cartIds = new Set(
      messageArtifacts.flatMap((artifact) => (artifact.cartId ? [artifact.cartId] : [])),
    )
    const historicalCartArtifacts = latestCartSnapshotArtifacts(
      allArtifacts.filter((artifact) => artifact.cartId && cartIds.has(artifact.cartId)),
      messageArtifacts
        .filter((artifact) => artifact.type === 'CHECKOUT')
        .map((artifact) => artifact.createdAt)
        .sort(compareArtifactTimestamps)
        .at(-1),
    )
    const historicalCartCount = historicalCartArtifacts.filter(
      (artifact) => artifact.type === 'CART',
    ).length
    blocks.push({
      type: 'checkout',
      merchantCount: historicalCartCount || checkoutCount,
      lines: cartItemsFromAgentArtifacts(historicalCartArtifacts, allProducts),
      products: allProducts,
    })
  }
  return blocks
}

/** Converts only authoritative ledger rows; transient stream projection is appended by the view. */
export function discoverMessagesFromAgentConversation(
  conversation: AgentConversationDetailProfile,
  deliveryLocations: readonly UserLocation[],
): DiscoverChatMessage[] {
  const artifactsByMessage = new Map<string, AgentArtifactProfile[]>()
  for (const artifact of conversation.artifacts) {
    const existing = artifactsByMessage.get(artifact.messageId) ?? []
    existing.push(artifact)
    artifactsByMessage.set(artifact.messageId, existing)
  }
  const artifactHostMessageByRun = new Map<string, string>()
  const userQueryByRun = new Map<string, string>()
  for (const message of conversation.messages) {
    if (message.role === 'USER' && message.runId && message.textContent) {
      userQueryByRun.set(message.runId, message.textContent)
    }
    if (message.role === 'ASSISTANT' && message.runId) {
      artifactHostMessageByRun.set(message.runId, message.messageId)
    }
  }
  const toolBlocksByRun = new Map<string, DiscoverChatBlock[]>()
  for (const message of conversation.messages) {
    if (message.role !== 'TOOL' || !message.runId) continue
    let blocks = blocksForAgentMessage(
      message,
      artifactsByMessage.get(message.messageId) ?? [],
      conversation.artifacts,
      deliveryLocations,
    )
    if (message.correlationId?.split(':').at(-1) === 'search_catalog') {
      const query = userQueryByRun.get(message.runId)
      if (query) {
        blocks = blocks.map((block) => (block.type === 'products' ? { ...block, query } : block))
      }
    }
    if (blocks.length === 0) continue
    const existing = toolBlocksByRun.get(message.runId) ?? []
    existing.push(...blocks)
    toolBlocksByRun.set(message.runId, existing)
  }
  const messages: DiscoverChatMessage[] = []
  for (const message of conversation.messages) {
    if (message.role === 'SYSTEM_SUMMARY') continue
    const artifacts = artifactsByMessage.get(message.messageId) ?? []
    if (message.role === 'USER' || message.role === 'USER_ACTION') {
      let text = message.textContent ?? (message.role === 'USER_ACTION' ? 'Completed action' : '')
      if (
        message.role === 'USER_ACTION' &&
        /^(?:Pinned|Unpinned|Watching|Stopped watching)\b/i.test(text)
      ) {
        const products = productsFromAgentArtifacts(conversation.artifacts)
        for (const artifact of artifacts) {
          const key = artifact.canonicalProductKey
          if (!key || !text.includes(key)) continue
          const product = productByCanonicalKey(products, key)
          const label = product?.name ?? (artifact.label !== key ? artifact.label : null)
          if (label) text = text.replaceAll(key, label)
        }
      }
      messages.push({
        id: message.messageId,
        role: 'you',
        text,
      })
      const blocks = blocksForAgentMessage(
        message,
        artifacts,
        conversation.artifacts,
        deliveryLocations,
      )
      if (blocks.length > 0) {
        messages.push({ id: `${message.messageId}:artifacts`, role: 'ai', blocks })
      }
      continue
    }
    if (message.role === 'ASSISTANT') {
      const toolBlocks =
        message.runId && artifactHostMessageByRun.get(message.runId) === message.messageId
          ? (toolBlocksByRun.get(message.runId) ?? [])
          : []
      const searchProducts = toolBlocks.find(
        (block): block is Extract<DiscoverChatBlock, { type: 'products' }> =>
          block.type === 'products' && Boolean(block.query),
      )
      const text = searchProducts
        ? conciseProductResultIntroduction(
            message.textContent,
            searchProducts.query,
            searchProducts.products,
          )
        : message.textContent
          ? plainAgentText(message.textContent)
          : null
      messages.push({
        id: message.messageId,
        role: 'ai',
        blocks: [...(text ? [{ type: 'text' as const, text }] : []), ...toolBlocks],
      })
      continue
    }
    if (message.role === 'TOOL') {
      if (message.runId && artifactHostMessageByRun.has(message.runId)) continue
      const blocks = blocksForAgentMessage(
        message,
        artifacts,
        conversation.artifacts,
        deliveryLocations,
      )
      const searchQuery = message.runId ? userQueryByRun.get(message.runId) : undefined
      const displayBlocks: DiscoverChatBlock[] =
        message.correlationId?.split(':').at(-1) === 'search_catalog' && searchQuery
          ? blocks.flatMap<DiscoverChatBlock>((block) =>
              block.type === 'products'
                ? [
                    {
                      type: 'text' as const,
                      text: conciseProductResultIntroduction(null, searchQuery, block.products),
                    },
                    { ...block, query: searchQuery },
                  ]
                : [block],
            )
          : blocks
      if (displayBlocks.length > 0) {
        messages.push({ id: message.messageId, role: 'ai', blocks: displayBlocks })
      }
    }
  }
  return messages
}
