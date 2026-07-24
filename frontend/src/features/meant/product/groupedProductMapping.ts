import type { CanonicalProductProfile } from '../../../lib/apiClient'
import { merchantAdjacentDisplayLabel, merchantDisplayOrigin } from '../cart/merchantOrigin'
import type { Product, ProductMedia } from '../types'
import { minorUnitsToMajor } from '../utils'

function firstAttribute(product: CanonicalProductProfile, name: string): string | null {
  return (
    product.attributes.find((attribute) => attribute.name.trim().toLowerCase() === name)?.value ??
    null
  )
}

function uniqueMerchantCount(product: CanonicalProductProfile): number {
  return new Set(
    product.offers.map(
      (offer) =>
        offer.identity.merchantScope.externalMerchantIdentity?.value ??
        offer.identity.merchantScope.merchantIntegrationFallbackId ??
        offer.merchantName ??
        offer.key,
    ),
  ).size
}

function productTone(key: string): string {
  const tones = ['#e7ebef', '#eaede6', '#eceae7', '#eee9ed', '#e9ede8']
  const code = Array.from(key).reduce((sum, character) => sum + character.charCodeAt(0), 0)
  return tones[code % tones.length] ?? tones[0]
}

const SEARCH_RELEVANCE_TAKE =
  'This looks relevant to your search based on the available product details.'

function groupedCardPriceOffer(product: CanonicalProductProfile) {
  const recommended = product.offers.find((offer) => offer.key === product.recommendedOfferKey)
  const displayCurrency =
    recommended?.price?.currency ?? product.offers.find((offer) => offer.price)?.price?.currency
  if (!displayCurrency) return null

  return product.offers.reduce<(typeof product.offers)[number] | null>((current, offer) => {
    if (!offer.price || offer.price.currency !== displayCurrency) return current
    return !current || offer.price.minorUnits < (current.price?.minorUnits ?? Infinity)
      ? offer
      : current
  }, null)
}

function canonicalProductOptions(product: CanonicalProductProfile) {
  const valuesByName = new Map<string, { name: string; values: Set<string> }>()
  product.offers.forEach((offer) => {
    offer.selectedOptions.forEach((option) => {
      const name = option.name.trim()
      const value = option.value.trim()
      if (!name || !value) return
      const key = name.toLowerCase()
      const existing = valuesByName.get(key) ?? { name, values: new Set<string>() }
      existing.values.add(value)
      valuesByName.set(key, existing)
    })
  })
  return Array.from(valuesByName.values()).map((option) => ({
    name: option.name,
    values: Array.from(option.values).sort((left, right) => left.localeCompare(right)),
  }))
}

export function productFromCanonical(product: CanonicalProductProfile): Product {
  const personalization = product.personalization
  const recommended =
    product.offers.find((offer) => offer.key === product.recommendedOfferKey) ?? product.offers[0]
  const displayedPriceOffer = groupedCardPriceOffer(product)
  const displayedPrice = displayedPriceOffer?.price
  const price = displayedPrice
    ? minorUnitsToMajor(displayedPrice.minorUnits, displayedPrice.currency)
    : null
  const media: ProductMedia[] = product.media
    .filter((item) => Boolean(item.url))
    .map((item) => ({ type: item.type || 'image', url: item.url, altText: item.altText }))
  const merchantName = recommended?.merchantOrigin
    ? merchantDisplayOrigin(recommended.merchantOrigin)
    : merchantAdjacentDisplayLabel(recommended?.merchantName)
  const rankingUnavailable = !product.rankingExplanation
  const match = Math.round((product.rankingExplanation?.scoreBasisPoints ?? 0) / 100)
  const detailOptions = canonicalProductOptions(product)
  const selectedOptions = recommended?.selectedOptions.map((option) => ({
    name: option.name,
    value: option.value,
  }))
  const selectedVariantAvailable = recommended
    ? recommended.availability.status === 'OUT_OF_STOCK' ||
      recommended.availability.status === 'DISCONTINUED'
      ? false
      : recommended.availability.status === 'UNKNOWN'
        ? null
        : true
    : null

  return {
    id: product.key,
    merchantDomain: recommended?.merchantOrigin ?? null,
    name: product.title?.trim() || 'Untitled product',
    brand: merchantAdjacentDisplayLabel(
      firstAttribute(product, 'brand') || merchantName || 'Multiple merchants',
    ),
    category: firstAttribute(product, 'category') || 'Product',
    tone: productTone(product.key),
    imageUrl: media.find((item) => item.type.toLowerCase() === 'image')?.url ?? null,
    remote: true,
    match: Math.max(0, Math.min(100, match)),
    rankingUnavailable,
    priceFrom: price,
    priceFromMinorUnits: displayedPrice?.minorUnits ?? null,
    priceCurrency: displayedPrice?.currency ?? null,
    listPrice: null,
    merchants: uniqueMerchantCount(product),
    satisfies: personalization?.matchedFilterIds ?? [],
    misses: personalization?.missedFilterIds ?? [],
    note: personalization?.whyMeantForYou?.trim() || SEARCH_RELEVANCE_TAKE,
    pros: [],
    cons: [],
    review: { score: null, count: 0, insight: 'Review data varies by merchant.' },
    media,
    materials: product.materials.map((material) => material.name),
    certifications: product.certifications.map((certification) => certification.name),
    catalogAttributes: product.attributes,
    detailDescription: product.description ?? null,
    detailOptions,
    selectedOptions,
    totalVariants: product.offers.length,
    selectedVariantAvailable,
    // Existing cards require their established view-model offer shape. Exact selection always
    // reads canonicalProduct.offers and never uses these display-only projections for cart input.
    offers: product.offers.map((offer) => ({
      merchant: offer.merchantOrigin
        ? merchantDisplayOrigin(offer.merchantOrigin)
        : merchantAdjacentDisplayLabel(offer.merchantName),
      price: offer.price
        ? (minorUnitsToMajor(offer.price.minorUnits, offer.price.currency) ?? Number.NaN)
        : Number.NaN,
      priceMinorUnits: offer.price?.minorUnits ?? null,
      priceCurrency: offer.price?.currency ?? null,
      merchantDomain: offer.merchantOrigin ?? null,
      delivery:
        offer.delivery.length > 0
          ? 'Delivery estimate available'
          : 'Delivery calculated by merchant',
      available:
        offer.availability.status === 'OUT_OF_STOCK' || offer.availability.status === 'DISCONTINUED'
          ? false
          : offer.availability.status === 'UNKNOWN'
            ? null
            : true,
    })),
    commercialFactsAuthoritative:
      displayedPriceOffer?.commercialState.authority === 'REHYDRATED_CURRENT',
    canonicalProduct: product,
  }
}
