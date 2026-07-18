import { describe, expect, test } from 'bun:test'

import type { CanonicalProductProfile } from '../../../lib/apiClient'
import { createDiscoverChatThread } from './utils'
import {
  applyProductResultSetHydration,
  markProductResultSetsLoading,
  resetLoadingProductResultSets,
  retryFailedProductResultSet,
  unresolvedProductResultSetIds,
} from './productResultSetHydration'

const RESULT_SET_ID = '00000000-0000-4000-8000-000000000101'

function canonicalProduct(key: string): CanonicalProductProfile {
  return {
    key,
    title: `Product ${key}`,
    media: [],
    attributes: [],
    materials: [],
    certifications: [],
    attribution: [],
    identityEvidence: [],
    provenance: [],
    personalization: {
      whyMeantForYou: 'Relevant product',
      matchedFilterIds: [],
      missedFilterIds: [],
    },
    recommendedOfferKey: `${key}-offer`,
    offers: [
      {
        key: `${key}-offer`,
        identity: {
          provider: 'shopify',
          merchantScope: {
            type: 'EXTERNAL_MERCHANT',
            externalMerchantIdentity: { type: 'MERCHANT', value: `${key}-merchant` },
          },
          externalProductIdentity: { type: 'PRODUCT', value: `${key}-product` },
          components: [],
        },
        merchantName: 'Merchant',
        price: { minorUnits: 1000, currency: 'USD' },
        availability: { status: 'IN_STOCK' },
        delivery: [],
        selectedOptions: [],
        checkoutExperience: 'MEANT_MANAGED',
        commercialState: { authority: 'REHYDRATED_CURRENT', rehydrationStatus: 'FRESH' },
        provenance: [],
      },
    ],
  }
}

function referencedThread() {
  return createDiscoverChatThread([
    {
      id: 'assistant-1',
      role: 'ai',
      blocks: [
        { type: 'text', text: 'Saved result text' },
        {
          type: 'products',
          products: [],
          query: 'shoes',
          productResultSetId: RESULT_SET_ID,
        },
      ],
    },
  ])
}

describe('Discover product-result history hydration', () => {
  test('finds each unresolved result set only once', () => {
    const thread = referencedThread()
    const duplicate = {
      ...thread,
      messages: [...thread.messages, thread.messages[0]!],
    }

    expect(unresolvedProductResultSetIds(duplicate)).toEqual([RESULT_SET_ID])
    expect(
      unresolvedProductResultSetIds(markProductResultSetsLoading(thread, [RESULT_SET_ID])),
    ).toEqual([])
  })

  test('maps current products in server order and retains partial-unavailable state', () => {
    const thread = markProductResultSetsLoading(referencedThread(), [RESULT_SET_ID])
    const hydrated = applyProductResultSetHydration(thread, [
      {
        resultSetId: RESULT_SET_ID,
        status: 'loaded',
        products: [canonicalProduct('second'), canonicalProduct('first')],
        unavailableCount: 2,
      },
    ])
    const productsBlock = hydrated.messages[0]?.blocks?.[1]

    expect(productsBlock?.type).toBe('products')
    if (productsBlock?.type !== 'products') throw new Error('Expected products block')
    expect(productsBlock.products.map((product) => product.id)).toEqual(['second', 'first'])
    expect(productsBlock.unavailableCount).toBe(2)
    expect(productsBlock.historyHydration).toBe('loaded')
    expect(hydrated.messages[0]?.blocks?.[0]).toEqual({
      type: 'text',
      text: 'Saved result text',
    })
  })

  test('marks all-result and transport failures terminal so they do not refetch forever', () => {
    const loading = markProductResultSetsLoading(referencedThread(), [RESULT_SET_ID])
    const unavailable = applyProductResultSetHydration(loading, [
      {
        resultSetId: RESULT_SET_ID,
        status: 'loaded',
        products: [],
        unavailableCount: 4,
      },
    ])
    const failed = applyProductResultSetHydration(loading, [
      { resultSetId: RESULT_SET_ID, status: 'failed' },
    ])

    expect(unresolvedProductResultSetIds(unavailable)).toEqual([])
    expect(unresolvedProductResultSetIds(failed)).toEqual([])
  })

  test('makes an aborted loading attempt retryable without changing the thread timestamp', () => {
    const thread = referencedThread()
    const loading = markProductResultSetsLoading(thread, [RESULT_SET_ID])
    const reset = resetLoadingProductResultSets(loading, [RESULT_SET_ID])

    expect(unresolvedProductResultSetIds(reset)).toEqual([RESULT_SET_ID])
    expect(reset.updatedAt).toBe(thread.updatedAt)
  })

  test('makes an explicit request failure retryable without changing durable history metadata', () => {
    const thread = referencedThread()
    const failed = applyProductResultSetHydration(
      markProductResultSetsLoading(thread, [RESULT_SET_ID]),
      [
        {
          resultSetId: RESULT_SET_ID,
          status: 'failed',
        },
      ],
    )
    const retryable = retryFailedProductResultSet(failed, RESULT_SET_ID)

    expect(unresolvedProductResultSetIds(retryable)).toEqual([RESULT_SET_ID])
    expect(retryable.updatedAt).toBe(thread.updatedAt)
  })
})
