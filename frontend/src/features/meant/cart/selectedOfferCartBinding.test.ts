import { describe, expect, mock, test } from 'bun:test'

import type { CartProfile } from '../../../lib/apiClient'
import type { CartItem } from '../types'
import {
  bindSelectedOfferWithStaleCartRecovery,
  cartSnapshotHasExactOfferLine,
  confirmedCartIdForMerchant,
  confirmedCartIdForOffer,
  mergeConfirmedCartSnapshot,
  mergeInitialSelectedOfferSnapshot,
  settleUnconfirmedSelectedOfferAddition,
} from './selectedOfferCartBinding'

function confirmedItem(overrides: Partial<CartItem>): CartItem {
  return {
    id: 'product-a',
    merchant: 'Same merchant name',
    qty: 1,
    offerKey: 'offer-a',
    cartId: 'cart-a',
    cartLineId: 'line-a',
    syncing: false,
    syncError: null,
    ...overrides,
  }
}

describe('selected offer cart binding', () => {
  test('does not reuse a same-name merchant cart for a different exact offer', () => {
    const cart = [confirmedItem({ offerKey: 'offer-other', cartId: 'cart-other' })]

    expect(confirmedCartIdForOffer(cart, 'offer-a')).toBeUndefined()
  })

  test('reuses one confirmed merchant cart for separate exact variant lines', () => {
    const first = confirmedItem({
      offerKey: 'offer-a',
      merchantId: 'merchant-record-uuid',
      merchantIntegrationId: 'cart-integration-uuid',
      routingScopeKey: 'SHOPIFY:integration:cart-integration-uuid',
      merchantScopeKey: 'shopify:integration-fallback:catalog-integration-uuid',
      cartId: 'cart-a',
    })

    expect(
      confirmedCartIdForMerchant([first], {
        merchantIntegrationId: 'catalog-integration-uuid',
        merchantScopeKey: 'shopify:integration-fallback:catalog-integration-uuid',
      }),
    ).toBe('cart-a')
    expect(
      confirmedCartIdForMerchant([first], {
        routingScopeKey: 'SHOPIFY:integration:cart-integration-uuid',
      }),
    ).toBe('cart-a')

    const second = confirmedItem({
      offerKey: 'offer-b',
      merchantId: 'merchant-record-uuid',
      merchantIntegrationId: 'cart-integration-uuid',
      routingScopeKey: 'SHOPIFY:integration:cart-integration-uuid',
      merchantScopeKey: 'shopify:integration-fallback:catalog-integration-uuid',
      cartId: 'cart-a',
      cartLineId: null,
      syncing: true,
    })
    const snapshot = {
      cartId: 'cart-a',
      merchantId: 'merchant-record-uuid',
      merchantIntegrationId: 'cart-integration-uuid',
      routingScopeKey: 'SHOPIFY:integration:cart-integration-uuid',
      lines: [
        { offerKey: 'offer-a', cartLineId: 'line-a', quantity: 1 },
        { offerKey: 'offer-b', cartLineId: 'line-b', quantity: 1 },
      ],
    } as unknown as CartProfile

    const merged = mergeConfirmedCartSnapshot([first, second], 'cart-a', snapshot)
    expect(merged).toHaveLength(2)
    expect(merged.map((item) => item.offerKey)).toEqual(['offer-a', 'offer-b'])
    expect(merged.map((item) => item.cartLineId)).toEqual(['line-a', 'line-b'])
    expect(merged[1]).toMatchObject({
      merchantId: 'merchant-record-uuid',
      merchantIntegrationId: 'cart-integration-uuid',
      routingScopeKey: 'SHOPIFY:integration:cart-integration-uuid',
      merchantScopeKey: 'shopify:integration-fallback:catalog-integration-uuid',
    })
  })

  test('never reuses a cart from merchant display name alone', () => {
    expect(
      confirmedCartIdForMerchant(
        [
          confirmedItem({
            merchantId: null,
            merchantDomain: null,
            merchantIntegrationId: null,
            externalMerchantId: null,
            routingScopeKey: null,
            merchantScopeKey: null,
          }),
        ],
        {},
      ),
    ).toBeUndefined()
  })

  test('keeps external merchant identity scoped to its provider', () => {
    const external = confirmedItem({
      provider: 'SHOPIFY',
      externalMerchantId: 'merchant-1',
      merchantId: null,
      merchantDomain: null,
      merchantIntegrationId: null,
      routingScopeKey: null,
      merchantScopeKey: null,
    })

    expect(
      confirmedCartIdForMerchant([external], {
        provider: 'ETSY',
        externalMerchantId: 'merchant-1',
      }),
    ).toBeUndefined()
    expect(
      confirmedCartIdForMerchant([external], {
        provider: 'SHOPIFY',
        externalMerchantId: 'merchant-1',
      }),
    ).toBe('cart-a')
  })

  test('keeps merchant domains scoped to their provider', () => {
    const domain = confirmedItem({
      provider: 'SHOPIFY',
      merchantDomain: 'merchant.example',
      merchantId: null,
      merchantIntegrationId: null,
      externalMerchantId: null,
      routingScopeKey: null,
      merchantScopeKey: null,
    })

    expect(
      confirmedCartIdForMerchant([domain], {
        provider: 'ETSY',
        merchantDomain: 'merchant.example',
      }),
    ).toBeUndefined()
    expect(
      confirmedCartIdForMerchant([domain], {
        provider: 'SHOPIFY',
        merchantDomain: 'merchant.example',
      }),
    ).toBe('cart-a')
    expect(
      confirmedCartIdForMerchant([domain], {
        merchantDomain: 'merchant.example',
      }),
    ).toBeUndefined()
    expect(
      confirmedCartIdForMerchant([{ ...domain, provider: null }], {
        merchantDomain: 'merchant.example',
      }),
    ).toBe('cart-a')
  })

  test('does not let a weak domain override conflicting strong merchant scope keys', () => {
    const scoped = confirmedItem({
      provider: 'SHOPIFY',
      merchantDomain: 'merchant.example',
      merchantScopeKey: 'shopify:external:SHOP:namespace:merchant-a',
      routingScopeKey: null,
      merchantIntegrationId: null,
      externalMerchantId: null,
    })

    expect(
      confirmedCartIdForMerchant([scoped], {
        provider: 'SHOPIFY',
        merchantDomain: 'merchant.example',
        merchantScopeKey: 'shopify:external:SHOP:namespace:merchant-b',
      }),
    ).toBeUndefined()
    expect(
      confirmedCartIdForMerchant([scoped], {
        provider: 'SHOPIFY',
        merchantDomain: 'merchant.example',
      }),
    ).toBe('cart-a')
  })

  test('matches a saved routing-only line to a canonical sibling by provider merchant identity', () => {
    const savedLine = confirmedItem({
      provider: 'SHOPIFY',
      externalMerchantId: 'external-merchant-a',
      merchantId: 'merchant-record-uuid',
      merchantDomain: 'merchant.example',
      merchantIntegrationId: null,
      routingScopeKey: 'SHOPIFY:integration:cart-integration-uuid',
      merchantScopeKey: null,
    })

    expect(
      confirmedCartIdForMerchant([savedLine], {
        provider: 'SHOPIFY',
        externalMerchantId: 'external-merchant-a',
        merchantScopeKey: 'shopify:external:MERCHANT:global:external-merchant-a',
      }),
    ).toBe('cart-a')
  })

  test('reuses a confirmed cart only for the exact repeated offer key', () => {
    const cart = [confirmedItem({})]

    expect(confirmedCartIdForOffer(cart, 'offer-a')).toBe('cart-a')
  })

  test('does not reuse optimistic or rejected local cart state', () => {
    expect(
      confirmedCartIdForOffer([confirmedItem({ cartLineId: null, syncing: true })], 'offer-a'),
    ).toBeUndefined()
    expect(
      confirmedCartIdForOffer([confirmedItem({ syncError: 'Rejected by server' })], 'offer-a'),
    ).toBeUndefined()
  })

  test('initial response reconciliation does not mutate an unrelated same-name item', () => {
    const unrelated = confirmedItem({
      id: 'unrelated-product',
      offerKey: 'unrelated-offer',
      cartId: null,
      cartLineId: null,
    })
    const selected = confirmedItem({
      cartId: null,
      cartLineId: null,
      syncing: true,
    })
    const snapshot = {
      cartId: 'server-cart',
      merchantId: 'server-merchant',
      merchantDomain: 'server.example',
      lines: [
        {
          offerKey: 'offer-a',
          cartLineId: 'server-line',
          quantity: 1,
        },
      ],
    } as unknown as CartProfile

    const merged = mergeInitialSelectedOfferSnapshot(
      [unrelated, selected],
      'product-a',
      'offer-a',
      snapshot,
    )

    expect(merged[0]).toEqual(unrelated)
    expect(merged[1]).toMatchObject({
      offerKey: 'offer-a',
      cartId: 'server-cart',
      cartLineId: 'server-line',
      merchantId: 'server-merchant',
      merchantDomain: 'server.example',
      syncing: false,
    })
  })

  test('repeat response reconciliation is scoped by confirmed cart id, not merchant name', () => {
    const sameNameOtherCart = confirmedItem({
      id: 'other-product',
      offerKey: 'other-offer',
      cartId: 'other-cart',
      cartLineId: 'other-line',
    })
    const repeated = confirmedItem({})
    const snapshot = {
      cartId: 'cart-a',
      lines: [{ offerKey: 'offer-a', cartLineId: 'line-a', quantity: 2 }],
    } as unknown as CartProfile

    const merged = mergeConfirmedCartSnapshot([sameNameOtherCart, repeated], 'cart-a', snapshot)

    expect(merged[0]).toEqual(sameNameOtherCart)
    expect(merged[1]?.qty).toBe(2)
  })

  test('rebuilds once without the expired cart id after a confirmed cart returns 404', async () => {
    const events: string[] = []
    const rebuiltSnapshot = {
      cartId: 'fresh-cart',
      lines: [{ offerKey: 'offer-a', cartLineId: 'fresh-line' }],
    } as unknown as CartProfile
    const bind = mock(async () => {
      events.push('bind:dead-cart')
      throw new Error('Cart expired')
    })
    const rebuild = mock(async () => {
      events.push('rebuild:without-cart-id')
      return rebuiltSnapshot
    })

    await expect(
      bindSelectedOfferWithStaleCartRecovery('dead-cart', bind, rebuild, () => true),
    ).resolves.toEqual({ snapshot: rebuiltSnapshot, rebuilt: true })
    expect(events).toEqual(['bind:dead-cart', 'rebuild:without-cart-id'])
    expect(bind).toHaveBeenCalledTimes(1)
    expect(rebuild).toHaveBeenCalledTimes(1)
  })

  test('requires the requested exact offer and a merchant cart line before reporting success', () => {
    const omitted = {
      cartId: 'cart-a',
      lines: [{ offerKey: 'sibling-offer', cartLineId: 'line-b', quantity: 1 }],
    } as unknown as CartProfile
    const missingLineHandle = {
      cartId: 'cart-a',
      lines: [{ offerKey: 'offer-a', cartLineId: '', remoteCartLineId: '', quantity: 1 }],
    } as unknown as CartProfile
    const confirmed = {
      cartId: 'cart-a',
      lines: [{ offerKey: 'offer-a', cartLineId: 'line-a', quantity: 2 }],
    } as unknown as CartProfile

    expect(cartSnapshotHasExactOfferLine(omitted, 'offer-a', 1)).toBe(false)
    expect(cartSnapshotHasExactOfferLine(missingLineHandle, 'offer-a', 1)).toBe(false)
    expect(cartSnapshotHasExactOfferLine(confirmed, 'offer-a', 2)).toBe(true)
    expect(cartSnapshotHasExactOfferLine(confirmed, 'offer-a', 3)).toBe(false)
  })

  test('does not accept a repeated add when the 200 response retains the pre-add quantity', () => {
    const unchanged = {
      cartId: 'cart-a',
      lines: [{ offerKey: 'offer-a', cartLineId: 'line-a', quantity: 1 }],
    } as unknown as CartProfile

    expect(cartSnapshotHasExactOfferLine(unchanged, 'offer-a', 2)).toBe(false)
    expect(
      settleUnconfirmedSelectedOfferAddition(
        [confirmedItem({ qty: 1, syncing: false, syncError: null })],
        'product-a',
        'offer-a',
        true,
      ),
    ).toEqual([confirmedItem({ qty: 1, syncing: false, syncError: null })])
  })
})
