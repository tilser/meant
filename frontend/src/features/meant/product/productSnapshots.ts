import type { MerchantProductDetailsProfile } from '../../../lib/apiClient'
import type { Product, ProductId, ProductMedia } from '../types'

function nonEmptyImageUrl(value?: string | null): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function firstProductMediaImage(media?: readonly ProductMedia[]): string | null {
  return (
    media
      ?.filter((item) => item.type?.toLowerCase() === 'image')
      ?.map((item) => nonEmptyImageUrl(item.url))
      ?.find((url): url is string => Boolean(url)) ?? null
  )
}

export function productImageUrl(product: Pick<Product, 'imageUrl' | 'media'>): string | null {
  return nonEmptyImageUrl(product.imageUrl) ?? firstProductMediaImage(product.media)
}

function mergeProductMediaSnapshots(
  existingMedia?: readonly ProductMedia[],
  nextMedia?: readonly ProductMedia[],
): ProductMedia[] {
  const merged: ProductMedia[] = []
  const seen = new Set<string>()
  for (const item of [...(nextMedia ?? []), ...(existingMedia ?? [])]) {
    const url = nonEmptyImageUrl(item.url)
    if (!url) {
      continue
    }
    const key = `${item.type?.toLowerCase() ?? 'media'}|${url}`
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    merged.push({ ...item, url })
  }
  return merged
}

export function mergeProductSnapshot(existing: Product | undefined, product: Product): Product {
  if (!existing) {
    const imageUrl = productImageUrl(product)
    return imageUrl && imageUrl !== product.imageUrl ? { ...product, imageUrl } : product
  }

  const media = mergeProductMediaSnapshots(existing.media, product.media)
  const imageUrl =
    nonEmptyImageUrl(product.imageUrl) ??
    firstProductMediaImage(media) ??
    nonEmptyImageUrl(existing.imageUrl) ??
    firstProductMediaImage(existing.media)

  return {
    ...product,
    imageUrl,
    media: media.length > 0 ? media : product.media,
  }
}

/**
 * Saving confirms the durable reference, but the immediate response deliberately contains no
 * rehydrated commercial facts. Keep the live server-issued offer snapshot until the saved-product
 * detail endpoint refreshes it instead of replacing it with an empty, non-authoritative shell.
 */
export function confirmedSavedProductSnapshot(live: Product, confirmed: Product): Product {
  const hasLiveCommerce = Boolean(live.canonicalProduct) || live.offers.length > 0
  if (confirmed.commercialFactsAuthoritative || !hasLiveCommerce) {
    return mergeProductSnapshot(live, confirmed)
  }

  return mergeProductSnapshot(confirmed, {
    ...live,
    id: confirmed.id,
    productHash: confirmed.productHash ?? live.productHash,
    name: confirmed.name,
    brand: confirmed.brand,
    category: confirmed.category,
    tone: confirmed.tone,
    imageUrl: confirmed.imageUrl ?? live.imageUrl,
    productUrl: confirmed.productUrl ?? live.productUrl,
    remote: confirmed.remote,
    match: confirmed.match,
    satisfies: confirmed.satisfies,
    misses: confirmed.misses,
    note: confirmed.note,
    pros: confirmed.pros,
    cons: confirmed.cons,
    review: confirmed.review,
    needs: confirmed.needs,
    provides: confirmed.provides,
  })
}

function nonEmptyDetailArray<T>(
  current?: readonly T[],
  existing?: readonly T[],
): readonly T[] | undefined {
  if (current && current.length > 0) return current
  return existing && existing.length > 0 ? existing : current
}

function nonEmptyDetailText(current?: string | null, existing?: string | null): string | null {
  return nonEmptyImageUrl(current) ?? nonEmptyImageUrl(existing)
}

export function mergeRehydratedProductDetails(
  existing?: MerchantProductDetailsProfile | null,
  refreshed?: MerchantProductDetailsProfile | null,
): MerchantProductDetailsProfile | null {
  if (!refreshed) return existing ?? null
  if (!existing) return refreshed

  return {
    ...refreshed,
    productId: nonEmptyDetailText(refreshed.productId, existing.productId),
    handle: nonEmptyDetailText(refreshed.handle, existing.handle),
    title: nonEmptyDetailText(refreshed.title, existing.title),
    description: nonEmptyDetailText(refreshed.description, existing.description),
    url: nonEmptyDetailText(refreshed.url, existing.url),
    imageUrl: nonEmptyDetailText(refreshed.imageUrl, existing.imageUrl),
    images: [...(nonEmptyDetailArray(refreshed.images, existing.images) ?? [])],
    media: [...(nonEmptyDetailArray(refreshed.media, existing.media) ?? [])],
    categories: [...(nonEmptyDetailArray(refreshed.categories, existing.categories) ?? [])],
    tags: [...(nonEmptyDetailArray(refreshed.tags, existing.tags) ?? [])],
    options: [...(nonEmptyDetailArray(refreshed.options, existing.options) ?? [])],
    variants: [...(nonEmptyDetailArray(refreshed.variants, existing.variants) ?? [])],
    selectedVariantId: nonEmptyDetailText(refreshed.selectedVariantId, existing.selectedVariantId),
    selectedVariantTitle: nonEmptyDetailText(
      refreshed.selectedVariantTitle,
      existing.selectedVariantTitle,
    ),
    selectedVariantSku: nonEmptyDetailText(
      refreshed.selectedVariantSku,
      existing.selectedVariantSku,
    ),
    selectedVariantImageUrl: nonEmptyDetailText(
      refreshed.selectedVariantImageUrl,
      existing.selectedVariantImageUrl,
    ),
    selectedVariantImageAltText: nonEmptyDetailText(
      refreshed.selectedVariantImageAltText,
      existing.selectedVariantImageAltText,
    ),
    selectedOptions: [
      ...(nonEmptyDetailArray(refreshed.selectedOptions, existing.selectedOptions) ?? []),
    ],
    skus: [...(nonEmptyDetailArray(refreshed.skus, existing.skus) ?? [])],
    certifications: [
      ...(nonEmptyDetailArray(refreshed.certifications, existing.certifications) ?? []),
    ],
    materials: [...(nonEmptyDetailArray(refreshed.materials, existing.materials) ?? [])],
    collections: [...(nonEmptyDetailArray(refreshed.collections, existing.collections) ?? [])],
    attributes: [...(nonEmptyDetailArray(refreshed.attributes, existing.attributes) ?? [])],
    // Provider notices are current disclosures. An empty fresh response must clear old notices.
    messages: [...refreshed.messages],
    totalVariants: refreshed.totalVariants ?? existing.totalVariants,
  }
}

/** Keep provider copy/media/options visible, but remove facts that must be current to be shown. */
export function savedProductDetailsRefreshShell(
  details?: MerchantProductDetailsProfile | null,
): MerchantProductDetailsProfile | null {
  if (!details) return null
  return {
    ...details,
    priceMin: null,
    priceMax: null,
    priceCurrency: null,
    listPriceMin: null,
    listPriceMax: null,
    listPriceCurrency: null,
    requiresSellingPlan: null,
    selectedVariantPriceAmount: null,
    selectedVariantPriceCurrency: null,
    selectedVariantListPriceAmount: null,
    selectedVariantListPriceCurrency: null,
    selectedVariantAvailable: null,
    variants: details.variants.map((variant) => ({
      ...variant,
      priceAmount: null,
      priceCurrency: null,
      listPriceAmount: null,
      listPriceCurrency: null,
      available: null,
    })),
  }
}

/**
 * A saved-product refresh owns every cart, offer, price, availability, and routing field. Product
 * copy and catalog enrichment are presentation-only, so a provider response that is temporarily
 * less rich must not make an already-visible detail collapse. The canonical search-session
 * payload is always removed even when it was present on the live product.
 */
export function refreshedSavedProductSnapshot(
  existing: Product | undefined,
  refreshed: Product,
): Product {
  const merged = mergeProductSnapshot(existing, refreshed)
  if (!existing) {
    return { ...merged, canonicalProduct: undefined }
  }

  return {
    ...merged,
    media: nonEmptyDetailArray(merged.media, existing.media),
    catalogCategories: nonEmptyDetailArray(refreshed.catalogCategories, existing.catalogCategories),
    certifications: nonEmptyDetailArray(refreshed.certifications, existing.certifications),
    materials: nonEmptyDetailArray(refreshed.materials, existing.materials),
    skus: nonEmptyDetailArray(refreshed.skus, existing.skus),
    collections: nonEmptyDetailArray(refreshed.collections, existing.collections),
    catalogAttributes: nonEmptyDetailArray(refreshed.catalogAttributes, existing.catalogAttributes),
    detailDescription: nonEmptyDetailText(refreshed.detailDescription, existing.detailDescription),
    detailOptions: nonEmptyDetailArray(refreshed.detailOptions, existing.detailOptions),
    selectedOptions: nonEmptyDetailArray(refreshed.selectedOptions, existing.selectedOptions),
    totalVariants: refreshed.totalVariants ?? existing.totalVariants,
    rehydratedDetails: mergeRehydratedProductDetails(
      existing.rehydratedDetails,
      refreshed.rehydratedDetails,
    ),
    canonicalProduct: undefined,
  }
}

/**
 * Keep only presentation data while a durable saved-product reference is being rehydrated.
 * Search-session and previously rehydrated offer keys must never remain cartable during this
 * transition: a failed refresh therefore leaves this safe shell available for retry.
 */
export function savedProductRefreshShell(product: Product): Product {
  return {
    ...product,
    merchantId: null,
    merchantDomain: null,
    merchantProductId: null,
    priceFrom: null,
    priceFromMinorUnits: null,
    priceCurrency: null,
    listPrice: null,
    merchants: 0,
    selectedVariantAvailable: null,
    offers: [],
    commercialFactsAuthoritative: false,
    rehydratedDetails: savedProductDetailsRefreshShell(product.rehydratedDetails),
    canonicalProduct: undefined,
  }
}

export function upsertProductSnapshot(products: Product[], product: Product): Product[] {
  const existingIndex = products.findIndex((candidate) => candidate.id === product.id)
  if (existingIndex < 0) {
    return [mergeProductSnapshot(undefined, product), ...products]
  }
  return products.map((candidate, index) =>
    index === existingIndex ? mergeProductSnapshot(candidate, product) : candidate,
  )
}

export function productSnapshotsForIds(products: Product[], ids: readonly ProductId[]): Product[] {
  const byId = new Map(products.map((product) => [product.id, product] as const))
  return ids.map((id) => byId.get(id)).filter((product): product is Product => Boolean(product))
}

/**
 * Return the first snapshot for each product id. Callers express authority through collection
 * order, so durable saved-product snapshots can stay ahead of live/search copies that only carry
 * a session-scoped offer key.
 */
export function uniqueProductSnapshotsByPriority(
  ...collections: readonly (readonly Product[])[]
): Product[] {
  const products: Product[] = []
  const seen = new Set<ProductId>()
  for (const collection of collections) {
    for (const product of collection) {
      if (seen.has(product.id)) continue
      seen.add(product.id)
      products.push(product)
    }
  }
  return products
}

export function appendProductSnapshots(
  products: Product[],
  nextProducts: readonly Product[],
): Product[] {
  const merged = [...products]
  const indexes = new Map(merged.map((product, index) => [product.id, index] as const))
  nextProducts.forEach((product) => {
    const index = indexes.get(product.id)
    if (index === undefined) {
      indexes.set(product.id, merged.length)
      merged.push(mergeProductSnapshot(undefined, product))
      return
    }
    merged[index] = mergeProductSnapshot(merged[index], product)
  })
  return merged
}
