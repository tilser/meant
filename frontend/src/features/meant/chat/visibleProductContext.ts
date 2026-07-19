import type { Product } from '../types'
import type { VisibleProductContext } from './types'

export const DESKTOP_PRODUCT_PAGE_SIZE = 4

export interface VisibleProductContextRegistration {
  conversationId: string
  context: VisibleProductContext | null
}

export type VisibleProductContextRegistry = Map<string, VisibleProductContextRegistration>

function boundedIndex(value: number, length: number): number {
  return Math.min(Math.max(length - 1, 0), Math.max(0, Math.trunc(value)))
}

export function visibleProductContextForBatch({
  products,
  sourceMessageId,
  isPhone,
  page,
  phoneIndex,
}: Readonly<{
  products: readonly Product[]
  sourceMessageId?: string
  isPhone: boolean
  page: number
  phoneIndex: number
}>): VisibleProductContext | null {
  const normalizedSourceMessageId = sourceMessageId?.trim()
  if (!normalizedSourceMessageId || products.length === 0) return null
  const startIndex = isPhone
    ? boundedIndex(phoneIndex, products.length)
    : boundedIndex(page, Math.ceil(products.length / DESKTOP_PRODUCT_PAGE_SIZE)) *
      DESKTOP_PRODUCT_PAGE_SIZE
  const visibleProducts = products.slice(
    startIndex,
    startIndex + (isPhone ? 1 : DESKTOP_PRODUCT_PAGE_SIZE),
  )
  const orderedCanonicalProductKeys = visibleProducts.flatMap((product) => {
    const key = product.canonicalProduct?.key?.trim()
    return key ? [key] : []
  })
  if (orderedCanonicalProductKeys.length !== visibleProducts.length) return null
  return {
    sourceMessageId: normalizedSourceMessageId,
    orderedCanonicalProductKeys,
  }
}

/**
 * Keeps the most recently mounted or interacted-with product batch authoritative. A mounted
 * but unbindable batch intentionally resolves to null instead of falling back to stale cards.
 */
export function updateVisibleProductContextRegistry(
  registry: VisibleProductContextRegistry,
  conversationId: string,
  sourceMessageId: string,
  context: VisibleProductContext | null | undefined,
): VisibleProductContext | null {
  const normalizedSourceMessageId = sourceMessageId.trim()
  if (!conversationId || !normalizedSourceMessageId) return null

  if (context === undefined) {
    registry.delete(normalizedSourceMessageId)
  } else {
    const verifiedContext =
      context?.sourceMessageId.trim() === normalizedSourceMessageId ? context : null
    registry.delete(normalizedSourceMessageId)
    registry.set(normalizedSourceMessageId, { conversationId, context: verifiedContext })
  }

  return (
    [...registry.values()]
      .reverse()
      .find((candidate) => candidate.conversationId === conversationId)?.context ?? null
  )
}
