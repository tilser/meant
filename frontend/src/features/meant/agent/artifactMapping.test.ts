import { describe, expect, test } from 'bun:test'

import type {
  AgentArtifactProfile,
  AgentConversationDetailProfile,
  AgentMessageProfile,
  CanonicalProductProfile,
} from '../../../lib/apiClient'
import {
  cartItemsFromAgentArtifacts,
  discoverMessagesFromAgentConversation,
  latestCartSnapshotArtifacts,
  productFromAgentArtifact,
  productInteractionState,
} from './artifactMapping'

const createdAt = '2026-07-18T12:00:00Z'

function canonicalProduct(key: string): CanonicalProductProfile {
  return {
    key,
    title: 'Grounded trail shoe',
    media: [],
    attributes: [{ name: 'brand', value: 'Meant Test' }],
    materials: [],
    certifications: [],
    attribution: [],
    identityEvidence: [],
    provenance: [],
    personalization: {
      whyMeantForYou: 'Matches your saved trail-running preference.',
      matchedFilterIds: ['trail-running'],
      missedFilterIds: [],
    },
    recommendedOfferKey: 'offer-1',
    offers: [
      {
        key: 'offer-1',
        identity: {
          provider: 'test',
          merchantScope: {
            type: 'EXTERNAL_MERCHANT',
            externalMerchantIdentity: { type: 'MERCHANT', value: 'merchant-1' },
          },
          externalProductIdentity: { type: 'PRODUCT', value: 'external-product-1' },
          externalVariantIdentity: { type: 'VARIANT', value: 'variant-1' },
          components: [],
        },
        merchantName: 'Trail Shop',
        price: { minorUnits: 12900, currency: 'USD' },
        availability: { status: 'IN_STOCK' },
        delivery: [],
        selectedOptions: [{ name: 'Size', value: '42' }],
        checkoutExperience: 'MEANT_MANAGED',
        commercialState: { authority: 'REHYDRATED_CURRENT', rehydrationStatus: 'FRESH' },
        provenance: [],
      },
    ],
  }
}

function artifact(
  overrides: Partial<AgentArtifactProfile> & Pick<AgentArtifactProfile, 'type' | 'stableKey'>,
): AgentArtifactProfile {
  return {
    artifactId: `artifact:${overrides.stableKey}:${overrides.createdAt ?? createdAt}`,
    messageId: 'message-tool',
    runId: 'run-1',
    ordinal: 1,
    label: 'Artifact',
    canonicalProductKey: null,
    offerKey: null,
    inventoryItemId: null,
    cartId: null,
    cartLineId: null,
    checkoutAttemptId: null,
    payloadJson: '{}',
    createdAt,
    ...overrides,
  }
}

describe('agent artifact mapping', () => {
  test('maps direct and nested canonical product payloads through the existing product adapter', () => {
    const product = canonicalProduct('product-1')
    const direct = artifact({
      type: 'PRODUCT',
      stableKey: product.key,
      canonicalProductKey: product.key,
      payloadJson: JSON.stringify(product),
    })
    const nested = artifact({
      type: 'PRODUCT',
      stableKey: `${product.key}:detail`,
      canonicalProductKey: product.key,
      payloadJson: JSON.stringify({ product, detailStatus: 'FRESH' }),
    })

    expect(productFromAgentArtifact(direct)).toMatchObject({
      id: 'product-1',
      name: 'Grounded trail shoe',
      priceFrom: 129,
    })
    expect(productFromAgentArtifact(nested)?.canonicalProduct).toEqual(product)
  })

  test('uses the latest persistent pin/watch state for every product', () => {
    const states = [
      artifact({
        type: 'PRODUCT_STATE',
        stableKey: 'product-state:product-1',
        canonicalProductKey: 'product-1',
        payloadJson: JSON.stringify({ pinned: true, watched: false }),
      }),
      artifact({
        type: 'PRODUCT_STATE',
        stableKey: 'product-state:product-1',
        canonicalProductKey: 'product-1',
        createdAt: '2026-07-18T12:01:00Z',
        payloadJson: JSON.stringify({ pinned: false, watched: true }),
      }),
    ]

    const state = productInteractionState(states)

    expect(state.pinned.has('product-1')).toBe(false)
    expect(state.watched.has('product-1')).toBe(true)
  })

  test('does not resurrect a removed cart line from an older artifact snapshot', () => {
    const productArtifact = artifact({
      type: 'PRODUCT',
      stableKey: 'product-1',
      canonicalProductKey: 'product-1',
      offerKey: 'offer-1',
      payloadJson: JSON.stringify(canonicalProduct('product-1')),
    })
    const firstCart = artifact({
      type: 'CART',
      stableKey: 'cart:cart-1',
      messageId: 'cart-message-1',
      cartId: 'cart-1',
      payloadJson: JSON.stringify({ cartId: 'cart-1', merchantDomain: 'trail.example' }),
    })
    const firstLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:line-1',
      messageId: 'cart-message-1',
      cartId: 'cart-1',
      cartLineId: 'line-1',
      offerKey: 'offer-1',
      payloadJson: JSON.stringify({
        cartLineId: 'line-1',
        productTitle: 'Grounded trail shoe',
        quantity: 1,
        offerKey: 'offer-1',
      }),
    })
    const emptyCart = artifact({
      type: 'CART',
      stableKey: 'cart:cart-1',
      messageId: 'cart-message-2',
      cartId: 'cart-1',
      createdAt: '2026-07-18T12:02:00Z',
      payloadJson: JSON.stringify({ cartId: 'cart-1', merchantDomain: 'trail.example' }),
    })

    const snapshot = latestCartSnapshotArtifacts([firstCart, firstLine, emptyCart])
    const products = [productFromAgentArtifact(productArtifact)!]

    expect(snapshot.map((item) => item.artifactId)).toEqual([emptyCart.artifactId])
    expect(cartItemsFromAgentArtifacts(snapshot, products)).toEqual([])
  })

  test('rebuilds the immutable transcript from server-owned messages and artifacts', () => {
    const user: AgentMessageProfile = {
      messageId: 'message-user',
      runId: 'run-1',
      sequenceNumber: 1,
      role: 'USER',
      contentKind: 'TEXT',
      textContent: 'Find trail shoes',
      contentJson: null,
      correlationId: null,
      createdAt,
    }
    const tool: AgentMessageProfile = {
      messageId: 'message-tool',
      runId: 'run-1',
      sequenceNumber: 2,
      role: 'TOOL',
      contentKind: 'TOOL_RESULT',
      textContent: null,
      contentJson: '{}',
      correlationId: 'call-1:search_catalog',
      createdAt,
    }
    const product = canonicalProduct('product-1')
    const conversation: AgentConversationDetailProfile = {
      conversationId: 'conversation-1',
      title: 'Trail shoes',
      status: 'ACTIVE',
      activeMissionId: null,
      latestSequence: 2,
      createdAt,
      updatedAt: createdAt,
      rollingSummary: null,
      summaryVersion: 0,
      latestCursor: 4,
      messages: [user, tool],
      artifacts: [
        artifact({
          type: 'PRODUCT',
          stableKey: product.key,
          canonicalProductKey: product.key,
          payloadJson: JSON.stringify(product),
        }),
      ],
    }

    const messages = discoverMessagesFromAgentConversation(conversation, [])

    expect(messages[0]).toMatchObject({ role: 'you', text: 'Find trail shoes' })
    expect(messages[1]?.blocks?.[0]).toMatchObject({
      type: 'products',
      products: [{ id: 'product-1' }],
    })
  })

  test('renders a durable shopping mission with deterministic coverage', () => {
    const tool: AgentMessageProfile = {
      messageId: 'message-tool',
      runId: 'run-1',
      sequenceNumber: 1,
      role: 'TOOL',
      contentKind: 'TOOL_RESULT',
      textContent: null,
      contentJson: '{}',
      correlationId: 'call-1:create_shopping_mission',
      createdAt,
    }
    const mission = artifact({
      type: 'MISSION',
      stableKey: 'mission:picnic',
      label: 'Summer picnic in San Francisco',
      payloadJson: JSON.stringify({
        goal: 'Summer picnic in San Francisco',
        status: 'PLANNING',
        assumptions: [{ key: 'party-size', value: 'Planning for four people.' }],
        requirements: [
          { id: 'blanket', label: 'Picnic blanket', requiredQuantity: 1, optional: false },
          { id: 'drinks', label: 'Cold drinks', requiredQuantity: 4, optional: false },
        ],
        coverage: [
          {
            requirementId: 'blanket',
            state: 'COVERED',
            requiredQuantity: 1,
            coveredQuantity: 1,
          },
          {
            requirementId: 'drinks',
            state: 'PARTIAL',
            requiredQuantity: 4,
            coveredQuantity: 2,
          },
        ],
      }),
    })
    const conversation: AgentConversationDetailProfile = {
      conversationId: 'conversation-1',
      title: 'Picnic',
      status: 'ACTIVE',
      activeMissionId: 'picnic',
      latestSequence: 1,
      createdAt,
      updatedAt: createdAt,
      rollingSummary: null,
      summaryVersion: 0,
      latestCursor: 2,
      messages: [tool],
      artifacts: [mission],
    }

    const messages = discoverMessagesFromAgentConversation(conversation, [])

    expect(messages[0]?.blocks?.[0]).toEqual({
      type: 'mission',
      goal: 'Summer picnic in San Francisco',
      status: 'PLANNING',
      assumptions: ['Planning for four people.'],
      requirements: [
        {
          id: 'blanket',
          label: 'Picnic blanket',
          state: 'COVERED',
          requiredQuantity: 1,
          coveredQuantity: 1,
          optional: false,
        },
        {
          id: 'drinks',
          label: 'Cold drinks',
          state: 'PARTIAL',
          requiredQuantity: 4,
          coveredQuantity: 2,
          optional: false,
        },
      ],
    })
  })
})
