import type { Preference, Product, UserLocation } from '../types'
import { productCuratedFields } from '../product/productCuration'
import { productPriceFrom } from '../utils'

export function assistantProductContext(
  product: Product,
  deliveryLocations: readonly UserLocation[],
  preferences: readonly Preference[] = [],
) {
  const curatedFields = productCuratedFields(product, preferences)
  return {
    id: product.id,
    name: product.name,
    brand: product.brand,
    category: product.category,
    match: product.match,
    priceFrom: productPriceFrom(product, deliveryLocations),
    note: curatedFields.note,
  }
}
