import type { UserProductSearchProductProfile } from '../../../lib/apiClient'
import { searchProductReviewInsight } from '../chat/utils'
import type {
  Preference,
  Product,
  ProductAgentStage,
  ProductCatalogAttribute,
  ProductCatalogCategory,
  ProductMedia,
} from '../types'
import { displayProductCategoryValue } from '../utils'
import { productWithCuratedFields } from './productCuration'

function stripHtml(value: string | null | undefined): string {
  return (
    value
      ?.replace(/<[^>]*>/g, ' ')
      .replace(/\s+/g, ' ')
      .trim() ?? ''
  )
}

function parsePriceAmount(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined) {
    return null
  }
  if (typeof value === 'number') {
    return value / 100
  }
  const parsed = Number.parseFloat(normalizeLocalizedPriceAmount(value))
  return Number.isFinite(parsed) ? parsed : null
}

function normalizeLocalizedPriceAmount(value: string): string {
  const cleaned = value.trim().replace(/[^0-9.,-]+/g, '')
  if (!cleaned) {
    return ''
  }
  const sign = cleaned.includes('-') ? '-' : ''
  const unsigned = cleaned.replace(/-/g, '')
  if (!/[0-9]/.test(unsigned)) {
    return ''
  }
  const lastDot = unsigned.lastIndexOf('.')
  const lastComma = unsigned.lastIndexOf(',')

  if (lastDot !== -1 && lastComma !== -1) {
    return (
      sign +
      (lastComma > lastDot
        ? unsigned.replace(/\./g, '').replace(',', '.')
        : unsigned.replace(/,/g, ''))
    )
  }
  if (lastComma !== -1) {
    return sign + normalizeSingleSeparatorPriceAmount(unsigned, ',')
  }
  if (lastDot !== -1) {
    return sign + normalizeSingleSeparatorPriceAmount(unsigned, '.')
  }
  return sign + unsigned
}

function normalizeSingleSeparatorPriceAmount(value: string, separator: ',' | '.'): string {
  const firstSeparator = value.indexOf(separator)
  const lastSeparator = value.lastIndexOf(separator)
  const separatorPattern = separator === ',' ? /,/g : /\./g
  if (firstSeparator !== lastSeparator) {
    return hasGroupedThousandsPriceAmount(value, separator)
      ? value.replace(separatorPattern, '')
      : ''
  }
  const fractionalDigits = value.length - lastSeparator - 1
  if (separator === ',' && fractionalDigits === 3 && lastSeparator <= 3) {
    return value.replace(separatorPattern, '')
  }
  return separator === ',' ? value.replace(',', '.') : value
}

function hasGroupedThousandsPriceAmount(value: string, separator: ',' | '.'): boolean {
  const firstSeparator = value.indexOf(separator)
  if (firstSeparator <= 0 || firstSeparator > 3) {
    return false
  }
  let groupStart = firstSeparator + 1
  while (groupStart < value.length) {
    const nextSeparator = value.indexOf(separator, groupStart)
    const groupEnd = nextSeparator === -1 ? value.length : nextSeparator
    if (groupEnd - groupStart !== 3) {
      return false
    }
    groupStart = groupEnd + 1
  }
  return groupStart === value.length + 1
}

function normalizeRatingScore(value: number | null | undefined): number | null {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return null
  }
  return Math.max(0, Math.min(5, value))
}

function searchProductPrice(product: UserProductSearchProductProfile): number {
  return (
    parsePriceAmount(product.selectedVariantPriceAmount) ??
    parsePriceAmount(product.detailPriceMin) ??
    parsePriceAmount(product.priceMinAmount) ??
    0
  )
}

function searchProductListPrice(
  product: UserProductSearchProductProfile,
  currentPrice: number,
): number | null {
  const listPrice = parsePriceAmount(product.listPriceAmount)
  if (listPrice === null || listPrice <= currentPrice) {
    return null
  }
  return listPrice
}

function searchProductMedia(product: UserProductSearchProductProfile): ProductMedia[] {
  const seen = new Set<string>()
  const fromApi = (product.media ?? [])
    .map((item): ProductMedia | null => {
      if (!item.url) {
        return null
      }
      return {
        type: item.type || 'image',
        url: item.url,
        altText: item.altText,
      }
    })
    .filter((item): item is ProductMedia => item !== null)
  const fallback = [product.selectedVariantImageUrl, product.detailImageUrl, product.imageUrl]
    .filter((url): url is string => Boolean(url))
    .map((url) => ({ type: 'image', url, altText: product.selectedVariantImageAltText }))

  return [...fromApi, ...fallback].filter((item) => {
    const key = `${item.type.toLowerCase()}|${item.url}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

function searchProductCatalogCategories(
  product: UserProductSearchProductProfile,
): ProductCatalogCategory[] {
  return (product.categories ?? [])
    .filter((category) => Boolean(displayProductCategoryValue(category.value)))
    .map((category) => ({
      value: displayProductCategoryValue(category.value) ?? '',
      taxonomy: category.taxonomy,
    }))
}

function searchProductStringValues(values: readonly string[] | null | undefined): string[] {
  const seen = new Set<string>()
  return (values ?? [])
    .map((value) => value.trim())
    .filter(Boolean)
    .filter((value) => {
      const key = value.toLowerCase()
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
}

function searchProductAttributes(
  product: UserProductSearchProductProfile,
): ProductCatalogAttribute[] {
  return (product.attributes ?? [])
    .filter((attribute) => Boolean(attribute.name) && Boolean(attribute.value))
    .map((attribute) => ({
      name: attribute.name ?? '',
      value: attribute.value ?? '',
    }))
}

function searchProductCategory(
  product: UserProductSearchProductProfile,
  preferences: readonly Preference[],
): string {
  const catalogCategory = (product.categories ?? [])
    .map((category) => displayProductCategoryValue(category.value))
    .find(Boolean)
  if (catalogCategory) {
    return catalogCategory
  }

  const category = (product.matchedFilterIds ?? [])
    .map((id) => preferences.find((preference) => preference.id === id)?.category)
    .find(Boolean)

  switch (category) {
    case 'food':
      return 'Groceries'
    case 'materials':
      return 'Clothing'
    case 'technology':
      return 'Technology'
    case 'home':
      return 'Home & Kitchen'
    case 'personal-care':
      return 'Personal Care'
    default:
      return 'Product'
  }
}

function toneForSearchProduct(product: UserProductSearchProductProfile): string {
  const tones = ['#e7ebef', '#eaede6', '#eceae7', '#eee9ed', '#e9ede8']
  const code = Array.from(product.productKey).reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return tones[code % tones.length] ?? tones[0]
}

export function productFromSearchResult(
  product: UserProductSearchProductProfile,
  preferences: readonly Preference[],
  agentStage?: ProductAgentStage,
): Product {
  const price = searchProductPrice(product)
  const media = searchProductMedia(product)
  const listPrice = searchProductListPrice(product, price)
  const catalogCategories = searchProductCatalogCategories(product)
  const certifications = searchProductStringValues(product.certifications)
  const materials = searchProductStringValues(product.materials)
  const skus = searchProductStringValues(product.skus)
  const collections = searchProductStringValues(product.collections)
  const catalogAttributes = searchProductAttributes(product)
  const brand = product.merchantName || product.merchantDomain
  const detail = stripHtml(product.detailDescription || product.descriptionHtml)
  const ratingScore = normalizeRatingScore(product.ratingScore)
  const reviewCount = Math.max(0, product.reviewCount ?? 0)
  const candidate = agentStage === 'candidate'
  const matchedFilterIds = product.matchedFilterIds ?? []
  const missedFilterIds = product.missedFilterIds ?? []
  const baseProduct: Product = {
    id: product.productKey,
    productHash: product.productHash,
    merchantId: product.merchantId,
    merchantDomain: product.merchantDomain,
    merchantProductId: product.productId,
    name: product.title,
    brand,
    category: searchProductCategory(product, preferences),
    tone: toneForSearchProduct(product),
    imageUrl:
      media.find((item) => item.type.toLowerCase() === 'image')?.url ||
      product.imageUrl ||
      product.detailImageUrl ||
      product.selectedVariantImageUrl,
    productUrl: product.url,
    remote: true,
    match: product.matchScore,
    priceFrom: price,
    listPrice,
    merchants: 1,
    satisfies: matchedFilterIds,
    misses: missedFilterIds,
    note: product.whyMeantForYou || detail,
    pros: [],
    cons: [],
    review: {
      score: ratingScore,
      count: reviewCount,
      insight: searchProductReviewInsight(candidate, ratingScore, reviewCount),
    },
    media,
    catalogCategories,
    certifications,
    materials,
    skus,
    collections,
    catalogAttributes,
    detailError: product.detailError,
    detailDescription: detail || null,
    offers: [
      {
        merchant: brand,
        price,
        delivery:
          product.available === false || product.selectedVariantAvailable === false
            ? 'Availability unclear'
            : 'Available from merchant',
        merchantId: product.merchantId,
        merchantDomain: product.merchantDomain,
        productVariantId: product.selectedVariantId,
        variantTitle: product.selectedVariantTitle,
        available:
          product.available === false || product.selectedVariantAvailable === false
            ? false
            : product.selectedVariantAvailable,
      },
    ],
    inventoryRelationship: product.inventoryRelationship,
    inventoryItemId: product.inventoryItemId,
    inventoryItemName: product.inventoryItemName,
    agentStage,
    agentUpdatedAt: agentStage ? Date.now() : undefined,
  }
  return productWithCuratedFields(baseProduct, preferences)
}
