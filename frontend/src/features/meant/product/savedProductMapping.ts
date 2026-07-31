import type {
  CanonicalOfferProfile,
  CatalogProductReferenceInput,
  MerchantProductDetailsProfile,
  SaveUserProductInput,
  UserSavedProductDetailsProfile,
  UserSavedProductProfile,
} from '../../../lib/apiClient'
import { merchantAdjacentDisplayLabel, merchantDisplayOrigin } from '../cart/merchantOrigin'
import type {
  Preference,
  Product,
  ProductCatalogAttribute,
  ProductCatalogCategory,
  ProductMedia,
  ProductOption,
  ProductSelectedOption,
} from '../types'
import { displayProductCategoryValue, minorUnitsToMajor } from '../utils'
import { productCuratedFields, productWithCuratedFields } from './productCuration'

function catalogReferenceForRecommendedOffer(
  product: Product,
): CatalogProductReferenceInput | undefined {
  const canonical = product.canonicalProduct
  if (!canonical) return undefined
  const offer = canonical.offers.find(
    (candidate) => candidate.key === canonical.recommendedOfferKey,
  )
  if (!offer) return undefined

  // Offer provenance is server-ranked. Select the first complete record that matches this exact
  // offer identity, and keep every reference field on that one record to avoid mixed authority.
  const provenance = offer.provenance.find((candidate) => provenanceMatchesOffer(candidate, offer))
  if (!provenance) return undefined

  const provider = provenance.provider.trim()
  const sourceIdentity = provenance.discoverySource.value.trim()
  const externalProductId = provenance.externalProductReference.value.trim()
  const merchantIntegrationId = provenance.localRouting?.merchantIntegrationId.trim()
  const externalMerchantId = provenance.externalMerchantReference?.value.trim()
  const externalVariantId = provenance.externalVariantReference?.value.trim()
  const components = offer.identity.components.map((component) => ({
    externalProductId: component.externalProductIdentity.value.trim(),
    ...(component.externalVariantIdentity?.value.trim()
      ? { externalVariantId: component.externalVariantIdentity.value.trim() }
      : {}),
    quantity: component.quantity,
    selectedOptions: component.selectedOptions.map((option) => {
      const group = option.group?.trim()
      return {
        ...(group ? { group } : {}),
        name: option.name,
        value: option.value,
      }
    }),
  }))
  const sellingPlan = offer.identity.sellingPlanIdentity
  return {
    provider,
    sourceType: provenance.discoverySource.type,
    sourceIdentity,
    ...(merchantIntegrationId ? { merchantIntegrationId } : {}),
    ...(externalMerchantId ? { externalMerchantId } : {}),
    externalProductId,
    ...(externalVariantId ? { externalVariantId } : {}),
    selectedOptions: offer.selectedOptions.map((option) => {
      const group = option.group?.trim()
      return {
        ...(group ? { group } : {}),
        name: option.name,
        value: option.value,
      }
    }),
    offerKey: offer.key,
    ...(components.length > 0 ? { components } : {}),
    ...(sellingPlan
      ? {
          sellingPlan: {
            ...(sellingPlan.groupReference?.value.trim()
              ? { groupId: sellingPlan.groupReference.value.trim() }
              : {}),
            ...(sellingPlan.planReference?.value.trim()
              ? { planId: sellingPlan.planReference.value.trim() }
              : {}),
            options: sellingPlan.options.map((option) => ({
              name: option.name,
              value: option.value,
            })),
          },
        }
      : {}),
  }
}

function provenanceMatchesOffer(
  provenance: CanonicalOfferProfile['provenance'][number],
  offer: CanonicalOfferProfile,
): boolean {
  const provider = provenance.provider.trim()
  const sourceProvider = provenance.discoverySource.provider.trim()
  const sourceIdentity = provenance.discoverySource.value.trim()
  const externalProductId = provenance.externalProductReference.value.trim()
  if (
    !provider ||
    provider !== sourceProvider ||
    provider !== offer.identity.provider.trim() ||
    !sourceIdentity ||
    !externalProductId ||
    provenance.externalProductReference.type !== 'PRODUCT' ||
    offer.identity.externalProductIdentity.type !== 'PRODUCT' ||
    !offer.identity.externalProductIdentity.value.trim() ||
    !offer.key.trim() ||
    offer.selectedOptions.some((option) => !option.name.trim() || !option.value.trim()) ||
    !configurationIsValid(offer, provider)
  ) {
    return false
  }

  const offerVariantId = offer.identity.externalVariantIdentity?.value.trim()
  const provenanceVariantId = provenance.externalVariantReference?.value.trim()
  if (
    (offer.identity.externalVariantIdentity &&
      (offer.identity.externalVariantIdentity.type !== 'VARIANT' || !offerVariantId)) ||
    (provenance.externalVariantReference &&
      (provenance.externalVariantReference.type !== 'VARIANT' || !provenanceVariantId)) ||
    (offerVariantId || undefined) !== (provenanceVariantId || undefined)
  ) {
    return false
  }

  const offerMerchantId = offer.identity.merchantScope.externalMerchantIdentity?.value.trim()
  const provenanceMerchantId = provenance.externalMerchantReference?.value.trim()
  if (
    (offer.identity.merchantScope.externalMerchantIdentity &&
      (offer.identity.merchantScope.externalMerchantIdentity.type !== 'MERCHANT' ||
        !offerMerchantId)) ||
    (provenance.externalMerchantReference &&
      (provenance.externalMerchantReference.type !== 'MERCHANT' || !provenanceMerchantId)) ||
    (offerMerchantId || undefined) !== (provenanceMerchantId || undefined)
  ) {
    return false
  }

  const fallbackIntegrationId = offer.identity.merchantScope.merchantIntegrationFallbackId?.trim()
  const routingIntegrationId = provenance.localRouting?.merchantIntegrationId.trim()
  if (offer.identity.merchantScope.type === 'LOCAL_MERCHANT_INTEGRATION_FALLBACK') {
    return Boolean(
      !offerMerchantId &&
      !provenanceMerchantId &&
      fallbackIntegrationId &&
      fallbackIntegrationId === routingIntegrationId,
    )
  }
  return (
    offer.identity.merchantScope.type === 'EXTERNAL_MERCHANT' &&
    !fallbackIntegrationId &&
    Boolean(offerMerchantId)
  )
}

function configurationIsValid(offer: CanonicalOfferProfile, provider: string): boolean {
  const componentsValid = offer.identity.components.every((component) => {
    const product = component.externalProductIdentity
    const variant = component.externalVariantIdentity
    return (
      product.type === 'PRODUCT' &&
      Boolean(product.value.trim()) &&
      (!product.namespace || product.namespace.trim() === provider) &&
      (!variant ||
        (variant.type === 'VARIANT' &&
          Boolean(variant.value.trim()) &&
          (!variant.namespace || variant.namespace.trim() === provider))) &&
      Number.isSafeInteger(component.quantity) &&
      component.quantity > 0 &&
      component.selectedOptions.every(
        (option) => Boolean(option.name.trim()) && Boolean(option.value.trim()),
      )
    )
  })
  if (!componentsValid) return false

  const plan = offer.identity.sellingPlanIdentity
  if (!plan) return true
  const group = plan.groupReference
  const selected = plan.planReference
  return (
    Boolean(group || selected) &&
    (!group ||
      (group.type === 'SELLING_PLAN_GROUP' &&
        Boolean(group.value.trim()) &&
        (!group.namespace || group.namespace.trim() === provider))) &&
    (!selected ||
      (selected.type === 'SELLING_PLAN' &&
        Boolean(selected.value.trim()) &&
        (!selected.namespace || selected.namespace.trim() === provider))) &&
    plan.options.every((option) => Boolean(option.name.trim()) && Boolean(option.value.trim()))
  )
}

function cleanDetailStrings(values: readonly (string | null | undefined)[]): string[] {
  return values.map((value) => value?.trim()).filter((value): value is string => Boolean(value))
}

function savedDetailMedia(details?: UserSavedProductDetailsProfile | null): ProductMedia[] {
  if (!details) return []
  const media = details.media.flatMap((item) => {
    const url = item.url?.trim()
    return url
      ? [{ type: item.type?.trim() || 'image', url, altText: item.altText?.trim() || null }]
      : []
  })
  const images = details.images.flatMap((item) => {
    const url = item.url?.trim()
    return url ? [{ type: 'image', url, altText: item.altText?.trim() || null }] : []
  })
  const seen = new Set<string>()
  return [...media, ...images].filter((item) => {
    const key = `${item.type.toLowerCase()}|${item.url}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function savedDetailCategories(
  details?: UserSavedProductDetailsProfile | null,
): ProductCatalogCategory[] {
  if (!details) return []
  return details.categories.flatMap((category) => {
    const value = category.value?.trim()
    return value ? [{ value, taxonomy: category.taxonomy?.trim() || null }] : []
  })
}

function savedDetailAttributes(
  details?: UserSavedProductDetailsProfile | null,
): ProductCatalogAttribute[] {
  if (!details) return []
  return details.attributes.flatMap((attribute) => {
    const name = attribute.name?.trim()
    const value = attribute.value?.trim()
    return name && value ? [{ name, value }] : []
  })
}

function savedDetailOptions(details?: UserSavedProductDetailsProfile | null): ProductOption[] {
  if (!details) return []
  return details.options.flatMap((option) => {
    const name = option.name?.trim()
    const values = cleanDetailStrings(option.values ?? [])
    return name && values.length > 0 ? [{ name, values }] : []
  })
}

function savedDetailSelectedOptions(
  details?: UserSavedProductDetailsProfile | null,
): ProductSelectedOption[] {
  if (!details) return []
  return details.selectedOptions.flatMap((option) => {
    const name = option.name?.trim()
    const value = option.value?.trim()
    return name && value ? [{ name, value }] : []
  })
}

function savedMerchantDetails(
  details?: UserSavedProductDetailsProfile | null,
): MerchantProductDetailsProfile | null {
  return details ?? null
}

function normalizedSavedDetailRating(
  details?: UserSavedProductDetailsProfile | null,
): number | null {
  const score = details?.ratingScore
  if (typeof score !== 'number' || !Number.isFinite(score) || score < 0) return null

  const scaleMax = details?.ratingScaleMax
  if (typeof scaleMax === 'number' && Number.isFinite(scaleMax) && scaleMax > 0) {
    return Math.min(5, (score / scaleMax) * 5)
  }
  return score <= 5 ? score : null
}

function savedDetailReviewCount(details?: UserSavedProductDetailsProfile | null): number | null {
  const count = details?.reviewCount
  return typeof count === 'number' && Number.isSafeInteger(count) && count >= 0 ? count : null
}

const TECHNICAL_MERCHANT_LABELS = new Set([
  'CACHED_OBSERVATION',
  'DATASET_IMPORT',
  'GENERIC_UCP',
  'GLOBAL_CATALOG',
  'LOCAL_STOREFRONT',
  'MANUAL_ASSERTION',
  'MEANT_MERCHANT_SEMANTIC',
  'MERCHANT_INTEGRATION',
  'MERCHANT_STOREFRONT',
  'PROVIDER_CATALOG',
  'SHOPIFY_GLOBAL_CATALOG',
  'USER_PRODUCT_SEARCH_CACHE',
])

const INTERNAL_MERCHANT_REFERENCE = /^(?:LOCAL_STOREFRONT|MERCHANT_INTEGRATION)\s*:/i
const UUID_REFERENCE = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i
const GID_REFERENCE = /(?:^|:)gid:\/\/[^\s]+$/i

function nonTechnicalMerchantLabel(value: string | null | undefined): string | null {
  const label = value?.trim()
  if (
    !label ||
    INTERNAL_MERCHANT_REFERENCE.test(label) ||
    TECHNICAL_MERCHANT_LABELS.has(label.toUpperCase()) ||
    UUID_REFERENCE.test(label) ||
    GID_REFERENCE.test(label)
  ) {
    return null
  }
  return label
}

function savedOfferMerchantLabel(
  details: UserSavedProductDetailsProfile | null | undefined,
  offer: UserSavedProductProfile['offers'][number],
): string {
  const merchantOrigin = offer.merchantOrigin?.trim() || details?.merchantOrigin?.trim() || null
  if (merchantOrigin) {
    return merchantDisplayOrigin(merchantOrigin)
  }
  return merchantAdjacentDisplayLabel(
    nonTechnicalMerchantLabel(details?.merchantName) ?? nonTechnicalMerchantLabel(offer.merchant),
  )
}

export function savedProductFromProfile(
  product: UserSavedProductProfile,
  preferences: readonly Preference[] = [],
): Product {
  const priceFrom = minorUnitsToMajor(product.priceFromMinorUnits, product.priceCurrency)
  const authoritative = product.commercialFactsAuthoritative
  const details = product.details
  const currentOffer = authoritative
    ? (product.offers.find((offer) => Boolean(offer.offerKey?.trim())) ?? product.offers[0])
    : undefined
  const currentMerchantId = currentOffer?.merchantId?.trim() || null
  const currentMerchantOrigin =
    currentOffer?.merchantOrigin?.trim() || details?.merchantOrigin?.trim() || null
  const currentMerchantProductId = currentMerchantId ? details?.productId?.trim() || null : null
  const storedReview = product.review
    ? {
        score: product.review.score,
        count: product.review.count ?? 0,
        insight: product.review.insight ?? 'Current review facts are unavailable.',
      }
    : { score: null, count: 0, insight: 'Current review facts are unavailable.' }
  const currentRating = normalizedSavedDetailRating(details)
  const currentReviewCount = savedDetailReviewCount(details)
  const media = savedDetailMedia(details)
  const detailImageUrl =
    details?.selectedVariantImageUrl?.trim() ||
    details?.imageUrl?.trim() ||
    media.find((item) => item.type.toLowerCase() === 'image')?.url ||
    null
  const snapshot: Product = {
    id: product.id,
    productHash: product.productHash,
    merchantId: currentMerchantId,
    merchantDomain: currentMerchantOrigin,
    merchantProductId: currentMerchantProductId,
    name: product.name ?? details?.title ?? 'Saved product unavailable',
    brand: merchantAdjacentDisplayLabel(product.brand ?? 'Unavailable'),
    category: displayProductCategoryValue(product.category) ?? 'Product',
    tone: product.tone ?? '#e7ebef',
    imageUrl: product.imageUrl ?? detailImageUrl,
    productUrl: details?.url?.trim() || product.productUrl,
    remote: Boolean(currentMerchantId && currentMerchantProductId) || (product.remote ?? false),
    match: product.match ?? 0,
    priceFrom: authoritative ? priceFrom : null,
    priceFromMinorUnits: authoritative && priceFrom != null ? product.priceFromMinorUnits : null,
    priceCurrency: authoritative && priceFrom != null ? product.priceCurrency : null,
    merchants: product.merchants ?? 0,
    satisfies: product.satisfies,
    misses: product.misses,
    note: product.note ?? 'Current product details are unavailable.',
    pros: product.pros,
    cons: product.cons,
    review: {
      score: currentRating ?? storedReview.score,
      count: currentReviewCount ?? storedReview.count,
      insight: storedReview.insight,
    },
    media,
    catalogCategories: savedDetailCategories(details),
    certifications: cleanDetailStrings(details?.certifications ?? []),
    materials: cleanDetailStrings(details?.materials ?? []),
    skus: cleanDetailStrings(details?.skus ?? []),
    collections: cleanDetailStrings(details?.collections ?? []),
    catalogAttributes: savedDetailAttributes(details),
    detailDescription: details?.description?.trim() || null,
    detailOptions: savedDetailOptions(details),
    selectedOptions: savedDetailSelectedOptions(details),
    totalVariants: details?.totalVariants ?? null,
    selectedVariantAvailable: details?.selectedVariantAvailable ?? null,
    offers: authoritative
      ? product.offers
          .filter((offer) => {
            const hasExactServerKey = Boolean(offer.offerKey?.trim())
            const hasDisplayPrice =
              minorUnitsToMajor(offer.priceMinorUnits, offer.priceCurrency) != null
            return hasExactServerKey || hasDisplayPrice
          })
          .map((offer) => {
            const price = minorUnitsToMajor(offer.priceMinorUnits, offer.priceCurrency)
            return {
              ...offer,
              merchant: savedOfferMerchantLabel(details, offer),
              merchantDomain:
                offer.merchantOrigin?.trim() || details?.merchantOrigin?.trim() || null,
              price: price ?? Number.NaN,
              priceMinorUnits: price == null ? null : offer.priceMinorUnits,
              priceCurrency: price == null ? null : offer.priceCurrency,
              delivery: offer.delivery ?? 'Calculated at checkout',
            }
          })
      : [],
    needs: product.needs ? (product.needs as Product['needs']) : undefined,
    provides:
      (product.provides?.length ?? 0) > 0 ? (product.provides as Product['provides']) : undefined,
    commercialFactsAuthoritative: authoritative,
    rehydratedDetails: savedMerchantDetails(details),
  }
  return productWithCuratedFields(snapshot, preferences)
}

export function savedProductInput(
  product: Product,
  preferences: readonly Preference[] = [],
  selectedOfferKey?: string | null,
): SaveUserProductInput {
  const curatedFields = productCuratedFields(product, preferences)
  const catalogReference = catalogReferenceForRecommendedOffer(product)
  return {
    id: product.canonicalProduct?.key ?? product.id,
    productHash: product.productHash ?? null,
    name: product.name,
    brand: product.brand,
    category: product.category,
    tone: product.tone,
    imageUrl: product.imageUrl ?? null,
    productUrl: product.productUrl ?? null,
    remote: product.remote ?? false,
    match: product.match,
    priceFrom: product.priceFrom,
    merchants: product.merchants,
    satisfies: [...product.satisfies],
    misses: [...product.misses],
    note: curatedFields.note,
    pros: [...curatedFields.pros],
    cons: [...curatedFields.cons],
    review: {
      score: product.review.score ?? 0,
      count: product.review.count,
      insight: product.review.insight,
    },
    offers: product.offers.map((offer) => ({
      merchant: offer.merchant,
      price: offer.price,
      delivery: offer.delivery,
      merchantId: offer.merchantId ?? null,
      merchantDomain: null,
      productVariantId: offer.productVariantId ?? null,
      variantTitle: offer.variantTitle ?? null,
      available: offer.available ?? null,
    })),
    needs: product.needs ?? null,
    provides: product.provides ? [...product.provides] : [],
    ...(catalogReference ? { catalogReference } : {}),
    ...(selectedOfferKey?.trim() ? { selectedOfferKey: selectedOfferKey.trim() } : {}),
  }
}
