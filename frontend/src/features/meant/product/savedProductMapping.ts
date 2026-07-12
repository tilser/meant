import type {
  CanonicalOfferProfile,
  CatalogProductReferenceInput,
  SaveUserProductInput,
  UserSavedProductProfile,
} from '../../../lib/apiClient'
import type { Preference, Product } from '../types'
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
  return {
    provider,
    sourceType: provenance.discoverySource.type,
    sourceIdentity,
    ...(merchantIntegrationId ? { merchantIntegrationId } : {}),
    ...(externalMerchantId ? { externalMerchantId } : {}),
    externalProductId,
    ...(externalVariantId ? { externalVariantId } : {}),
    selectedOptions: offer.selectedOptions.map((option) => ({
      name: option.name,
      value: option.value,
    })),
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
    offer.selectedOptions.some((option) => !option.name.trim() || !option.value.trim())
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

export function savedProductFromProfile(
  product: UserSavedProductProfile,
  preferences: readonly Preference[] = [],
): Product {
  const priceFrom = minorUnitsToMajor(product.priceFromMinorUnits, product.priceCurrency)
  const authoritative = product.commercialFactsAuthoritative && priceFrom != null
  const snapshot: Product = {
    id: product.id,
    productHash: product.productHash,
    name: product.name ?? 'Saved product unavailable',
    brand: product.brand ?? 'Unavailable',
    category: displayProductCategoryValue(product.category) ?? 'Product',
    tone: product.tone ?? '#e7ebef',
    imageUrl: product.imageUrl,
    productUrl: product.productUrl,
    remote: product.remote ?? false,
    match: product.match ?? 0,
    priceFrom: authoritative ? priceFrom : null,
    priceFromMinorUnits: authoritative ? product.priceFromMinorUnits : null,
    priceCurrency: authoritative ? product.priceCurrency : null,
    merchants: product.merchants ?? 0,
    satisfies: product.satisfies,
    misses: product.misses,
    note: product.note ?? 'Current product details are unavailable.',
    pros: product.pros,
    cons: product.cons,
    review: product.review
      ? {
          score: product.review.score,
          count: product.review.count ?? 0,
          insight: product.review.insight ?? 'Current review facts are unavailable.',
        }
      : { score: null, count: 0, insight: 'Current review facts are unavailable.' },
    offers: authoritative
      ? product.offers
          .filter(
            (
              offer,
            ): offer is typeof offer & {
              merchant: string
              priceMinorUnits: number
              priceCurrency: string
            } =>
              offer.merchant != null &&
              minorUnitsToMajor(offer.priceMinorUnits, offer.priceCurrency) != null,
          )
          .map((offer) => ({
            ...offer,
            merchant: offer.merchant,
            price: minorUnitsToMajor(offer.priceMinorUnits, offer.priceCurrency) as number,
            priceMinorUnits: offer.priceMinorUnits,
            priceCurrency: offer.priceCurrency,
            delivery: offer.delivery ?? 'Calculated at checkout',
          }))
      : [],
    needs: product.needs ? (product.needs as Product['needs']) : undefined,
    provides:
      (product.provides?.length ?? 0) > 0 ? (product.provides as Product['provides']) : undefined,
    commercialFactsAuthoritative: authoritative,
  }
  return productWithCuratedFields(snapshot, preferences)
}

export function savedProductInput(
  product: Product,
  preferences: readonly Preference[] = [],
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
      merchantDomain: offer.merchantDomain ?? null,
      productVariantId: offer.productVariantId ?? null,
      variantTitle: offer.variantTitle ?? null,
      available: offer.available ?? null,
    })),
    needs: product.needs ?? null,
    provides: product.provides ? [...product.provides] : [],
    ...(catalogReference ? { catalogReference } : {}),
  }
}
