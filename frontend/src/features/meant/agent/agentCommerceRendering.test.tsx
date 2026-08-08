import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { CartProfile } from '../../../lib/apiClient'
import { InlineCartBlock } from '../chat/blocks/InlineCartBlock'
import { InlineCheckoutBlock } from '../chat/blocks/InlineCheckoutBlock'
import { InlineMiniCompareBlock } from '../chat/blocks/InlineMiniCompareBlock'
import { DustWrap } from '../chat/DiscoverChatMessageRow'
import { DiscoverProductBatch } from '../chat/DiscoverProductBatch'
import type { DiscoverChatBlock } from '../chat/types'
import type { MerchantCartStateReplacement } from '../cart/types'
import { liveCheckoutGroupFor } from '../cart/checkoutGroupResolution'
import { resolveLiveCartItem } from '../cart/cartPartition'
import { ProductReviewsPanel } from '../product/ProductReviewsPanel'
import type { CartItem, Product } from '../types'
import { cartGroups, cartLines } from '../utils'
import { commerceActionQueueFor, SerializedAgentActionQueue } from './actionQueue'
import { cartStateReplacementFromCartProfile } from './artifactMapping'
import {
  agentCartProvenanceAfterReplacements,
  agentCartPartitionFingerprints,
  agentCartReplacementUnchangedSinceSubmission,
  agentCartReplacementSupersededByCurrentPartition,
  agentCartStateFingerprint,
  isTerminalAgentRunStatus,
  liveCartQuantityAfterDelta,
  optimisticAgentCartQuantityChange,
  rebasePendingAgentCartRunsAfterReplacements,
  registerPendingAgentCartRun,
  settlePendingAgentCartRun,
} from './cartSync'

function product(id: string, name: string, offerKey: string, price: number): Product {
  return {
    id,
    name,
    brand: 'Meant Test',
    category: 'Running shoes',
    tone: '#e7ebef',
    match: 92,
    priceFrom: price,
    priceCurrency: 'USD',
    listPrice: null,
    merchants: 1,
    satisfies: [],
    misses: [],
    note: 'Matches the current running-shoe search.',
    pros: [],
    cons: [],
    review: { score: 4.7, count: 120, insight: 'Well reviewed.' },
    offers: [
      {
        offerKey,
        merchant: 'Running Shop',
        price,
        priceCurrency: 'USD',
        delivery: 'Delivery estimate available',
        productVariantId: `${id}-variant`,
        available: true,
      },
    ],
  }
}

const first = product('product-1', 'Grounded trail shoe', 'offer-1', 129)
const second = product('product-2', 'Responsive road shoe', 'offer-2', 149)
const cart: CartItem[] = [
  {
    id: first.id,
    merchant: 'Running Shop',
    qty: 1,
    cartId: 'cart-1',
    cartLineId: 'line-1',
    remoteCartLineId: 'remote-line-1',
    productVariantId: 'product-1-variant',
    offerKey: 'offer-1',
    checkoutUrl: 'https://running.example/checkout',
    unitPriceAmount: '129',
    orderCurrency: 'USD',
  },
]

describe('agent commerce artifacts reuse the established components', () => {
  test('keeps the existing small remove-message cross available when agent rows opt in', () => {
    const markup = renderToStaticMarkup(
      <DustWrap side="meant" saved={false} deletable onGone={() => undefined}>
        <span>Agent message</span>
      </DustWrap>,
    )

    expect(markup).toContain('aria-label="Delete message"')
    expect(markup).toContain('mt-tool-del')
  })

  test('uses the existing dust effect for a controlled automatic removal', () => {
    const markup = renderToStaticMarkup(
      <DustWrap side="you" saved={false} deletable removing onGone={() => undefined}>
        <span>Pin notice</span>
      </DustWrap>,
    )

    expect(markup).toContain('mt-dustwrap side-you dusting')
    expect(markup).toContain('mt-dust-svg')
    expect(markup).not.toContain('aria-label="Delete message"')
  })

  test('treats cancelled and failed cart runs as terminal synchronization outcomes', () => {
    expect(isTerminalAgentRunStatus('CANCELLED')).toBe(true)
    expect(isTerminalAgentRunStatus('FAILED')).toBe(true)
    expect(isTerminalAgentRunStatus('WAITING_FOR_USER')).toBe(true)
    expect(isTerminalAgentRunStatus('COMPLETED')).toBe(true)
    expect(isTerminalAgentRunStatus('RUNNING')).toBe(false)
  })

  test('guards delayed run reconciliation with the submitted live-cart revision', () => {
    const merchantACart: CartItem = {
      ...cart[0]!,
      merchantId: 'merchant-a',
      merchantIntegrationId: 'integration-a',
      merchantDomain: 'shared.example',
      provider: 'shopify',
    }
    const merchantBCart: CartItem = {
      ...cart[0]!,
      id: second.id,
      offerKey: 'offer-2',
      cartId: 'cart-2',
      cartLineId: 'line-2',
      merchantId: 'merchant-b',
      merchantIntegrationId: 'integration-b',
      merchantDomain: 'shared.example',
      provider: 'shopify',
    }
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'merchant-a',
      merchantId: 'merchant-a',
      merchantDomain: 'shared.example',
      provider: 'shopify',
      merchantIntegrationId: 'integration-a',
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: {
        merchantKey: 'merchant-a',
        merchant: 'Running Shop',
        cartId: 'cart-1',
        remoteCartId: null,
        checkoutUrl: null,
        continueUrl: null,
        subtotalAmount: 129,
        totalAmount: 129,
        currency: 'USD',
        appliedCodes: [],
      },
      lines: [merchantACart],
    }
    const submitted = agentCartPartitionFingerprints([merchantACart, merchantBCart], {})

    expect(
      agentCartReplacementUnchangedSinceSubmission(
        replacement,
        submitted,
        agentCartPartitionFingerprints([merchantACart, { ...merchantBCart, qty: 2 }], {}),
      ),
    ).toBe(true)
    expect(
      agentCartReplacementUnchangedSinceSubmission(
        replacement,
        submitted,
        agentCartPartitionFingerprints([{ ...merchantACart, qty: 2 }, merchantBCart], {}),
      ),
    ).toBe(false)
    expect(agentCartPartitionFingerprints([merchantBCart, merchantACart], {})).toEqual(submitted)
  })

  test('includes only the targeted merchant snapshot in its partition revision', () => {
    const line = { ...cart[0]!, merchantId: 'merchant-a' }
    const initialSnapshot = {
      merchantKey: 'merchant-a',
      merchant: 'Running Shop',
      cartId: 'cart-1',
      remoteCartId: null,
      checkoutUrl: null,
      continueUrl: null,
      subtotalAmount: 129,
      totalAmount: 129,
      currency: 'USD',
      appliedCodes: [],
    }
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'merchant-a',
      merchantId: 'merchant-a',
      merchantDomain: null,
      provider: null,
      merchantIntegrationId: null,
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: initialSnapshot,
      lines: [line],
    }
    const submitted = agentCartPartitionFingerprints([line], { 'merchant-a': initialSnapshot })
    const current = agentCartPartitionFingerprints([line], {
      'merchant-a': {
        ...initialSnapshot,
        appliedCodes: [
          {
            type: 'DISCOUNT',
            code: 'RUN10',
            displayCode: 'RUN10',
            label: null,
            applicable: true,
            amount: 10,
            currency: 'USD',
          },
        ],
      },
    })

    expect(agentCartReplacementUnchangedSinceSubmission(replacement, submitted, current)).toBe(
      false,
    )
  })

  test('allows a genuinely new partition but protects one created after submission', () => {
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'new-merchant',
      merchantId: 'new-merchant',
      merchantDomain: 'new.example',
      provider: 'shopify',
      merchantIntegrationId: 'new-integration',
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: {
        merchantKey: 'new-merchant',
        merchant: 'New Merchant',
        cartId: 'new-cart',
        remoteCartId: null,
        checkoutUrl: null,
        continueUrl: null,
        subtotalAmount: 20,
        totalAmount: 20,
        currency: 'USD',
        appliedCodes: [],
      },
      lines: [],
    }
    const submitted = agentCartPartitionFingerprints([], {})

    expect(
      agentCartReplacementUnchangedSinceSubmission(
        replacement,
        submitted,
        agentCartPartitionFingerprints([], {}),
      ),
    ).toBe(true)
    expect(
      agentCartReplacementUnchangedSinceSubmission(
        replacement,
        submitted,
        agentCartPartitionFingerprints(
          [
            {
              ...cart[0]!,
              merchant: 'New Merchant',
              merchantId: 'new-merchant',
              merchantIntegrationId: 'new-integration',
              merchantDomain: 'new.example',
              provider: 'shopify',
              cartId: 'user-cart',
            },
          ],
          {},
        ),
      ),
    ).toBe(false)
  })

  test('preserves the submitted cart revision when a running turn is rediscovered', () => {
    const revisionBefore = {
      aliases: { 'merchant:merchant-a': 'partition-a' },
      revisions: { 'partition-a': 'revision-before' },
    }
    const pending = registerPendingAgentCartRun({}, 'run-1', 'conversation-1', revisionBefore)
    expect(
      registerPendingAgentCartRun(pending, 'run-1', 'conversation-1', {
        aliases: { 'merchant:merchant-a': 'partition-a' },
        revisions: { 'partition-a': 'revision-after' },
      }),
    ).toBe(pending)
    expect(pending['run-1']?.cartFingerprints).toEqual(revisionBefore)
  })

  test('fails closed when a running turn is rediscovered without its submitted cart revision', () => {
    const pending = registerPendingAgentCartRun({}, 'run-unknown', 'conversation-1', undefined)
    expect(pending['run-unknown']?.cartFingerprints).toBeUndefined()
    expect(pending['run-unknown']?.cartFingerprint).toBeUndefined()
  })

  test('signals one settlement only for the matching pending run', () => {
    const pending = registerPendingAgentCartRun({}, 'run-1', 'conversation-1', undefined)

    expect(settlePendingAgentCartRun(pending, 'run-1', 'conversation-other')).toBe(pending)

    const settled = settlePendingAgentCartRun(pending, 'run-1', 'conversation-1')
    expect(settled).not.toBe(pending)
    expect(settled['run-1']).toBeUndefined()
    expect(settlePendingAgentCartRun(settled, 'run-1', 'conversation-1')).toBe(settled)
  })

  test('rebases only the safely replaced partition for another pending run', () => {
    const merchantA = {
      ...cart[0]!,
      merchantId: 'merchant-a',
      merchantIntegrationId: 'integration-a',
    }
    const merchantB = {
      ...cart[0]!,
      id: second.id,
      offerKey: 'offer-2',
      cartId: 'cart-2',
      cartLineId: 'line-2',
      merchantId: 'merchant-b',
      merchantIntegrationId: 'integration-b',
    }
    const replacementLine = { ...merchantA, qty: 2 }
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'merchant-a',
      merchantId: 'merchant-a',
      merchantDomain: null,
      provider: null,
      merchantIntegrationId: 'integration-a',
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: {
        merchantKey: 'merchant-a',
        merchant: 'Running Shop',
        cartId: 'cart-1',
        remoteCartId: null,
        checkoutUrl: null,
        continueUrl: null,
        subtotalAmount: 258,
        totalAmount: 258,
        currency: 'USD',
        appliedCodes: [],
      },
      lines: [replacementLine],
    }
    const submitted = agentCartPartitionFingerprints([merchantA, merchantB], {})
    const pending = registerPendingAgentCartRun(
      registerPendingAgentCartRun(
        registerPendingAgentCartRun({}, 'run-1', 'conversation-1', submitted),
        'run-other',
        'conversation-other',
        submitted,
      ),
      'run-2',
      'conversation-1',
      submitted,
    )
    const beforeReplacement = agentCartPartitionFingerprints(
      [merchantA, { ...merchantB, qty: 3 }],
      {},
    )
    const afterReplacement = agentCartPartitionFingerprints(
      [replacementLine, { ...merchantB, qty: 3 }],
      { 'merchant-a': replacement.snapshot },
    )
    const rebased = rebasePendingAgentCartRunsAfterReplacements(
      pending,
      'run-1',
      'conversation-1',
      [replacement],
      beforeReplacement,
      afterReplacement,
    )
    const runTwoRevision = rebased['run-2']?.cartFingerprints

    expect(runTwoRevision).toBeDefined()
    expect(rebased['run-other']).toBe(pending['run-other'])
    expect(
      agentCartReplacementUnchangedSinceSubmission(replacement, runTwoRevision, afterReplacement),
    ).toBe(true)
    const submittedMerchantBPartition = submitted.aliases['merchant:merchant-b']
    const rebasedMerchantBPartition = runTwoRevision?.aliases['merchant:merchant-b']
    expect(rebasedMerchantBPartition).toBe(submittedMerchantBPartition)
    expect(runTwoRevision?.revisions[rebasedMerchantBPartition!]).toBe(
      submitted.revisions[submittedMerchantBPartition!],
    )
    expect(runTwoRevision?.revisions[rebasedMerchantBPartition!]).not.toBe(
      afterReplacement.revisions[afterReplacement.aliases['merchant:merchant-b']!],
    )
  })

  test('records agent provenance across conversations without trusting a local edit', () => {
    const submitted = agentCartPartitionFingerprints(cart, {})
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'running-shop',
      merchantId: null,
      merchantDomain: 'running.example',
      provider: 'test',
      merchantIntegrationId: null,
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: {
        merchantKey: 'running-shop',
        merchant: 'Running Shop',
        cartId: 'cart-1',
        remoteCartId: 'remote-cart-1',
        checkoutUrl: null,
        continueUrl: null,
        subtotalAmount: 258,
        totalAmount: 258,
        currency: 'USD',
        appliedCodes: [],
      },
      lines: [{ ...cart[0]!, qty: 2, merchantDomain: 'running.example' }],
    }
    const after = agentCartPartitionFingerprints(replacement.lines, {
      'running-shop': replacement.snapshot,
    })
    const provenance = agentCartProvenanceAfterReplacements(null, [replacement], after)
    const laterRunReplacement = {
      ...replacement,
      lines: [{ ...replacement.lines[0]!, qty: 3 }],
    }

    expect(
      agentCartReplacementUnchangedSinceSubmission(laterRunReplacement, provenance, after),
    ).toBe(true)
    expect(
      agentCartReplacementUnchangedSinceSubmission(
        replacement,
        provenance,
        agentCartPartitionFingerprints([{ ...replacement.lines[0]!, qty: 3 }], {
          'running-shop': replacement.snapshot,
        }),
      ),
    ).toBe(false)
    expect(
      agentCartReplacementUnchangedSinceSubmission(laterRunReplacement, submitted, after),
    ).toBe(false)
  })

  test('projects every line from the authoritative cart read into the replacement', () => {
    const fallback: MerchantCartStateReplacement = {
      merchantKey: 'running-scope',
      merchantId: 'merchant-1',
      merchantDomain: 'running.example',
      provider: 'test',
      merchantIntegrationId: 'integration-1',
      externalMerchantId: 'external-1',
      routingScopeKey: 'running-scope',
      snapshot: {
        merchantKey: 'running-scope',
        merchant: 'sollys-online-grocery.myshopify.com',
        cartId: 'cart-1',
        remoteCartId: 'remote-cart-1',
        checkoutUrl: null,
        continueUrl: null,
        subtotalAmount: 129,
        totalAmount: 129,
        currency: 'USD',
        appliedCodes: [],
      },
      lines: cart,
    }
    const profile = {
      cartId: 'cart-1',
      merchantId: 'merchant-1',
      merchantDomain: 'nycfactory.com',
      provider: 'test',
      merchantIntegrationId: 'integration-1',
      externalMerchantId: 'external-1',
      routingScopeKey: 'running-scope',
      remoteCartId: 'remote-cart-1',
      totalQuantity: 3,
      totalAmount: '407',
      subtotalAmount: '407',
      currency: 'USD',
      active: true,
      createdAt: '2026-07-23T10:00:00Z',
      updatedAt: '2026-07-23T10:01:00Z',
      refreshedAt: '2026-07-23T10:01:00Z',
      appliedCodes: [],
      deliveryGroups: [],
      messages: [],
      lines: [
        {
          cartLineId: 'line-1',
          remoteCartLineId: 'remote-line-1',
          productId: 'remote-product-1',
          productTitle: first.name,
          productVariantId: 'product-1-variant',
          quantity: 2,
          subtotalAmount: '258',
          totalAmount: '258',
          currency: 'USD',
          offerKey: 'offer-1',
          createdAt: '2026-07-23T10:00:00Z',
          updatedAt: '2026-07-23T10:01:00Z',
        },
        {
          cartLineId: 'line-2',
          remoteCartLineId: 'remote-line-2',
          productId: 'remote-product-2',
          productTitle: second.name,
          productVariantId: 'product-2-variant',
          quantity: 1,
          subtotalAmount: '149',
          totalAmount: '149',
          currency: 'USD',
          offerKey: 'offer-2',
          createdAt: '2026-07-23T10:01:00Z',
          updatedAt: '2026-07-23T10:01:00Z',
        },
      ],
    } as unknown as CartProfile

    const replacement = cartStateReplacementFromCartProfile(
      profile,
      [first, second],
      cart,
      fallback,
    )

    expect(replacement?.lines).toHaveLength(2)
    expect(replacement?.lines.map((line) => [line.id, line.qty, line.cartLineId])).toEqual([
      ['product-1', 2, 'line-1'],
      ['product-2', 1, 'line-2'],
    ])
    expect(replacement?.snapshot.totalAmount).toBe(407)
    expect(replacement?.snapshot.merchantOrigin).toBe('nycfactory.com')
    expect(replacement?.lines.every((line) => line.merchantOrigin === 'nycfactory.com')).toBe(true)
    expect(
      replacement?.lines.every((line) => line.merchant === 'sollys-online-grocery.myshopify.com'),
    ).toBe(true)
  })

  test('recognizes an inactive artifact cart superseded by the live cart in the same scope', () => {
    const staleReplacement: MerchantCartStateReplacement = {
      merchantKey: 'running-scope',
      merchantId: 'merchant-1',
      merchantDomain: 'running.example',
      provider: 'test',
      merchantIntegrationId: 'integration-1',
      externalMerchantId: 'external-1',
      routingScopeKey: 'running-scope',
      snapshot: {
        merchantKey: 'running-scope',
        merchant: 'Running Shop',
        cartId: 'cart-old',
        remoteCartId: 'remote-cart-old',
        checkoutUrl: null,
        continueUrl: null,
        subtotalAmount: 129,
        totalAmount: 129,
        currency: 'USD',
        appliedCodes: [],
      },
      lines: [
        {
          ...cart[0]!,
          merchantId: 'merchant-1',
          merchantDomain: 'running.example',
          routingScopeKey: 'running-scope',
          merchantScopeKey: 'running-scope',
          cartId: 'cart-old',
          remoteCartId: 'remote-cart-old',
        },
      ],
    }
    const replacementCart = staleReplacement.lines.map((line) => ({
      ...line,
      cartId: 'cart-new',
      remoteCartId: 'remote-cart-new',
    }))
    const current = agentCartPartitionFingerprints(replacementCart, {})

    expect(agentCartReplacementSupersededByCurrentPartition(staleReplacement, current)).toBe(true)
    expect(
      agentCartReplacementSupersededByCurrentPartition(
        { ...staleReplacement, snapshot: { ...staleReplacement.snapshot, cartId: 'cart-new' } },
        current,
      ),
    ).toBe(false)
  })

  test('fails closed for the transient flat partition schema stored by an older build', () => {
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'merchant-a',
      merchantId: 'merchant-a',
      merchantDomain: null,
      provider: null,
      merchantIntegrationId: null,
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: {
        merchantKey: 'merchant-a',
        merchant: 'Running Shop',
        cartId: 'cart-1',
        remoteCartId: null,
        checkoutUrl: null,
        continueUrl: null,
        subtotalAmount: 129,
        totalAmount: 129,
        currency: 'USD',
        appliedCodes: [],
      },
      lines: cart,
    }
    const transientFlatShape = {
      'merchant:merchant-a': 'old-inline-fingerprint',
    } as unknown as ReturnType<typeof agentCartPartitionFingerprints>

    expect(
      agentCartReplacementUnchangedSinceSubmission(
        replacement,
        transientFlatShape,
        agentCartPartitionFingerprints(cart, {}),
      ),
    ).toBe(false)
  })

  test('keeps one full revision per partition and supports the legacy whole-cart guard', () => {
    const fingerprints = agentCartPartitionFingerprints(cart, {})
    expect(Object.keys(fingerprints.aliases).length).toBeGreaterThan(1)
    expect(Object.keys(fingerprints.revisions)).toHaveLength(1)
    expect(agentCartStateFingerprint([...cart].reverse(), {})).toBe(
      agentCartStateFingerprint(cart, {}),
    )
  })

  test('applies immutable cart-row controls as deltas to the current live quantity', () => {
    expect(liveCartQuantityAfterDelta(3, 1)).toBe(4)
    expect(liveCartQuantityAfterDelta(3, -1)).toBe(2)
    expect(liveCartQuantityAfterDelta(0, -1)).toBe(0)
  })

  test('projects cart-card quantity changes immediately from the live cart', () => {
    const liveLine = { ...cart[0]!, qty: 3 }
    const historicalLine = { ...liveLine, qty: 1 }

    const increased = optimisticAgentCartQuantityChange(
      [liveLine],
      historicalLine.id,
      historicalLine.merchant,
      2,
      undefined,
      1,
      historicalLine,
    )

    expect(increased?.quantity).toBe(4)
    expect(increased?.cart[0]).toMatchObject({ qty: 4, syncing: true, syncError: null })

    const removed = optimisticAgentCartQuantityChange(
      increased?.cart ?? [],
      liveLine.id,
      liveLine.merchant,
      0,
      increased?.identity,
      undefined,
      liveLine,
    )
    expect(removed?.quantity).toBe(0)
    expect(removed?.cart).toEqual([])
  })

  test('serializes rapid cart intents and keeps processing after a failed action', async () => {
    const queue = new SerializedAgentActionQueue()
    const events: string[] = []
    let quantity = 1
    const firstIntent = queue.enqueue(async () => {
      events.push('first:start')
      await Promise.resolve()
      quantity = liveCartQuantityAfterDelta(quantity, 1)
      events.push(`first:${quantity}`)
    })
    const failedIntent = queue.enqueue(async () => {
      events.push('failed')
      throw new Error('merchant unavailable')
    })
    const secondIntent = queue.enqueue(async () => {
      quantity = liveCartQuantityAfterDelta(quantity, 1)
      events.push(`second:${quantity}`)
    })

    await firstIntent
    await expect(failedIntent).rejects.toThrow('merchant unavailable')
    await secondIntent
    expect(events).toEqual(['first:start', 'first:2', 'failed', 'second:3'])
  })

  test('keeps one commerce queue per account across view remounts', () => {
    expect(commerceActionQueueFor('queue-test-account')).toBe(
      commerceActionQueueFor('queue-test-account'),
    )
    expect(commerceActionQueueFor('queue-test-account')).not.toBe(
      commerceActionQueueFor('queue-test-other-account'),
    )
  })

  test('notifies a remounted view when an account action settles', async () => {
    const queue = new SerializedAgentActionQueue()
    let settled = 0
    const unsubscribe = queue.subscribeToSettled(() => {
      settled += 1
    })

    await queue.enqueue(async () => 'completed')
    expect(settled).toBe(1)
    unsubscribe()
    await queue.enqueue(async () => 'completed again')
    expect(settled).toBe(1)
  })

  test('resolves a recreated historical line only by one offer in the same merchant partition', () => {
    const historical = {
      ...cart[0]!,
      merchantId: 'running-shop',
      merchantIntegrationId: 'running-shop-primary',
      cartId: 'old-cart',
      cartLineId: 'old-line',
    }
    const recreated = {
      ...historical,
      cartId: 'new-cart',
      cartLineId: 'new-line',
      qty: 3,
    }
    const otherMerchant = {
      ...recreated,
      merchantId: 'other-shop',
      merchantIntegrationId: 'other-shop-primary',
      cartId: 'other-cart',
      cartLineId: 'other-line',
    }

    expect(resolveLiveCartItem([otherMerchant, recreated], historical, 'old-identity')).toBe(
      recreated,
    )
    expect(
      resolveLiveCartItem(
        [recreated, { ...recreated, cartLineId: 'duplicate-line' }],
        historical,
        'old-identity',
      ),
    ).toBeNull()
  })

  test('keeps historical checkout display rows but resolves actions to the recreated live cart', () => {
    const historical = {
      ...cart[0]!,
      merchantId: 'running-shop',
      merchantIntegrationId: 'running-shop-primary',
      cartId: 'old-cart',
      cartLineId: 'old-line',
    }
    const current = {
      ...historical,
      cartId: 'new-cart',
      cartLineId: 'new-line',
      checkoutUrl: 'https://running.example/new-checkout',
    }
    const displayGroup = cartGroups(cartLines([historical], [first]))[0]!
    const liveGroup = cartGroups(cartLines([current], [first]))[0]!

    expect(liveCheckoutGroupFor(displayGroup, [liveGroup])).toBe(liveGroup)
    expect(liveCheckoutGroupFor(displayGroup, [])).toBeNull()

    const staleMarkup = renderToStaticMarkup(
      <InlineCheckoutBlock
        threadId="conversation-1"
        cart={[historical]}
        products={[first]}
        actionCart={[]}
        actionProducts={[first]}
        onCheckout={() => undefined}
        activeCheckout={null}
        checkoutBusy={false}
        checkoutError={null}
        onCheckoutAssistant={async () => null}
        onRefreshCheckout={() => undefined}
        onOpenCart={() => undefined}
        onOpenOrders={() => undefined}
      />,
    )
    expect(staleMarkup).toContain('Cart changed — open full cart')
    expect(staleMarkup).toContain('disabled=""')
  })

  test('rejects a historical checkout when live contents or cart boundaries changed', () => {
    const historical = {
      ...cart[0]!,
      merchantId: 'running-shop',
      merchantIntegrationId: 'running-shop-primary',
      cartId: 'old-cart',
      cartLineId: 'old-line',
    }
    const displayGroup = cartGroups(cartLines([historical], [first, second]))[0]!
    const differentOffer = {
      ...historical,
      id: second.id,
      offerKey: 'offer-2',
      productVariantId: 'product-2-variant',
      cartId: 'new-cart',
      cartLineId: 'different-line',
      qty: 9,
    }
    const differentGroup = cartGroups(cartLines([differentOffer], [first, second]))[0]!
    const secondHistoricalLine = { ...historical, cartLineId: 'old-line-2' }
    const boundaryDisplayGroup = cartGroups(
      cartLines([historical, secondHistoricalLine], [first]),
    )[0]!
    const mergedCarts = cartGroups(
      cartLines(
        [
          { ...historical, cartId: 'new-cart', cartLineId: 'new-line' },
          { ...secondHistoricalLine, cartId: 'another-cart', cartLineId: 'another-line' },
        ],
        [first],
      ),
    )[0]!

    expect(liveCheckoutGroupFor(displayGroup, [differentGroup])).toBeNull()
    expect(liveCheckoutGroupFor(boundaryDisplayGroup, [mergedCarts])).toBeNull()
  })

  test('renders agent products through the existing card batch and every product CTA', () => {
    const markup = renderToStaticMarkup(
      <DiscoverProductBatch
        products={[first, second]}
        query="running shoes"
        deliveryLocations={[]}
        preferences={[]}
        savedSet={new Set()}
        savePendingSet={new Set()}
        pinnedSet={new Set()}
        watchedSet={new Set()}
        shelfProductSet={new Set()}
        onOpen={() => undefined}
        onToggleSave={() => undefined}
        onAddCart={() => undefined}
        onPin={() => undefined}
        onWatch={() => undefined}
        onDig={() => undefined}
        onJustPick={() => undefined}
        onCompareHere={() => undefined}
        onShelfAddProduct={() => undefined}
        onDragProduct={() => undefined}
      />,
    )

    expect(markup).toContain('class="mt-card')
    expect(markup).toContain('Grounded trail shoe')
    expect(markup).toContain('Responsive road shoe')
    expect(markup).toContain('Add to cart')
    expect(markup).toContain('>Pin<')
    expect(markup).toContain('>Watch<')
    expect(markup).toContain('>Reviews<')
    expect(markup).toContain('Find a discount')
    expect(markup).toContain('>Similar<')
    expect(markup).toContain('Compare here')
    expect(markup).toContain('Just pick one')
    expect(markup).not.toContain('disabled=""')
  })

  test('disables agent product CTAs while a conversation run is active', () => {
    const markup = renderToStaticMarkup(
      <DiscoverProductBatch
        products={[first, second]}
        query="running shoes"
        deliveryLocations={[]}
        preferences={[]}
        savedSet={new Set()}
        savePendingSet={new Set()}
        pinnedSet={new Set()}
        watchedSet={new Set()}
        shelfProductSet={new Set()}
        onOpen={() => undefined}
        onToggleSave={() => undefined}
        onAddCart={() => undefined}
        onPin={() => undefined}
        onWatch={() => undefined}
        onDig={() => undefined}
        onJustPick={() => undefined}
        onCompareHere={() => undefined}
        onShelfAddProduct={() => undefined}
        onDragProduct={() => undefined}
        agentActionsDisabled
      />,
    )

    expect(markup).toContain('class="mt-ct-addbtn" type="button" disabled=""')
    expect(markup).toContain('class="mt-ct-pinbtn " type="button" disabled=""')
    expect(markup).toContain('class="mt-ct-watchbtn " type="button" disabled=""')
    expect(markup).toContain('class="mt-ct-askchip" type="button" disabled=""')
    expect(markup).toContain('class="mt-ct-suggchip" type="button" disabled=""')
    expect(markup).toContain('class="mt-ct-suggchip ghost" type="button" disabled=""')
    expect(markup).not.toContain('class="mt-save " type="button" aria-label="Save" disabled=""')
  })

  test('renders only the trusted merchant origin in cart and checkout blocks', () => {
    const poisonedCart = [
      {
        ...cart[0]!,
        merchant: 'sollys-online-grocery.myshopify.com',
        merchantDomain: 'nycfactory.com',
        merchantOrigin: 'nycfactory.com',
      },
    ]
    const markup = renderToStaticMarkup(
      <>
        <InlineCartBlock
          cart={poisonedCart}
          products={[first]}
          onQty={() => undefined}
          onRemove={() => undefined}
          onAddCart={() => undefined}
          onOpenCart={() => undefined}
          onCheckoutHere={() => undefined}
        />
        <InlineCheckoutBlock
          threadId="conversation-1"
          cart={poisonedCart}
          products={[first]}
          onCheckout={() => undefined}
          activeCheckout={null}
          checkoutBusy={false}
          checkoutError={null}
          onCheckoutAssistant={async () => null}
          onRefreshCheckout={() => undefined}
          onOpenCart={() => undefined}
          onOpenOrders={() => undefined}
        />
      </>,
    )

    expect(markup).toContain('nycfactory.com')
    expect(markup).not.toContain('sollys-online-grocery.myshopify.com')
  })

  test('renders an explicit review result when a catalog merchant has no review integration', () => {
    const markup = renderToStaticMarkup(
      <ProductReviewsPanel
        product={{ ...first, review: { score: null, count: 0, insight: '' } }}
        mode="chat"
        initialResponse={{
          merchantId: '',
          productId: 'external-product-1',
          provider: 'UNKNOWN',
          rating: null,
          reviewCount: 0,
          hasMore: false,
          reviews: [],
          cached: false,
          supported: false,
          message:
            'Reviews are unavailable because this merchant does not have a connected review provider.',
        }}
      />,
    )

    expect(markup).toContain('Reviews · Grounded trail shoe')
    expect(markup).toContain('No review data')
    expect(markup).toContain(
      'Reviews are unavailable because this merchant does not have a connected review provider.',
    )
  })

  test('shows a pending cart addition instead of an empty cart before the first line arrives', () => {
    const loadingMarkup = renderToStaticMarkup(
      <InlineCartBlock
        cart={[]}
        products={[first]}
        onQty={() => undefined}
        onRemove={() => undefined}
        onAddCart={() => undefined}
        onOpenCart={() => undefined}
        onCheckoutHere={() => undefined}
        loading
      />,
    )
    const loadedMarkup = renderToStaticMarkup(
      <InlineCartBlock
        cart={cart}
        products={[first, second]}
        onQty={() => undefined}
        onRemove={() => undefined}
        onAddCart={() => undefined}
        onOpenCart={() => undefined}
        onCheckoutHere={() => undefined}
        loading
      />,
    )
    const emptyMarkup = renderToStaticMarkup(
      <InlineCartBlock
        cart={[]}
        products={[first]}
        onQty={() => undefined}
        onRemove={() => undefined}
        onAddCart={() => undefined}
        onOpenCart={() => undefined}
        onCheckoutHere={() => undefined}
      />,
    )

    expect(loadingMarkup).toContain('role="status"')
    expect(loadingMarkup).toContain('Adding item to cart…')
    expect(loadingMarkup).toContain('Confirming availability with the merchant.')
    expect(loadingMarkup).not.toContain('Your cart is empty.')
    expect(loadingMarkup).not.toContain('Checkout in chat')
    expect(loadedMarkup).toContain('Quantity for Grounded trail shoe')
    expect(loadedMarkup).toContain('1 item')
    expect(loadedMarkup).not.toContain('Adding item to cart…')
    expect(emptyMarkup).toContain('Nothing Meant for your cart yet.')
    expect(emptyMarkup).toContain('When a find clicks')
    expect(emptyMarkup).not.toContain('Checkout in chat')
  })

  test('renders comparison, cart, and checkout artifacts through their existing blocks', () => {
    const comparison: Extract<DiscoverChatBlock, { type: 'minicompare' }> = {
      type: 'minicompare',
      products: [first, second],
      rows: [
        { label: 'Match', values: ['92%', '92%'], winnerIndex: 0 },
        { label: 'From', values: ['$129', '$149'], winnerIndex: 0 },
      ],
      pickIndex: 0,
    }
    const compareMarkup = renderToStaticMarkup(
      <InlineMiniCompareBlock
        block={comparison}
        deliveryLocations={[]}
        onOpen={() => undefined}
        onAddCart={() => undefined}
        onOpenFullCompare={() => undefined}
      />,
    )
    const busyCompareMarkup = renderToStaticMarkup(
      <InlineMiniCompareBlock
        block={comparison}
        deliveryLocations={[]}
        onOpen={() => undefined}
        onAddCart={() => undefined}
        onOpenFullCompare={() => undefined}
        agentActionsDisabled
      />,
    )
    const cartMarkup = renderToStaticMarkup(
      <InlineCartBlock
        cart={cart}
        products={[first, second]}
        onQty={() => undefined}
        onRemove={() => undefined}
        onAddCart={() => undefined}
        onOpenCart={() => undefined}
        onCheckoutHere={() => undefined}
      />,
    )
    const checkoutMarkup = renderToStaticMarkup(
      <InlineCheckoutBlock
        threadId="conversation-1"
        cart={cart}
        products={[first, second]}
        onCheckout={() => undefined}
        activeCheckout={null}
        checkoutBusy={false}
        checkoutError={null}
        onCheckoutAssistant={async () => null}
        onRefreshCheckout={() => undefined}
        onOpenCart={() => undefined}
        onOpenOrders={() => undefined}
      />,
    )
    const busyCheckoutMarkup = renderToStaticMarkup(
      <InlineCheckoutBlock
        threadId="conversation-1"
        cart={cart}
        products={[first, second]}
        onCheckout={() => undefined}
        activeCheckout={null}
        checkoutBusy
        checkoutError={null}
        onCheckoutAssistant={async () => null}
        onRefreshCheckout={() => undefined}
        onOpenCart={() => undefined}
        onOpenOrders={() => undefined}
      />,
    )
    const syncingCheckoutMarkup = renderToStaticMarkup(
      <InlineCheckoutBlock
        threadId="conversation-1"
        cart={[{ ...cart[0]!, syncing: true }]}
        products={[first, second]}
        onCheckout={() => undefined}
        activeCheckout={null}
        checkoutBusy={false}
        checkoutError={null}
        onCheckoutAssistant={async () => null}
        onRefreshCheckout={() => undefined}
        onOpenCart={() => undefined}
        onOpenOrders={() => undefined}
      />,
    )

    expect(compareMarkup).toContain('Inline compare')
    expect(compareMarkup).toContain('Add pick')
    expect(compareMarkup).toContain('Open full compare')
    expect(compareMarkup).not.toContain('disabled=""')
    expect(busyCompareMarkup).toContain('class="mt-ct-addbtn solid" type="button" disabled=""')
    expect(busyCompareMarkup).toContain('class="mt-ct-mini-full" type="button" disabled=""')
    expect(cartMarkup).toContain('Cart in chat')
    expect(cartMarkup).toContain('Your <em>Meant</em> finds')
    expect(cartMarkup).toContain('Cart total')
    expect(cartMarkup).toContain('Quantity for Grounded trail shoe')
    expect(cartMarkup).toContain('Ready when you are.')
    expect(cartMarkup).toContain('Checkout in chat')
    expect(checkoutMarkup).toContain('Checkout in chat')
    expect(checkoutMarkup).toContain('<em>Meant</em> together')
    expect(checkoutMarkup.match(/Estimated total/g)).toHaveLength(1)
    expect(checkoutMarkup).toContain('>Merchant<')
    expect(checkoutMarkup).not.toContain('Running Shop')
    expect(checkoutMarkup).toContain('Start checkout in chat')
    expect(busyCheckoutMarkup).toContain('Checkout is starting...')
    expect(busyCheckoutMarkup).toContain('disabled=""')
    expect(syncingCheckoutMarkup).toContain('Merchant cart is still syncing')
    expect(syncingCheckoutMarkup).toContain('disabled=""')
  })
})
