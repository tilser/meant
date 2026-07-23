import type { Product } from '../types'
import type { DiscoverChatMessage } from '../chat/types'

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

export function cartInChatMessage(messageId: string): DiscoverChatMessage {
  return {
    id: messageId,
    role: 'ai',
    blocks: [{ type: 'cart', lines: [] }],
  }
}

export async function addChatProductToCart(
  product: Product,
  addSelectedOfferToCart: (product: Product, offerKey: string) => Promise<boolean>,
  showCartInChat: () => void,
): Promise<ChatCartAdditionResult> {
  const offerKey = exactProductOfferKey(product)
  if (!offerKey) {
    return 'missing-offer'
  }
  showCartInChat()
  try {
    return (await addSelectedOfferToCart(product, offerKey)) ? 'added' : 'failed'
  } catch {
    return 'failed'
  }
}
