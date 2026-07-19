import { describe, expect, test } from 'bun:test'

import type {
  AgentArtifactProfile,
  AgentConversationDetailProfile,
  AgentMessageProfile,
  CanonicalProductProfile,
} from '../../../lib/apiClient'
import {
  blocksForAgentMessage,
  cartItemsFromAgentArtifacts,
  cartStateReplacementsFromAgentArtifacts,
  conciseProductResultIntroduction,
  currentAgentProductSnapshots,
  discoverMessagesFromAgentConversation,
  latestCartSnapshotArtifacts,
  mergeAgentProductSnapshots,
  productFromAgentArtifact,
  productInteractionState,
  productsFromAgentArtifacts,
} from './artifactMapping'

const createdAt = '2026-07-18T12:00:00Z'

function canonicalProduct(
  key: string,
  offerKey = 'offer-1',
  title = 'Grounded trail shoe',
): CanonicalProductProfile {
  return {
    key,
    title,
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
    recommendedOfferKey: offerKey,
    offers: [
      {
        key: offerKey,
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

function legacyDomainProduct(key = 'legacy-product') {
  const identity = {
    provider: { value: 'SHOPIFY' },
    merchantScope: {
      externalMerchantIdentity: {
        type: 'MERCHANT',
        namespace: 'SHOPIFY',
        value: 'merchant-1',
      },
      merchantIntegrationFallbackId: null,
    },
    externalProductIdentity: {
      type: 'PRODUCT',
      namespace: 'SHOPIFY',
      value: 'product-1',
    },
    externalVariantIdentity: {
      type: 'VARIANT',
      namespace: 'SHOPIFY',
      value: 'variant-1',
    },
    selectedOptions: [{ name: 'Size', value: '42', group: null }],
    components: [],
    sellingPlanIdentity: null,
  }
  const provenance = {
    provider: { value: 'SHOPIFY' },
    discoverySource: {
      provider: { value: 'SHOPIFY' },
      type: 'PROVIDER_CATALOG',
      value: 'global-catalog',
    },
    localRouting: { merchantIntegrationId: 'integration-1' },
    externalMerchantReference: identity.merchantScope.externalMerchantIdentity,
    externalMerchantDomain: 'trail.example',
    externalProductReference: identity.externalProductIdentity,
    externalVariantReference: identity.externalVariantIdentity,
    freshness: { observedAt: createdAt, freshUntil: null },
    sourceReference: { type: 'PROVIDER_CATALOG', reference: 'product-1', uri: null },
  }
  const offer = {
    identity,
    merchantName: 'Legacy Trail Shop',
    variantTitle: 'Blue / 42',
    price: { minorUnits: 14500, currency: 'USD' },
    listPrice: null,
    availability: { status: 'IN_STOCK', quantity: 3, availableAt: null },
    delivery: [],
    checkoutUrl: 'https://trail.example/checkout',
    rankingEvidence: { checkoutCapable: true },
    provenance: [provenance],
  }
  return {
    offer,
    product: {
      key,
      title: 'Legacy grounded trail shoe',
      description: 'Durable history written before the wire projection was fixed.',
      media: [],
      attributes: [{ name: 'brand', value: 'Legacy Test', group: null }],
      materials: [],
      certifications: [],
      attribution: [],
      identityEvidence: [],
      provenance: [provenance],
      retrievalSignals: [],
      offers: [offer],
    },
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
  test('uses the model result subject when a follow-up query is context-only', () => {
    expect(
      conciseProductResultIntroduction(
        'I found many running shoes. Here are a few options: Grounded trail shoe for $129.',
        "I don't see any",
      ),
    ).toBe('I found these running shoes:')
  })

  test('does not let an older conversation replace a fresher same-id product snapshot', () => {
    const olderArtifact = artifact({
      type: 'PRODUCT',
      stableKey: 'product-history',
      canonicalProductKey: 'product-history',
      createdAt: '2026-07-18T12:00:00Z',
      payloadJson: JSON.stringify(canonicalProduct('product-history', 'offer-old', 'Old offer')),
    })
    const newerArtifact = artifact({
      type: 'PRODUCT',
      stableKey: 'product-history',
      canonicalProductKey: 'product-history',
      createdAt: '2026-07-19T12:00:00Z',
      payloadJson: JSON.stringify(
        canonicalProduct('product-history', 'offer-new', 'Current offer'),
      ),
    })

    const merged = mergeAgentProductSnapshots(
      [{ product: productFromAgentArtifact(newerArtifact)!, createdAt: newerArtifact.createdAt }],
      [{ product: productFromAgentArtifact(olderArtifact)!, createdAt: olderArtifact.createdAt }],
    )

    expect(merged).toHaveLength(1)
    expect(merged[0]?.product).toMatchObject({ name: 'Current offer' })
    expect(merged[0]?.product.canonicalProduct?.recommendedOfferKey).toBe('offer-new')
  })

  test('orders product snapshots chronologically when instant precision differs', () => {
    const exactSecond = artifact({
      type: 'PRODUCT',
      stableKey: 'product-precision',
      canonicalProductKey: 'product-precision',
      createdAt: '2026-07-19T12:00:00Z',
      payloadJson: JSON.stringify(
        canonicalProduct('product-precision', 'offer-exact', 'Exact-second offer'),
      ),
    })
    const oneNanosecondLater = artifact({
      type: 'PRODUCT',
      stableKey: 'product-precision',
      canonicalProductKey: 'product-precision',
      createdAt: '2026-07-19T12:00:00.000000001Z',
      payloadJson: JSON.stringify(
        canonicalProduct('product-precision', 'offer-later', 'One nanosecond later'),
      ),
    })

    const merged = mergeAgentProductSnapshots(
      [
        {
          product: productFromAgentArtifact(exactSecond)!,
          createdAt: exactSecond.createdAt,
        },
      ],
      [
        {
          product: productFromAgentArtifact(oneNanosecondLater)!,
          createdAt: oneNanosecondLater.createdAt,
        },
      ],
    )

    expect(merged).toHaveLength(1)
    expect(merged[0]?.product.name).toBe('One nanosecond later')
    expect(merged[0]?.createdAt).toBe(oneNanosecondLater.createdAt)
  })

  test('does not promote a skipped same-offer cart line as the product freshness source', () => {
    const olderProduct = artifact({
      type: 'PRODUCT',
      stableKey: 'product-cart-freshness',
      canonicalProductKey: 'product-cart-freshness',
      createdAt: '2026-07-19T12:00:00Z',
      payloadJson: JSON.stringify(
        canonicalProduct('product-cart-freshness', 'offer-shared', 'Older product facts'),
      ),
    })
    const cart = artifact({
      type: 'CART',
      stableKey: 'cart:cart-freshness',
      messageId: 'message-cart-freshness',
      cartId: 'cart-freshness',
      createdAt: '2026-07-19T12:02:00Z',
      payloadJson: JSON.stringify({
        cartId: 'cart-freshness',
        merchantDomain: 'freshness.example',
        routingScopeKey: 'test:external:freshness',
      }),
    })
    const sameOfferLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:line-freshness',
      messageId: cart.messageId,
      cartId: cart.cartId,
      cartLineId: 'line-freshness',
      offerKey: 'offer-shared',
      ordinal: 2,
      createdAt: cart.createdAt,
      payloadJson: JSON.stringify({
        productId: 'product-cart-freshness',
        productTitle: 'Older product facts',
        productVariantId: 'variant-1',
        quantity: 1,
        offerKey: 'offer-shared',
      }),
    })
    const fresherProduct = artifact({
      type: 'PRODUCT',
      stableKey: 'product-cart-freshness',
      canonicalProductKey: 'product-cart-freshness',
      createdAt: '2026-07-19T12:01:00Z',
      payloadJson: JSON.stringify(
        canonicalProduct('product-cart-freshness', 'offer-current', 'Fresher product facts'),
      ),
    })

    const promoted = currentAgentProductSnapshots([olderProduct, cart, sameOfferLine])
    const merged = mergeAgentProductSnapshots(
      promoted,
      currentAgentProductSnapshots([fresherProduct]),
    )

    expect(promoted).toMatchObject([
      { product: { name: 'Older product facts' }, createdAt: olderProduct.createdAt },
    ])
    expect(merged).toMatchObject([
      { product: { name: 'Fresher product facts' }, createdAt: fresherProduct.createdAt },
    ])
  })

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

  test('repairs legacy raw domain artifacts with exact keys from sibling offer artifacts', () => {
    const legacy = legacyDomainProduct()
    const productArtifact = artifact({
      type: 'PRODUCT',
      stableKey: legacy.product.key,
      canonicalProductKey: legacy.product.key,
      offerKey: 'exact-offer-1',
      payloadJson: JSON.stringify(legacy.product),
    })
    const offerArtifact = artifact({
      type: 'OFFER',
      stableKey: 'exact-offer-1',
      canonicalProductKey: legacy.product.key,
      offerKey: 'exact-offer-1',
      payloadJson: JSON.stringify(legacy.offer),
    })

    const product = productFromAgentArtifact(productArtifact, [productArtifact, offerArtifact])

    expect(product).toMatchObject({
      id: 'legacy-product',
      name: 'Legacy grounded trail shoe',
      priceFrom: 145,
      selectedOptions: [{ name: 'Size', value: '42' }],
    })
    expect(product?.canonicalProduct?.recommendedOfferKey).toBe('exact-offer-1')
    expect(product?.canonicalProduct?.offers[0]).toMatchObject({
      key: 'exact-offer-1',
      identity: {
        provider: 'SHOPIFY',
        merchantIntegrationId: null,
        merchantScope: { type: 'EXTERNAL_MERCHANT' },
      },
      selectedOptions: [{ name: 'Size', value: '42' }],
      checkoutExperience: 'MEANT_MANAGED',
      commercialState: { authority: 'DISCOVERY_OBSERVATION' },
      provenance: [
        {
          provider: 'SHOPIFY',
          merchantIntegrationId: 'integration-1',
          discoverySource: { provider: 'SHOPIFY' },
        },
      ],
    })
  })

  test('preserves detail ranking and current commercial state while repairing legacy history', () => {
    const legacy = legacyDomainProduct('legacy-detail')
    const productArtifact = artifact({
      type: 'PRODUCT',
      stableKey: legacy.product.key,
      canonicalProductKey: legacy.product.key,
      offerKey: 'exact-offer-detail',
      payloadJson: JSON.stringify({
        product: legacy.product,
        recommendedOfferKey: 'exact-offer-detail',
        productRankingExplanation: { scoreBasisPoints: 9300 },
        personalization: {
          whyMeantForYou: 'Matches your durable trail preference.',
          matchedFilterIds: ['trail'],
          missedFilterIds: [],
        },
        offerRankingExplanations: {
          'exact-offer-detail': { scoreBasisPoints: 9000 },
        },
        commercialStates: {
          'exact-offer-detail': {
            authority: 'REHYDRATED_CURRENT',
            rehydrationStatus: 'FRESH',
          },
        },
      }),
    })
    const offerArtifact = artifact({
      type: 'OFFER',
      stableKey: 'exact-offer-detail',
      canonicalProductKey: legacy.product.key,
      offerKey: 'exact-offer-detail',
      payloadJson: JSON.stringify(legacy.offer),
    })

    const product = productFromAgentArtifact(productArtifact, [productArtifact, offerArtifact])

    expect(product).toMatchObject({
      match: 93,
      note: 'Matches your durable trail preference.',
      satisfies: ['trail'],
      commercialFactsAuthoritative: true,
    })
    expect(product?.canonicalProduct?.offers[0]).toMatchObject({
      rankingExplanation: { scoreBasisPoints: 9000 },
      commercialState: { authority: 'REHYDRATED_CURRENT', rehydrationStatus: 'FRESH' },
    })
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

  test('keeps the first active cart and all of its lines for a timestamp-tied merchant route', () => {
    const newestCart = artifact({
      type: 'CART',
      stableKey: 'cart:newest-active',
      messageId: 'message-active-carts',
      cartId: 'newest-active',
      ordinal: 1,
      payloadJson: JSON.stringify({
        cartId: 'newest-active',
        routingScopeKey: 'shopify:external:active-merchant',
      }),
    })
    const newestFirstLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:newest-active-first',
      messageId: newestCart.messageId,
      cartId: newestCart.cartId,
      cartLineId: 'newest-active-first',
      offerKey: 'offer-newest-first',
      ordinal: 2,
      payloadJson: JSON.stringify({
        cartLineId: 'newest-active-first',
        productTitle: 'Newest first product',
        quantity: 1,
        offerKey: 'offer-newest-first',
      }),
    })
    const newestSecondLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:newest-active-second',
      messageId: newestCart.messageId,
      cartId: newestCart.cartId,
      cartLineId: 'newest-active-second',
      offerKey: 'offer-newest-second',
      ordinal: 3,
      payloadJson: JSON.stringify({
        cartLineId: 'newest-active-second',
        productTitle: 'Newest second product',
        quantity: 1,
        offerKey: 'offer-newest-second',
      }),
    })
    const olderCart = artifact({
      type: 'CART',
      stableKey: 'cart:older-active',
      messageId: newestCart.messageId,
      cartId: 'older-active',
      ordinal: 4,
      payloadJson: JSON.stringify({
        cartId: 'older-active',
        routingScopeKey: 'shopify:external:active-merchant',
      }),
    })
    const olderLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:older-active',
      messageId: olderCart.messageId,
      cartId: olderCart.cartId,
      cartLineId: 'older-active-line',
      offerKey: 'offer-older',
      ordinal: 5,
      payloadJson: JSON.stringify({
        cartLineId: 'older-active-line',
        productTitle: 'Older product',
        quantity: 1,
        offerKey: 'offer-older',
      }),
    })

    const snapshot = latestCartSnapshotArtifacts([
      newestCart,
      newestFirstLine,
      newestSecondLine,
      olderCart,
      olderLine,
    ])

    expect(snapshot.map((item) => item.artifactId)).toEqual([
      newestCart.artifactId,
      newestFirstLine.artifactId,
      newestSecondLine.artifactId,
    ])
  })

  test('selects the lower canonical message id for equal-timestamp cart snapshots regardless of input order', () => {
    const selectedCart = artifact({
      type: 'CART',
      stableKey: 'cart:message-tie-selected',
      messageId: '00000000-0000-0000-0000-000000000011',
      cartId: 'message-tie-selected',
      payloadJson: JSON.stringify({
        cartId: 'message-tie-selected',
        routingScopeKey: 'shopify:external:message-tie-merchant',
      }),
    })
    const staleCart = artifact({
      type: 'CART',
      stableKey: 'cart:message-tie-stale',
      messageId: '00000000-0000-0000-0000-000000000012',
      cartId: 'message-tie-stale',
      payloadJson: JSON.stringify({
        cartId: 'message-tie-stale',
        routingScopeKey: 'shopify:external:message-tie-merchant',
      }),
    })
    const staleLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:message-tie-stale',
      messageId: staleCart.messageId,
      cartId: staleCart.cartId,
      cartLineId: 'message-tie-stale-line',
      offerKey: 'offer-message-tie-stale',
      ordinal: 2,
      payloadJson: JSON.stringify({
        cartLineId: 'message-tie-stale-line',
        productTitle: 'Stale product',
        quantity: 1,
        offerKey: 'offer-message-tie-stale',
      }),
    })

    for (const input of [
      [selectedCart, staleCart, staleLine],
      [staleLine, staleCart, selectedCart],
    ]) {
      expect(latestCartSnapshotArtifacts(input).map((item) => item.artifactId)).toEqual([
        selectedCart.artifactId,
      ])
    }
  })

  test('keeps cart snapshot ordering total when legacy artifacts omit comparator fields', () => {
    const selectedCart = artifact({
      type: 'CART',
      stableKey: 'cart:complete-ordering',
      artifactId: '00000000-0000-0000-0000-000000000021',
      messageId: '00000000-0000-0000-0000-000000000011',
      ordinal: 1,
      cartId: 'complete-ordering',
      payloadJson: JSON.stringify({
        cartId: 'complete-ordering',
        routingScopeKey: 'shopify:external:nullish-ordering-merchant',
      }),
    })
    const legacyCarts = [
      artifact({
        type: 'CART',
        stableKey: 'cart:missing-message',
        artifactId: '00000000-0000-0000-0000-000000000022',
        messageId: undefined,
        ordinal: 1,
        cartId: 'missing-message',
        payloadJson: JSON.stringify({
          cartId: 'missing-message',
          routingScopeKey: 'shopify:external:nullish-ordering-merchant',
        }),
      }),
      artifact({
        type: 'CART',
        stableKey: 'cart:missing-ordinal',
        artifactId: '00000000-0000-0000-0000-000000000023',
        messageId: selectedCart.messageId,
        ordinal: undefined,
        cartId: 'missing-ordinal',
        payloadJson: JSON.stringify({
          cartId: 'missing-ordinal',
          routingScopeKey: 'shopify:external:nullish-ordering-merchant',
        }),
      }),
      artifact({
        type: 'CART',
        stableKey: 'cart:missing-artifact-id',
        artifactId: undefined,
        messageId: selectedCart.messageId,
        ordinal: selectedCart.ordinal,
        cartId: 'missing-artifact-id',
        payloadJson: JSON.stringify({
          cartId: 'missing-artifact-id',
          routingScopeKey: 'shopify:external:nullish-ordering-merchant',
        }),
      }),
    ]

    for (const legacyCart of legacyCarts) {
      for (const input of [
        [selectedCart, legacyCart],
        [legacyCart, selectedCart],
      ]) {
        expect(latestCartSnapshotArtifacts(input).map((item) => item.artifactId)).toEqual([
          selectedCart.artifactId,
        ])
      }
    }

    const nullMessageCart = artifact({
      type: 'CART',
      stableKey: 'cart:null-message',
      artifactId: '00000000-0000-0000-0000-000000000031',
      messageId: null as unknown as string,
      ordinal: 1,
      cartId: 'null-message',
      payloadJson: JSON.stringify({
        cartId: 'null-message',
        routingScopeKey: 'shopify:external:missing-message-merchant',
      }),
    })
    const undefinedMessageCart = artifact({
      type: 'CART',
      stableKey: 'cart:undefined-message',
      artifactId: '00000000-0000-0000-0000-000000000032',
      messageId: undefined,
      ordinal: 1,
      cartId: 'undefined-message',
      payloadJson: JSON.stringify({
        cartId: 'undefined-message',
        routingScopeKey: 'shopify:external:missing-message-merchant',
      }),
    })

    for (const input of [
      [nullMessageCart, undefinedMessageCart],
      [undefinedMessageCart, nullMessageCart],
    ]) {
      expect(latestCartSnapshotArtifacts(input).map((item) => item.artifactId)).toEqual([
        nullMessageCart.artifactId,
      ])
    }
  })

  test('selects the nanosecond-later cart snapshot when instant precision differs', () => {
    const exactSecondCart = artifact({
      type: 'CART',
      stableKey: 'cart:precision-old',
      messageId: 'cart-message-precision-old',
      cartId: 'precision-old',
      createdAt: '2026-07-19T12:00:00Z',
      payloadJson: JSON.stringify({
        cartId: 'precision-old',
        routingScopeKey: 'shopify:external:precision-merchant',
      }),
    })
    const nanosecondLaterCart = artifact({
      type: 'CART',
      stableKey: 'cart:precision-new',
      messageId: 'cart-message-precision-new',
      cartId: 'precision-new',
      createdAt: '2026-07-19T12:00:00.000000001Z',
      payloadJson: JSON.stringify({
        cartId: 'precision-new',
        routingScopeKey: 'shopify:external:precision-merchant',
      }),
    })
    const nanosecondLaterLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:precision-new-line',
      messageId: nanosecondLaterCart.messageId,
      cartId: nanosecondLaterCart.cartId,
      cartLineId: 'precision-new-line',
      offerKey: 'offer-precision-new',
      createdAt: nanosecondLaterCart.createdAt,
      payloadJson: JSON.stringify({
        cartLineId: 'precision-new-line',
        productTitle: 'Nanosecond-later product',
        quantity: 1,
        offerKey: 'offer-precision-new',
      }),
    })

    const snapshot = latestCartSnapshotArtifacts([
      nanosecondLaterLine,
      exactSecondCart,
      nanosecondLaterCart,
    ])

    expect(snapshot.map((item) => item.artifactId)).toEqual([
      nanosecondLaterLine.artifactId,
      nanosecondLaterCart.artifactId,
    ])
  })

  test('applies cart snapshot cutoffs at nanosecond precision', () => {
    const exactSecondCart = artifact({
      type: 'CART',
      stableKey: 'cart:cutoff-old',
      messageId: 'cart-message-cutoff-old',
      cartId: 'cutoff-old',
      createdAt: '2026-07-19T12:00:00Z',
      payloadJson: JSON.stringify({
        cartId: 'cutoff-old',
        routingScopeKey: 'shopify:external:cutoff-merchant',
      }),
    })
    const nanosecondLaterCart = artifact({
      type: 'CART',
      stableKey: 'cart:cutoff-new',
      messageId: 'cart-message-cutoff-new',
      cartId: 'cutoff-new',
      createdAt: '2026-07-19T12:00:00.000000001Z',
      payloadJson: JSON.stringify({
        cartId: 'cutoff-new',
        routingScopeKey: 'shopify:external:cutoff-merchant',
      }),
    })

    const beforeNanosecondUpdate = latestCartSnapshotArtifacts(
      [exactSecondCart, nanosecondLaterCart],
      exactSecondCart.createdAt,
    )
    const includingNanosecondUpdate = latestCartSnapshotArtifacts(
      [exactSecondCart, nanosecondLaterCart],
      nanosecondLaterCart.createdAt,
    )

    expect(beforeNanosecondUpdate.map((item) => item.cartId)).toEqual(['cutoff-old'])
    expect(includingNanosecondUpdate.map((item) => item.cartId)).toEqual(['cutoff-new'])
  })

  test('reconstructs cart-only history without adding a discovery product carousel', () => {
    const cartMessage: AgentMessageProfile = {
      messageId: 'message-cart-only',
      runId: 'run-cart-only',
      sequenceNumber: 1,
      role: 'TOOL',
      contentKind: 'TOOL_RESULT',
      textContent: null,
      contentJson: '{}',
      correlationId: 'call-cart:get_active_carts',
      createdAt,
    }
    const cart = artifact({
      type: 'CART',
      stableKey: 'cart:cart-only',
      messageId: cartMessage.messageId,
      cartId: 'cart-only',
      payloadJson: JSON.stringify({
        cartId: 'cart-only',
        merchantDomain: 'cart-only.example',
        provider: 'SHOPIFY',
        routingScopeKey: 'shopify:external:merchant-cart-only',
        totalAmount: '258.00',
        subtotalAmount: '258.00',
        currency: 'USD',
        appliedCodes: [
          {
            type: 'DISCOUNT',
            code: ' SAVE20 ',
            label: 'Summer discount',
            applicable: true,
            amount: '20.00',
            currency: 'USD',
          },
          {
            type: 'GIFT_CARD',
            code: '1234',
            label: 'Gift card',
            applicable: true,
            amount: '10.00',
            currency: 'USD',
          },
        ],
        deliveryGroups: [
          {
            id: 'delivery-group-1',
            handle: 'shipment-1',
            deliveryOptions: [
              {
                handle: 'standard',
                title: 'Standard delivery',
                cost: { amount: '5.00', currency: 'USD' },
                deliveryEstimate: '3–5 days',
                selected: true,
              },
            ],
            selectedDeliveryOption: {
              handle: 'standard',
              title: 'Standard delivery',
              cost: { amount: '5.00', currency: 'USD' },
              deliveryEstimate: '3–5 days',
              selected: true,
            },
          },
        ],
      }),
    })
    const line = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:cart-only-line',
      messageId: cartMessage.messageId,
      cartId: 'cart-only',
      cartLineId: 'cart-only-line',
      offerKey: 'cart-only-offer',
      ordinal: 2,
      payloadJson: JSON.stringify({
        cartLineId: 'cart-only-line',
        productId: 'cart-only-product',
        productTitle: 'Recovered cart shoe',
        productVariantId: 'cart-only-variant',
        variantTitle: 'Blue / 42',
        quantity: 2,
        offerKey: 'cart-only-offer',
        totalAmount: '258.00',
        subtotalAmount: '258.00',
        currency: 'USD',
      }),
    })

    const products = productsFromAgentArtifacts([cart, line])
    const lines = cartItemsFromAgentArtifacts([cart, line], [])
    const replacements = cartStateReplacementsFromAgentArtifacts([cart, line], [])
    const blocks = blocksForAgentMessage(cartMessage, [cart, line], [cart, line], [])

    expect(products).toMatchObject([
      {
        id: 'cart-only-product',
        name: 'Recovered cart shoe',
        priceFrom: 129,
        rankingUnavailable: true,
        offers: [
          {
            offerKey: 'cart-only-offer',
            merchant: 'cart-only.example',
            price: 129,
            productVariantId: 'cart-only-variant',
          },
        ],
      },
    ])
    expect(lines).toMatchObject([
      {
        id: 'cart-only-product',
        cartId: 'cart-only',
        cartLineId: 'cart-only-line',
        offerKey: 'cart-only-offer',
        unitPriceAmount: '129',
        deliveryGroups: [
          {
            id: 'delivery-group-1',
            deliveryOptions: [
              {
                handle: 'standard',
                title: 'Standard delivery',
                cost: { amount: '5.00', currency: 'USD' },
                deliveryEstimate: '3–5 days',
                selected: true,
              },
            ],
            selectedDeliveryOption: {
              handle: 'standard',
              cost: { amount: '5.00', currency: 'USD' },
            },
          },
        ],
      },
    ])
    expect(replacements).toMatchObject([
      {
        merchantKey: 'shopify:external:merchant-cart-only',
        snapshot: {
          cartId: 'cart-only',
          totalAmount: 258,
          appliedCodes: [
            {
              type: 'DISCOUNT',
              code: 'SAVE20',
              displayCode: 'SAVE20',
              amount: 20,
            },
            {
              type: 'GIFT_CARD',
              code: null,
              displayCode: '1234',
              amount: 10,
            },
          ],
        },
        lines: [{ id: 'cart-only-product', offerKey: 'cart-only-offer' }],
      },
    ])
    expect(blocks.map((block) => block.type)).toEqual(['cart'])
  })

  test('fails closed instead of replacing a cart from an incomplete line projection', () => {
    const cart = artifact({
      type: 'CART',
      stableKey: 'cart:incomplete',
      messageId: 'message-incomplete',
      cartId: 'cart-incomplete',
      payloadJson: JSON.stringify({
        cartId: 'cart-incomplete',
        merchantDomain: 'incomplete.example',
        routingScopeKey: 'shopify:external:merchant-incomplete',
      }),
    })
    const validLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:valid',
      messageId: cart.messageId,
      cartId: cart.cartId,
      cartLineId: 'valid-line',
      offerKey: 'valid-offer',
      ordinal: 2,
      payloadJson: JSON.stringify({
        productId: 'valid-product',
        productTitle: 'Valid product',
        quantity: 1,
        offerKey: 'valid-offer',
      }),
    })
    const incompleteLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:incomplete',
      messageId: cart.messageId,
      cartId: cart.cartId,
      cartLineId: 'incomplete-line',
      ordinal: 3,
      payloadJson: JSON.stringify({
        productId: 'incomplete-product',
        productTitle: 'Incomplete product',
        quantity: 1,
      }),
    })

    expect(cartItemsFromAgentArtifacts([cart, validLine, incompleteLine], [])).toHaveLength(1)
    expect(cartStateReplacementsFromAgentArtifacts([cart, validLine, incompleteLine], [])).toEqual(
      [],
    )
  })

  test('keeps only the newest cart for each merchant partition in cart and checkout state', () => {
    const oldCart = artifact({
      type: 'CART',
      stableKey: 'cart:old-cart',
      messageId: 'message-old-cart',
      cartId: 'old-cart',
      payloadJson: JSON.stringify({
        cartId: 'old-cart',
        merchantDomain: 'same-merchant.example',
        routingScopeKey: 'shopify:external:same-merchant',
      }),
    })
    const oldLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:old-line',
      messageId: oldCart.messageId,
      cartId: oldCart.cartId,
      cartLineId: 'old-line',
      offerKey: 'old-offer',
      ordinal: 2,
      payloadJson: JSON.stringify({
        productId: 'old-product',
        productTitle: 'Obsolete product',
        quantity: 1,
        offerKey: 'old-offer',
      }),
    })
    const newCart = artifact({
      type: 'CART',
      stableKey: 'cart:new-cart',
      messageId: 'message-new-cart',
      cartId: 'new-cart',
      createdAt: '2026-07-18T12:02:00Z',
      payloadJson: JSON.stringify({
        cartId: 'new-cart',
        merchantDomain: 'same-merchant.example',
        routingScopeKey: 'shopify:external:same-merchant',
      }),
    })
    const newLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:new-line',
      messageId: newCart.messageId,
      cartId: newCart.cartId,
      cartLineId: 'new-line',
      offerKey: 'new-offer',
      ordinal: 2,
      createdAt: newCart.createdAt,
      payloadJson: JSON.stringify({
        productId: 'new-product',
        productTitle: 'Current product',
        quantity: 1,
        offerKey: 'new-offer',
      }),
    })
    const checkoutMessage: AgentMessageProfile = {
      messageId: 'message-checkout-partition',
      runId: 'run-checkout-partition',
      sequenceNumber: 3,
      role: 'TOOL',
      contentKind: 'TOOL_RESULT',
      textContent: null,
      contentJson: '{}',
      correlationId: 'call-checkout:prepare_checkout',
      createdAt: '2026-07-18T12:03:00Z',
    }
    const checkouts = [oldCart, newCart].map((cart, index) =>
      artifact({
        type: 'CHECKOUT',
        stableKey: `checkout:${cart.cartId}`,
        messageId: checkoutMessage.messageId,
        cartId: cart.cartId,
        checkoutAttemptId: `attempt-${index + 1}`,
        ordinal: index + 1,
        createdAt: checkoutMessage.createdAt,
        payloadJson: JSON.stringify({ cartId: cart.cartId }),
      }),
    )
    const cartArtifacts = [oldCart, oldLine, newCart, newLine]
    const allArtifacts = [...cartArtifacts, ...checkouts]

    expect(latestCartSnapshotArtifacts(cartArtifacts).map((item) => item.cartId)).toEqual([
      'new-cart',
      'new-cart',
    ])
    expect(cartStateReplacementsFromAgentArtifacts(cartArtifacts, [])).toMatchObject([
      {
        merchantKey: 'shopify:external:same-merchant',
        snapshot: { cartId: 'new-cart' },
        lines: [{ id: 'new-product', cartId: 'new-cart' }],
      },
    ])
    expect(blocksForAgentMessage(checkoutMessage, checkouts, allArtifacts, [])[0]).toMatchObject({
      type: 'checkout',
      merchantCount: 1,
      lines: [{ id: 'new-product', cartId: 'new-cart' }],
    })
  })

  test('keeps the newest snapshot when a cart gains authoritative routing metadata', () => {
    const oldCart = artifact({
      type: 'CART',
      stableKey: 'cart:transition:legacy',
      messageId: 'message-transition-old',
      cartId: 'transition-cart',
      payloadJson: JSON.stringify({
        cartId: 'transition-cart',
        provider: 'SHOPIFY',
        merchantId: 'merchant-transition',
      }),
    })
    const oldLine = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:transition-old',
      messageId: oldCart.messageId,
      cartId: oldCart.cartId,
      cartLineId: 'transition-old-line',
      offerKey: 'transition-old-offer',
      ordinal: 2,
      payloadJson: JSON.stringify({
        productId: 'transition-old-product',
        productTitle: 'Removed product',
        quantity: 1,
        offerKey: 'transition-old-offer',
      }),
    })
    const newCart = artifact({
      type: 'CART',
      stableKey: 'cart:transition:current',
      messageId: 'message-transition-new',
      cartId: 'transition-cart',
      createdAt: '2026-07-18T12:02:00Z',
      payloadJson: JSON.stringify({
        cartId: 'transition-cart',
        provider: 'SHOPIFY',
        merchantId: 'merchant-transition',
        routingScopeKey: 'shopify:integration:transition',
        lines: [],
      }),
    })

    expect(latestCartSnapshotArtifacts([oldCart, oldLine, newCart])).toEqual([newCart])
    expect(cartStateReplacementsFromAgentArtifacts([oldCart, oldLine, newCart], [])).toMatchObject([
      {
        snapshot: { cartId: 'transition-cart' },
        lines: [],
      },
    ])
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
    expect(messages[1]?.blocks?.[0]).toEqual({
      type: 'text',
      text: 'I found these trail shoes:',
    })
    expect(messages[1]?.blocks?.[1]).toMatchObject({
      type: 'products',
      query: 'Find trail shoes',
      products: [{ id: 'product-1' }],
    })
  })

  test('replaces enumerated catalog prose with one concise lead-in and product cards', () => {
    const user: AgentMessageProfile = {
      messageId: 'message-user',
      runId: 'run-1',
      sequenceNumber: 1,
      role: 'USER',
      contentKind: 'TEXT',
      textContent: 'I am looking for some cool sunglasses',
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
    const assistant: AgentMessageProfile = {
      messageId: 'message-assistant',
      runId: 'run-1',
      sequenceNumber: 3,
      role: 'ASSISTANT',
      contentKind: 'TEXT',
      textContent:
        'I found these sunglasses: Fashion Square Vintage Polarized Sunglasses for $9.00 Classic Original for $59.00. Do any of these look interesting?',
      contentJson: null,
      correlationId: null,
      createdAt,
    }
    const product = canonicalProduct('product-1')
    const conversation: AgentConversationDetailProfile = {
      conversationId: 'conversation-1',
      title: 'Sunglasses',
      status: 'ACTIVE',
      activeMissionId: null,
      latestSequence: 3,
      createdAt,
      updatedAt: createdAt,
      rollingSummary: null,
      summaryVersion: 0,
      latestCursor: 5,
      messages: [user, tool, assistant],
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

    expect(messages).toHaveLength(2)
    expect(messages[1]).toMatchObject({
      id: 'message-assistant',
      role: 'ai',
      blocks: [
        { type: 'text', text: 'I found these sunglasses:' },
        {
          type: 'products',
          query: 'I am looking for some cool sunglasses',
          products: [{ id: 'product-1' }],
        },
      ],
    })
    expect(messages[1]?.blocks?.[0]).not.toMatchObject({ text: expect.stringContaining('$') })
    expect(messages[1]?.blocks?.[0]).not.toMatchObject({
      text: expect.stringContaining('Fashion Square'),
    })
  })

  test('replaces legacy canonical keys in product action messages with trusted product names', () => {
    const canonicalKey = 'product_v3_eb3f7d21b2460038c961b236c41b69fb5817cee1c87113ee'
    const action: AgentMessageProfile = {
      messageId: 'message-action',
      runId: null,
      sequenceNumber: 1,
      role: 'USER_ACTION',
      contentKind: 'TEXT',
      textContent: `Pinned product ${canonicalKey}.`,
      contentJson: null,
      correlationId: 'direct:pin_product',
      createdAt,
    }
    const product = canonicalProduct(canonicalKey, 'offer-1', 'Fashion Square Vintage Sunglasses')
    const conversation: AgentConversationDetailProfile = {
      conversationId: 'conversation-action',
      title: 'Sunglasses',
      status: 'ACTIVE',
      activeMissionId: null,
      latestSequence: 1,
      createdAt,
      updatedAt: createdAt,
      rollingSummary: null,
      summaryVersion: 0,
      latestCursor: 1,
      messages: [action],
      artifacts: [
        artifact({
          messageId: action.messageId,
          type: 'PRODUCT',
          stableKey: canonicalKey,
          canonicalProductKey: canonicalKey,
          payloadJson: JSON.stringify(product),
        }),
        artifact({
          messageId: action.messageId,
          type: 'PRODUCT_STATE',
          stableKey: `product-state:${canonicalKey}`,
          canonicalProductKey: canonicalKey,
          label: canonicalKey,
          payloadJson: JSON.stringify({ canonicalProductKey: canonicalKey, pinned: true }),
        }),
      ],
    }

    const messages = discoverMessagesFromAgentConversation(conversation, [])

    expect(messages[0]).toMatchObject({
      role: 'you',
      text: 'Pinned product Fashion Square Vintage Sunglasses.',
    })
    expect(messages[0]?.text).not.toContain('product_v3_')
  })

  test('maps the same exact product contract through comparison, cart, and checkout blocks', () => {
    const comparisonMessage: AgentMessageProfile = {
      messageId: 'message-comparison',
      runId: 'run-compare',
      sequenceNumber: 1,
      role: 'TOOL',
      contentKind: 'TOOL_RESULT',
      textContent: null,
      contentJson: '{}',
      correlationId: 'call-compare:compare_products',
      createdAt,
    }
    const cartMessage: AgentMessageProfile = {
      ...comparisonMessage,
      messageId: 'message-cart',
      runId: 'run-cart',
      sequenceNumber: 2,
      correlationId: 'call-cart:prepare_carts',
    }
    const checkoutMessage: AgentMessageProfile = {
      ...comparisonMessage,
      messageId: 'message-checkout',
      runId: 'run-checkout',
      sequenceNumber: 3,
      correlationId: 'call-checkout:prepare_checkout',
      createdAt: '2026-07-18T12:02:00Z',
    }
    const first = canonicalProduct('product-1', 'offer-1', 'Grounded trail shoe')
    const second = canonicalProduct('product-2', 'offer-2', 'Responsive road shoe')
    const productArtifacts = [
      artifact({
        type: 'PRODUCT',
        stableKey: first.key,
        messageId: comparisonMessage.messageId,
        canonicalProductKey: first.key,
        offerKey: first.recommendedOfferKey,
        payloadJson: JSON.stringify(first),
      }),
      artifact({
        type: 'PRODUCT',
        stableKey: second.key,
        messageId: comparisonMessage.messageId,
        canonicalProductKey: second.key,
        offerKey: second.recommendedOfferKey,
        ordinal: 2,
        payloadJson: JSON.stringify(second),
      }),
    ]
    const comparisonArtifact = artifact({
      type: 'COMPARISON',
      stableKey: 'comparison:product-1|product-2',
      messageId: comparisonMessage.messageId,
      payloadJson: '{}',
    })
    const cartArtifact = artifact({
      type: 'CART',
      stableKey: 'cart:cart-1',
      messageId: cartMessage.messageId,
      cartId: 'cart-1',
      createdAt: '2026-07-18T12:01:00Z',
      payloadJson: JSON.stringify({
        cartId: 'cart-1',
        merchantDomain: 'trail.example',
        provider: 'SHOPIFY',
        routingScopeKey: 'shopify:external:merchant-1',
        remoteCartId: 'remote-cart-1',
        checkoutUrl: 'https://trail.example/checkout',
        totalAmount: '258.00',
        subtotalAmount: '258.00',
        currency: 'USD',
      }),
    })
    const cartLineArtifact = artifact({
      type: 'CART_LINE',
      stableKey: 'cart-line:line-1',
      messageId: cartMessage.messageId,
      cartId: 'cart-1',
      cartLineId: 'line-1',
      offerKey: 'offer-1',
      createdAt: '2026-07-18T12:01:00Z',
      payloadJson: JSON.stringify({
        cartLineId: 'line-1',
        remoteCartLineId: 'remote-line-1',
        productTitle: first.title,
        productVariantId: 'variant-1',
        quantity: 2,
        offerKey: 'offer-1',
        totalAmount: '258.00',
        subtotalAmount: '258.00',
        currency: 'USD',
      }),
    })
    const checkoutArtifact = artifact({
      type: 'CHECKOUT',
      stableKey: 'checkout:attempt-1',
      messageId: checkoutMessage.messageId,
      cartId: 'cart-1',
      checkoutAttemptId: 'attempt-1',
      createdAt: checkoutMessage.createdAt,
      payloadJson: JSON.stringify({
        cartId: 'cart-1',
        checkoutAttemptId: 'attempt-1',
        nextAction: 'OPEN_EMBEDDED_CHECKOUT',
      }),
    })
    const allArtifacts = [
      ...productArtifacts,
      comparisonArtifact,
      cartArtifact,
      cartLineArtifact,
      checkoutArtifact,
    ]

    const comparison = blocksForAgentMessage(
      comparisonMessage,
      [...productArtifacts, comparisonArtifact],
      allArtifacts,
      [],
    )
    const cart = blocksForAgentMessage(
      cartMessage,
      [cartArtifact, cartLineArtifact],
      allArtifacts,
      [],
    )
    const checkout = blocksForAgentMessage(checkoutMessage, [checkoutArtifact], allArtifacts, [])
    const replacements = cartStateReplacementsFromAgentArtifacts(
      [cartArtifact, cartLineArtifact],
      productArtifacts.flatMap((item) => {
        const product = productFromAgentArtifact(item)
        return product ? [product] : []
      }),
    )

    expect(comparison[0]).toMatchObject({
      type: 'minicompare',
      products: [{ id: 'product-1' }, { id: 'product-2' }],
    })
    expect(cart[0]).toMatchObject({
      type: 'cart',
      lines: [
        {
          id: 'product-1',
          qty: 2,
          cartId: 'cart-1',
          cartLineId: 'line-1',
          offerKey: 'offer-1',
          merchantScopeKey: 'shopify:external:merchant-1',
          unitPriceAmount: '129',
        },
      ],
    })
    expect(checkout[0]).toMatchObject({
      type: 'checkout',
      merchantCount: 1,
      lines: [{ id: 'product-1', cartId: 'cart-1', cartLineId: 'line-1' }],
    })
    expect(replacements).toMatchObject([
      {
        merchantKey: 'shopify:external:merchant-1',
        routingScopeKey: 'shopify:external:merchant-1',
        snapshot: {
          cartId: 'cart-1',
          remoteCartId: 'remote-cart-1',
          subtotalAmount: 258,
          totalAmount: 258,
          currency: 'USD',
        },
        lines: [{ id: 'product-1', cartId: 'cart-1', offerKey: 'offer-1' }],
      },
    ])
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
