import type { Product } from '../types'

export function productArtworkUrl(product: Product, imageUrl?: string | null): string | null {
  return (
    imageUrl?.trim() ||
    product.imageUrl?.trim() ||
    product.media
      ?.filter((item) => item.type?.toLowerCase() === 'image')
      ?.map((item) => item.url?.trim() ?? '')
      ?.find(Boolean) ||
    null
  )
}
