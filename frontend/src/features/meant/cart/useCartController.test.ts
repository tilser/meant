import { describe, expect, test } from 'bun:test'

import type { CartItem } from '../types'
import type { MerchantCartSnapshot, MerchantCartStateReplacement } from './types'
import { reconcileMerchantCartStates } from './useCartController'

function snapshot(
  merchantKey: string,
  cartId: string,
  merchant = 'trail.example',
): MerchantCartSnapshot {
  return {
    merchantKey,
    merchant,
    cartId,
    remoteCartId: `remote-${cartId}`,
    checkoutUrl: `https://${merchant}/checkout`,
    continueUrl: null,
    subtotalAmount: 129,
    totalAmount: 129,
    currency: 'USD',
    appliedCodes: [],
  }
}

describe('agent cart state reconciliation', () => {
  test('replaces an older cart for the same merchant partition and invalidates its snapshot', () => {
    const oldLine: CartItem = {
      id: 'product-old',
      merchant: 'trail.example',
      qty: 1,
      provider: 'SHOPIFY',
      externalMerchantId: 'merchant-1',
      merchantDomain: 'trail.example',
      merchantScopeKey: 'shopify:external:merchant-1',
      cartId: 'cart-old',
      offerKey: 'offer-old',
    }
    const unrelated: CartItem = {
      id: 'product-other',
      merchant: 'other.example',
      qty: 1,
      merchantDomain: 'other.example',
      cartId: 'cart-other',
      offerKey: 'offer-other',
    }
    const newLine: CartItem = {
      ...oldLine,
      id: 'product-new',
      cartId: 'cart-new',
      offerKey: 'offer-new',
      merchantScopeKey: 'shopify:external:merchant-1:cart',
    }
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'shopify:external:merchant-1:cart',
      merchantId: null,
      merchantDomain: 'trail.example',
      provider: 'SHOPIFY',
      merchantIntegrationId: null,
      externalMerchantId: 'merchant-1',
      routingScopeKey: 'shopify:external:merchant-1:cart',
      snapshot: snapshot('shopify:external:merchant-1:cart', 'cart-new'),
      lines: [newLine],
    }

    const result = reconcileMerchantCartStates(
      [oldLine, unrelated],
      {
        'shopify:external:merchant-1': snapshot('shopify:external:merchant-1', 'cart-old'),
        'other.example': snapshot('other.example', 'cart-other', 'other.example'),
      },
      [replacement],
    )

    expect(result.cart).toEqual([unrelated, newLine])
    expect(result.snapshots['shopify:external:merchant-1']).toBeUndefined()
    expect(result.snapshots['shopify:external:merchant-1:cart']?.cartId).toBe('cart-new')
    expect(result.snapshots['other.example']?.cartId).toBe('cart-other')
  })

  test('does not collapse distinct strong merchant routes that share a provider and domain', () => {
    const integrationA: CartItem = {
      id: 'product-a',
      merchant: 'shared.example',
      qty: 1,
      provider: 'SHOPIFY',
      merchantDomain: 'shared.example',
      merchantIntegrationId: 'integration-a',
      routingScopeKey: 'shopify:integration-a',
      cartId: 'cart-a',
      offerKey: 'offer-a',
    }
    const integrationB: CartItem = {
      ...integrationA,
      id: 'product-b-old',
      merchantIntegrationId: 'integration-b',
      routingScopeKey: 'shopify:integration-b',
      cartId: 'cart-b-old',
      offerKey: 'offer-b-old',
    }
    const replacementLine: CartItem = {
      ...integrationB,
      id: 'product-b-new',
      cartId: 'cart-b-new',
      offerKey: 'offer-b-new',
    }
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'shopify:integration-b',
      merchantId: null,
      merchantDomain: 'shared.example',
      provider: 'SHOPIFY',
      merchantIntegrationId: 'integration-b',
      externalMerchantId: null,
      routingScopeKey: 'shopify:integration-b',
      snapshot: snapshot('shopify:integration-b', 'cart-b-new', 'shared.example'),
      lines: [replacementLine],
    }

    const result = reconcileMerchantCartStates(
      [integrationA, integrationB],
      {
        'shopify:integration-a': snapshot('shopify:integration-a', 'cart-a', 'shared.example'),
        'shopify:integration-b': snapshot('shopify:integration-b', 'cart-b-old', 'shared.example'),
      },
      [replacement],
    )

    expect(result.cart).toEqual([integrationA, replacementLine])
    expect(result.snapshots['shopify:integration-a']?.cartId).toBe('cart-a')
    expect(result.snapshots['shopify:integration-b']?.cartId).toBe('cart-b-new')
  })

  test('does not let a weaker scope alias override different integrations', () => {
    const existing: CartItem = {
      id: 'product-a',
      merchant: 'shared.example',
      qty: 1,
      provider: 'SHOPIFY',
      merchantDomain: 'shared.example',
      merchantIntegrationId: 'integration-a',
      routingScopeKey: 'shopify:shared-scope',
      cartId: 'cart-a',
      offerKey: 'offer-a',
    }
    const replacementLine: CartItem = {
      id: 'product-b',
      merchant: 'shared.example',
      qty: 1,
      provider: 'SHOPIFY',
      merchantDomain: 'shared.example',
      merchantIntegrationId: 'integration-b',
      merchantScopeKey: 'shopify:shared-scope',
      cartId: 'cart-b',
      offerKey: 'offer-b',
    }
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'shopify:shared-scope',
      merchantId: null,
      merchantDomain: 'shared.example',
      provider: 'SHOPIFY',
      merchantIntegrationId: 'integration-b',
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: snapshot('shopify:shared-scope', 'cart-b', 'shared.example'),
      lines: [replacementLine],
    }

    const result = reconcileMerchantCartStates([existing], {}, [replacement])

    expect(result.cart).toEqual([existing, replacementLine])
  })

  test('keeps distinct merchant scopes separate on the same provider domain', () => {
    const existing: CartItem = {
      id: 'product-a',
      merchant: 'shared.example',
      qty: 1,
      provider: 'SHOPIFY',
      merchantDomain: 'shared.example',
      merchantScopeKey: 'shopify:scope-a',
      cartId: 'cart-a',
      offerKey: 'offer-a',
    }
    const replacementLine: CartItem = {
      ...existing,
      id: 'product-b',
      merchantScopeKey: 'shopify:scope-b',
      cartId: 'cart-b',
      offerKey: 'offer-b',
    }
    const replacement: MerchantCartStateReplacement = {
      merchantKey: 'shopify:scope-b',
      merchantId: null,
      merchantDomain: 'shared.example',
      provider: 'SHOPIFY',
      merchantIntegrationId: null,
      externalMerchantId: null,
      routingScopeKey: null,
      snapshot: snapshot('shopify:scope-b', 'cart-b', 'shared.example'),
      lines: [replacementLine],
    }

    const result = reconcileMerchantCartStates([existing], {}, [replacement])

    expect(result.cart).toEqual([existing, replacementLine])
  })
})
