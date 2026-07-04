import type { Product, ProductId, ProductMedia } from '../types'

function nonEmptyImageUrl(value?: string | null): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function firstProductMediaImage(media?: readonly ProductMedia[]): string | null {
  return (
    media
      ?.filter((item) => item.type?.toLowerCase() === 'image')
      .map((item) => nonEmptyImageUrl(item.url))
      .find((url): url is string => Boolean(url)) ?? null
  )
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
    const imageUrl = nonEmptyImageUrl(product.imageUrl) ?? firstProductMediaImage(product.media)
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
