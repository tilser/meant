import {
  getCanonicalProductDetail,
  type CanonicalOfferProfile,
  type CanonicalProductDetailProfile,
  type CanonicalProductProfile,
} from '../../../lib/apiClient'
import type { Product } from '../types'

function identifierKey(
  identifier: CanonicalOfferProfile['identity']['externalProductIdentity'] | null | undefined,
): string {
  return identifier ? `${identifier.type}|${identifier.namespace ?? ''}|${identifier.value}` : ''
}

function optionKey(option: { group?: string | null; name: string; value: string }): string {
  return `${option.group ?? ''}|${option.name}|${option.value}`
}

export function canonicalOfferIdentityKey(offer: CanonicalOfferProfile): string {
  const identity = offer.identity
  const merchant = identity.merchantScope.externalMerchantIdentity
    ? identifierKey(identity.merchantScope.externalMerchantIdentity)
    : `local|${identity.merchantScope.merchantIntegrationFallbackId ?? ''}`
  const components = identity.components
    .map((component) => ({
      product: identifierKey(component.externalProductIdentity),
      variant: identifierKey(component.externalVariantIdentity),
      quantity: component.quantity,
      options: component.selectedOptions.map(optionKey).sort(),
    }))
    .sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)))
  const sellingPlan = identity.sellingPlanIdentity
    ? {
        group: identifierKey(identity.sellingPlanIdentity.groupReference),
        plan: identifierKey(identity.sellingPlanIdentity.planReference),
        options: identity.sellingPlanIdentity.options
          .map((option) => `${option.name}|${option.value}`)
          .sort(),
      }
    : null

  return JSON.stringify({
    provider: identity.provider,
    merchant,
    product: identifierKey(identity.externalProductIdentity),
    variant: identifierKey(identity.externalVariantIdentity),
    components,
    sellingPlan,
    options: offer.selectedOptions.map(optionKey).sort(),
  })
}

export function findRestoredCanonicalProduct(
  previous: CanonicalProductProfile,
  candidates: readonly CanonicalProductProfile[],
): CanonicalProductProfile | null {
  const sameCanonicalKey = candidates.find((candidate) => candidate.key === previous.key)
  if (sameCanonicalKey) {
    return sameCanonicalKey
  }

  const previousOfferIdentities = new Set(previous.offers.map(canonicalOfferIdentityKey))
  return (
    candidates.find((candidate) =>
      candidate.offers.some((offer) =>
        previousOfferIdentities.has(canonicalOfferIdentityKey(offer)),
      ),
    ) ?? null
  )
}

export function canonicalProductRecoveryQuery(
  product: Product,
  historicalQuery: string | null | undefined,
): string {
  return historicalQuery?.trim() || product.name.trim() || 'product'
}

interface RecoveryDependencies {
  loadDetail: typeof getCanonicalProductDetail
}

const DEFAULT_RECOVERY_DEPENDENCIES: RecoveryDependencies = {
  loadDetail: getCanonicalProductDetail,
}

export async function loadCanonicalProductDetailWithRecovery(
  input: {
    product: Product
    historicalQuery?: string | null
    selectedOfferKey?: string | null
    signal?: AbortSignal
    expectedUserId?: string
  },
  dependencies: RecoveryDependencies = DEFAULT_RECOVERY_DEPENDENCIES,
): Promise<CanonicalProductDetailProfile> {
  const previous = input.product.canonicalProduct
  if (!previous) {
    throw new Error('Canonical product detail recovery requires a canonical product')
  }

  return dependencies.loadDetail({
    canonicalProductKey: previous.key,
    selectedOfferKey: input.selectedOfferKey,
    signal: input.signal,
    expectedUserId: input.expectedUserId,
  })
}
