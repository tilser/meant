import type { Product } from '../types'
import type { DiscoverChatMessage } from '../chat/types'
import type { DirectPurchasePreparation } from '../product/directPurchasePreparation'

export type ChatCartAdditionResult = 'added' | 'failed' | 'requires-selection' | 'unavailable'

/** A historical key is a trusted product/merchant anchor, never an exact cart selection. */
export function productOfferAnchorKey(product: Product): string | null {
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
  preparePurchase: (product: Product) => Promise<DirectPurchasePreparation>,
  addSelectedOfferToCart: (product: Product, offerKey: string) => Promise<boolean>,
  showCartInChat: () => void,
): Promise<ChatCartAdditionResult> {
  let prepared: DirectPurchasePreparation
  try {
    prepared = await preparePurchase(product)
  } catch {
    return 'failed'
  }
  if (prepared.status !== 'ready') return prepared.status

  showCartInChat()
  try {
    return (await addSelectedOfferToCart(prepared.product, prepared.offerKey)) ? 'added' : 'failed'
  } catch {
    return 'failed'
  }
}
