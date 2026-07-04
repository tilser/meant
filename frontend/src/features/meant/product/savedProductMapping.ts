import type { SaveUserProductInput, UserSavedProductProfile } from '../../../lib/apiClient'
import type { Preference, Product } from '../types'
import { displayProductCategoryValue } from '../utils'
import { productCuratedFields, productWithCuratedFields } from './productCuration'

export function savedProductFromProfile(
  product: UserSavedProductProfile,
  preferences: readonly Preference[] = [],
): Product {
  const snapshot: Product = {
    id: product.id,
    productHash: product.productHash,
    name: product.name,
    brand: product.brand,
    category: displayProductCategoryValue(product.category) ?? 'Product',
    tone: product.tone,
    imageUrl: product.imageUrl,
    productUrl: product.productUrl,
    remote: product.remote,
    match: product.match,
    priceFrom: product.priceFrom,
    merchants: product.merchants,
    satisfies: product.satisfies,
    misses: product.misses,
    note: product.note,
    pros: product.pros,
    cons: product.cons,
    review: product.review,
    offers: product.offers,
    needs: product.needs ? (product.needs as Product['needs']) : undefined,
    provides:
      (product.provides?.length ?? 0) > 0 ? (product.provides as Product['provides']) : undefined,
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
