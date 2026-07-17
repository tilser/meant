import { getMerchantProductDetails } from '../../../lib/apiClient'
import type { Offer, Product, UserLocation } from '../types'
import { normalizedMerchantName } from '../utils'
import { canResolveCartOffer, offerCartable } from './utils'

export type ResolveCartableOfferResult =
  | { ok: true; product: Product; offer: Offer; productVariantId: string }
  | { ok: false; offer: Offer | null; message: string }

function browserLanguage(): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  return window.navigator.language.split('-')[0] || null
}

function sameOfferMerchant(left: Offer, right: Offer): boolean {
  if (left.merchantId && right.merchantId) {
    return left.merchantId === right.merchantId
  }
  const leftDomain = normalizedMerchantName(left.merchantDomain)
  const rightDomain = normalizedMerchantName(right.merchantDomain)
  if (leftDomain && rightDomain) {
    return leftDomain === rightDomain
  }
  return normalizedMerchantName(left.merchant) === normalizedMerchantName(right.merchant)
}

function productWithResolvedCartOffer(product: Product, offer: Offer): Product {
  let replaced = false
  const offers = product.offers.map((candidate) => {
    if (!sameOfferMerchant(candidate, offer)) {
      return candidate
    }
    replaced = true
    return { ...candidate, ...offer }
  })
  return {
    ...product,
    offers: replaced ? offers : [offer, ...product.offers],
  }
}

export async function resolveCartableOffer(input: {
  product: Product
  offer: Offer | null
  location?: UserLocation
  expectedUserId?: string
}): Promise<ResolveCartableOfferResult> {
  const { product, offer, location } = input
  if (!offer) {
    return {
      ok: false,
      offer: null,
      message: 'This item does not have a merchant offer that can be added to cart.',
    }
  }

  const directVariantId = offer.productVariantId?.trim()
  if (directVariantId && offerCartable(offer)) {
    return { ok: true, product, offer, productVariantId: directVariantId }
  }

  if (!canResolveCartOffer(product, offer)) {
    return {
      ok: false,
      offer,
      message: 'This item needs merchant product details before it can be added to cart.',
    }
  }

  const merchantId = offer.merchantId ?? product.merchantId ?? null
  const merchantDomain = offer.merchantDomain ?? product.merchantDomain ?? null
  const merchantProductId = product.merchantProductId?.trim()
  if (!merchantId || !merchantProductId) {
    return {
      ok: false,
      offer,
      message: 'This item is missing merchant product details needed for cart checkout.',
    }
  }

  const details = await getMerchantProductDetails({
    merchantId,
    productId: merchantProductId,
    addressCountry: location?.code,
    language: browserLanguage(),
    expectedUserId: input.expectedUserId,
  })
  const resolvedVariantId = details.selectedVariantId?.trim()
  const resolvedOffer: Offer = {
    ...offer,
    merchantId,
    merchantDomain,
    productVariantId: resolvedVariantId,
    variantTitle: details.selectedVariantTitle ?? offer.variantTitle,
    available: details.selectedVariantAvailable ?? offer.available,
  }
  if (!resolvedVariantId || !offerCartable(resolvedOffer)) {
    return {
      ok: false,
      offer: resolvedOffer,
      message: 'Merchant product details did not return an available checkout variant.',
    }
  }

  return {
    ok: true,
    product: productWithResolvedCartOffer(product, resolvedOffer),
    offer: resolvedOffer,
    productVariantId: resolvedVariantId,
  }
}
