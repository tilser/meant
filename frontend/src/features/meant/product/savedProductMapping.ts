import type { SaveUserProductInput, UserSavedProductProfile } from '../../../lib/apiClient'
import type { Preference, Product } from '../types'
import { displayProductCategoryValue, minorUnitsToMajor } from '../utils'
import { productCuratedFields, productWithCuratedFields } from './productCuration'

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
    imageUrl: authoritative ? product.imageUrl : null,
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
  return {
    id: product.id,
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
  }
}
