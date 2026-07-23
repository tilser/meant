import type { Product } from '../types'

export type ChatCartAdditionResult = 'added' | 'failed' | 'missing-offer'

export function exactProductOfferKey(product: Product): string | null {
  const candidates = [
    product.canonicalProduct?.recommendedOfferKey,
    product.canonicalProduct?.offers[0]?.key,
    product.offers.find((offer) => offer.offerKey?.trim())?.offerKey,
  ]
  return (
    candidates
      .map((candidate) => candidate?.trim())
      .find((candidate): candidate is string => Boolean(candidate)) ?? null
  )
}

export async function addChatProductToCart(
  product: Product,
  addSelectedOfferToCart: (product: Product, offerKey: string) => Promise<boolean>,
): Promise<ChatCartAdditionResult> {
  const offerKey = exactProductOfferKey(product)
  if (!offerKey) {
    return 'missing-offer'
  }
  try {
    return (await addSelectedOfferToCart(product, offerKey)) ? 'added' : 'failed'
  } catch {
    return 'failed'
  }
}
