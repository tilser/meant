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
import type { CartItem, Product, UserLocation } from '../types'
import { minorUnitsToMajor } from '../utils'

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

/** Maps the durable PRODUCT artifact, tolerating detail tools that wrap it in `{ product }`. */
export function productFromAgentArtifact(artifact: AgentArtifactProfile): Product | null {
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
    return null
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

export function productsFromAgentArtifacts(artifacts: readonly AgentArtifactProfile[]): Product[] {
  const byId = new Map<string, Product>()
  for (const artifact of artifacts) {
    const product = productFromAgentArtifact(artifact)
    if (product) {
      byId.set(product.id, product)
    }
  }
  return [...byId.values()]
}

export function latestAgentArtifacts(
  artifacts: readonly AgentArtifactProfile[],
): AgentArtifactProfile[] {
  const latest = new Map<string, AgentArtifactProfile>()
  for (const artifact of artifacts) {
    const current = latest.get(artifact.stableKey)
    if (!current || current.createdAt <= artifact.createdAt) {
      latest.set(artifact.stableKey, artifact)
    }
  }
  return [...latest.values()].sort(
    (left, right) => left.createdAt.localeCompare(right.createdAt) || left.ordinal - right.ordinal,
  )
}

/** Chooses complete cart snapshots (CART plus its sibling CART_LINE rows), not stale lines. */
export function latestCartSnapshotArtifacts(
  artifacts: readonly AgentArtifactProfile[],
  beforeOrAt?: string,
): AgentArtifactProfile[] {
  const selectedCarts = new Map<string, AgentArtifactProfile>()
  for (const artifact of artifacts) {
    if (
      artifact.type !== 'CART' ||
      !artifact.cartId ||
      (beforeOrAt && artifact.createdAt > beforeOrAt)
    ) {
      continue
    }
    const current = selectedCarts.get(artifact.cartId)
    if (!current || current.createdAt <= artifact.createdAt) {
      selectedCarts.set(artifact.cartId, artifact)
    }
  }
  const selectedMessageIds = new Set(
    [...selectedCarts.values()].map((artifact) => artifact.messageId),
  )
  return artifacts.filter(
    (artifact) =>
      (artifact.type === 'CART' &&
        Boolean(
          artifact.cartId && selectedCarts.get(artifact.cartId)?.artifactId === artifact.artifactId,
        )) ||
      (artifact.type === 'CART_LINE' && selectedMessageIds.has(artifact.messageId)),
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
    if (value && (!current || current.createdAt <= artifact.createdAt)) {
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
}

function parsedCartArtifact(artifact: AgentArtifactProfile): ParsedCartArtifact | null {
  if (artifact.type !== 'CART' || !artifact.cartId) return null
  const value = parseRecord(artifact.payloadJson)
  return {
    cartId: artifact.cartId,
    merchant:
      stringValue(value?.merchantDomain) ??
      stringValue(value?.provider) ??
      artifact.label ??
      'Merchant',
    merchantId: stringValue(value?.merchantId),
    merchantDomain: stringValue(value?.merchantDomain),
    provider: stringValue(value?.provider),
    merchantIntegrationId: stringValue(value?.merchantIntegrationId),
    externalMerchantId: stringValue(value?.externalMerchantId),
    routingScopeKey: stringValue(value?.routingScopeKey),
    remoteCartId: stringValue(value?.remoteCartId),
    checkoutUrl: stringValue(value?.checkoutUrl),
    continueUrl: stringValue(value?.continueUrl),
    totalAmount: stringValue(value?.totalAmount),
    subtotalAmount: stringValue(value?.subtotalAmount),
    currency: stringValue(value?.currency),
  }
}

/** Reconstructs immutable cart lines from CART/CART_LINE artifacts for inline history rendering. */
export function cartItemsFromAgentArtifacts(
  artifacts: readonly AgentArtifactProfile[],
  products: readonly Product[],
): CartItem[] {
  const carts = new Map<string, ParsedCartArtifact>()
  for (const artifact of artifacts) {
    const parsed = parsedCartArtifact(artifact)
    if (parsed) carts.set(parsed.cartId, parsed)
  }
  return artifacts.flatMap((artifact) => {
    if (artifact.type !== 'CART_LINE' || !artifact.cartId || !artifact.cartLineId) return []
    const line = parseRecord(artifact.payloadJson)
    const offerKey = stringValue(line?.offerKey) ?? artifact.offerKey
    const product = productForOffer(products, offerKey)
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
        productTitle: stringValue(line?.productTitle) ?? product.name,
        variantTitle: stringValue(line?.variantTitle),
        unitPriceAmount: stringValue(line?.subtotalAmount),
        lineTotalAmount: stringValue(line?.totalAmount),
        orderCurrency: stringValue(line?.currency),
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
  const products = productsFromAgentArtifacts(messageArtifacts)
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

  const cartArtifacts = messageArtifacts.filter(
    (artifact) => artifact.type === 'CART' || artifact.type === 'CART_LINE',
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
        .sort()
        .at(-1),
    )
    blocks.push({
      type: 'checkout',
      merchantCount: checkoutCount,
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
  const messages: DiscoverChatMessage[] = []
  for (const message of conversation.messages) {
    if (message.role === 'SYSTEM_SUMMARY') continue
    const artifacts = artifactsByMessage.get(message.messageId) ?? []
    if (message.role === 'USER' || message.role === 'USER_ACTION') {
      messages.push({
        id: message.messageId,
        role: 'you',
        text: message.textContent ?? (message.role === 'USER_ACTION' ? 'Completed action' : ''),
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
      messages.push({
        id: message.messageId,
        role: 'ai',
        blocks: message.textContent ? [{ type: 'text', text: message.textContent }] : [],
      })
      continue
    }
    if (message.role === 'TOOL') {
      const blocks = blocksForAgentMessage(
        message,
        artifacts,
        conversation.artifacts,
        deliveryLocations,
      )
      if (blocks.length > 0) messages.push({ id: message.messageId, role: 'ai', blocks })
    }
  }
  return messages
}
