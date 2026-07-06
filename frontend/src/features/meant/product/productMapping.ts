import type {
  MerchantProductDetailsProfile,
  ProductOptionProfile,
  ProductSelectedOptionProfile,
} from '../../../lib/apiClient'
import type { Product, ProductMedia, ProductOption, ProductSelectedOption } from '../types'

export function productOptionsFromProfiles(
  options: readonly ProductOptionProfile[] | null | undefined,
): ProductOption[] {
  return (options ?? [])
    .map((option): ProductOption | null => {
      const name = option.name?.trim()
      const values = (option.values ?? []).map((value) => value.trim()).filter(Boolean)
      if (!name || values.length === 0) {
        return null
      }
      return { name, values }
    })
    .filter((option): option is ProductOption => option !== null)
}

export function productSelectedOptionsFromProfiles(
  options: readonly ProductSelectedOptionProfile[] | null | undefined,
): ProductSelectedOption[] {
  return (options ?? [])
    .map((option): ProductSelectedOption | null => {
      const name = option.name?.trim()
      const value = option.value?.trim()
      if (!name || !value) {
        return null
      }
      return { name, value }
    })
    .filter((option): option is ProductSelectedOption => option !== null)
}

function mediaFromMerchantDetails(details: MerchantProductDetailsProfile | null): ProductMedia[] {
  if (!details) {
    return []
  }
  const media = (details.media ?? [])
    .map((item): ProductMedia | null => {
      const url = item.url || item.previewImageUrl
      if (!url) {
        return null
      }
      return {
        type: item.type || 'image',
        url,
        altText: item.altText,
      }
    })
    .filter((item): item is ProductMedia => item !== null)
  const variantMedia = (details.variants ?? [])
    .flatMap((variant) => {
      if (!variant) {
        return []
      }
      return [
        ...(variant.media ?? []).map((item): ProductMedia | null => {
          const url = item?.url || item?.previewImageUrl
          if (!url) {
            return null
          }
          return {
            type: item?.type || 'image',
            url,
            altText: item?.altText || variant.imageAltText,
          }
        }),
        variant.imageUrl
          ? {
              type: 'image',
              url: variant.imageUrl,
              altText: variant.imageAltText,
            }
          : null,
      ]
    })
    .filter((item): item is ProductMedia => item !== null)
  const images = (details.images ?? [])
    .map((image): ProductMedia | null => {
      if (!image.url) {
        return null
      }
      return { type: 'image', url: image.url, altText: image.altText }
    })
    .filter((item): item is ProductMedia => item !== null)
  const fallbacks = [details.selectedVariantImageUrl, details.imageUrl]
    .filter((url): url is string => Boolean(url))
    .map((url) => ({ type: 'image', url, altText: details.selectedVariantImageAltText }))

  return [...media, ...variantMedia, ...images, ...fallbacks]
}

export function mergeProductMedia(
  product: Product,
  details: MerchantProductDetailsProfile | null,
): ProductMedia[] {
  const seen = new Set<string>()
  return [...mediaFromMerchantDetails(details), ...(product.media ?? [])].filter((item) => {
    const key = `${item.type?.toLowerCase() ?? 'media'}|${item.url}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}
