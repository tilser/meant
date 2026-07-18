import type { CanonicalProductProfile } from '../../../lib/apiClient'
import { productFromCanonical } from '../product/groupedProductMapping'
import { normalizedProductResultSetId } from './productResultSetReference'
import type { DiscoverChatBlock, DiscoverChatThread } from './types'

type ProductsBlock = Extract<DiscoverChatBlock, { type: 'products' }>

export type ProductResultSetHydrationResolution =
  | {
      resultSetId: string
      status: 'loaded'
      products: readonly CanonicalProductProfile[]
      unavailableCount: number
    }
  | {
      resultSetId: string
      status: 'failed'
    }

function mapProductBlocks(
  thread: DiscoverChatThread,
  mapper: (block: ProductsBlock) => ProductsBlock,
): DiscoverChatThread {
  return {
    ...thread,
    messages: thread.messages.map((message) => ({
      ...message,
      blocks: message.blocks?.map((block) => (block.type === 'products' ? mapper(block) : block)),
    })),
  }
}

export function unresolvedProductResultSetIds(thread: DiscoverChatThread): string[] {
  const resultSetIds = new Set<string>()
  thread.messages.forEach((message) => {
    message.blocks?.forEach((block) => {
      if (block.type !== 'products' || block.products.length > 0 || block.historyHydration) {
        return
      }
      const resultSetId = normalizedProductResultSetId(block.productResultSetId)
      if (resultSetId) {
        resultSetIds.add(resultSetId)
      }
    })
  })
  return Array.from(resultSetIds)
}

export function markProductResultSetsLoading(
  thread: DiscoverChatThread,
  resultSetIds: readonly string[],
): DiscoverChatThread {
  const requested = new Set(resultSetIds)
  return mapProductBlocks(thread, (block) => {
    const resultSetId = normalizedProductResultSetId(block.productResultSetId)
    return resultSetId && requested.has(resultSetId) && block.products.length === 0
      ? { ...block, historyHydration: 'loading' }
      : block
  })
}

export function applyProductResultSetHydration(
  thread: DiscoverChatThread,
  resolutions: readonly ProductResultSetHydrationResolution[],
): DiscoverChatThread {
  const byId = new Map(resolutions.map((resolution) => [resolution.resultSetId, resolution]))
  return mapProductBlocks(thread, (block) => {
    const resultSetId = normalizedProductResultSetId(block.productResultSetId)
    const resolution = resultSetId ? byId.get(resultSetId) : undefined
    if (!resolution) {
      return block
    }
    if (resolution.status === 'failed') {
      return {
        ...block,
        products: [],
        unavailableCount: undefined,
        historyHydration: 'failed',
      }
    }
    const unavailableCount = Number.isFinite(resolution.unavailableCount)
      ? Math.max(0, Math.trunc(resolution.unavailableCount))
      : 0
    return {
      ...block,
      products: resolution.products.map(productFromCanonical),
      unavailableCount,
      historyHydration: 'loaded',
    }
  })
}

export function resetLoadingProductResultSets(
  thread: DiscoverChatThread,
  resultSetIds: readonly string[],
): DiscoverChatThread {
  const requested = new Set(resultSetIds)
  return mapProductBlocks(thread, (block) => {
    const resultSetId = normalizedProductResultSetId(block.productResultSetId)
    if (!resultSetId || !requested.has(resultSetId) || block.historyHydration !== 'loading') {
      return block
    }
    return { ...block, historyHydration: undefined }
  })
}

export function retryFailedProductResultSet(
  thread: DiscoverChatThread,
  resultSetId: string,
): DiscoverChatThread {
  return mapProductBlocks(thread, (block) => {
    if (
      normalizedProductResultSetId(block.productResultSetId) !== resultSetId ||
      block.historyHydration !== 'failed'
    ) {
      return block
    }
    return {
      ...block,
      historyHydration: undefined,
      unavailableCount: undefined,
    }
  })
}
