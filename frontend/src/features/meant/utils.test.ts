import { describe, expect, test } from 'bun:test'

import { ApiError } from '../../lib/apiError'
import type { CartProfile } from '../../lib/apiClient'
import { DEFAULT_PREFERENCE_IDS, PREFERENCES, PRODUCTS, REPLIES } from './data'
import {
  availableOffers,
  bestOffer,
  canMerchantShip,
  cartDeliveryOptionAmount,
  cartDeliveryOptions,
  cartGroups,
  cartLines,
  cartMerchantKey,
  cartRebuildItems,
  computeSmartAlerts,
  createOrder,
  deriveFilters,
  displayProductCategoryValue,
  firstUrl,
  formatOrderDate,
  isCartNotFoundError,
  isCorePreferenceId,
  listJoin,
  mergeCartSnapshot,
  money,
  normalizedMerchantName,
  orderTotal,
  prefLabel,
  productById,
  productMatchesClothingFit,
  productMerchantCount,
  productPriceFrom,
  productsByIds,
  productsForClothingFit,
  productsForLocation,
  productsForPreferences,
  readStorage,
  reliableRemoteCartSubtotal,
  reliableRemoteCartTotal,
  resolveAsk,
  resolveReply,
  selectedCartDeliveryOption,
  writeStorage,
} from './utils'
import type { CartDeliveryGroup, CartDeliveryOption, CartItem, CartLine, Product } from './types'

const product: Product = {
  id: 'product-1',
  name: 'Test product',
  brand: 'Test',
  category: 'home',
  tone: 'neutral',
  match: 80,
  priceFrom: 20,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: '',
  pros: [],
  cons: [],
  review: {
    score: 4.5,
    count: 10,
    insight: '',
  },
  offers: [],
}

function option(handle: string, amount: string, selected: boolean): CartDeliveryOption {
  return {
    handle,
    title: handle,
    cost: {
      amount,
      currency: 'USD',
    },
    selected,
  }
}

function group(
  id: string,
  options: readonly CartDeliveryOption[],
  selected?: CartDeliveryOption,
): CartDeliveryGroup {
  return {
    id,
    deliveryOptions: options,
    selectedDeliveryOption: selected ?? null,
  }
}

function line(deliveryGroups: readonly CartDeliveryGroup[]): CartLine {
  return {
    id: 'product-1',
    merchant: 'Review Merchant',
    qty: 1,
    product,
    price: 20,
    delivery: 'standard',
    deliveryGroups,
  }
}

function productWith(overrides: Partial<Product>): Product {
  return {
    ...product,
    id: (overrides.id ?? product.id) as Product['id'],
    name: overrides.name ?? product.name,
    brand: overrides.brand ?? product.brand,
    category: overrides.category ?? product.category,
    tone: overrides.tone ?? product.tone,
    match: overrides.match ?? product.match,
    priceFrom: overrides.priceFrom ?? product.priceFrom,
    merchants: overrides.merchants ?? product.merchants,
    satisfies: overrides.satisfies ?? product.satisfies,
    misses: overrides.misses ?? product.misses,
    note: overrides.note ?? product.note,
    pros: overrides.pros ?? product.pros,
    cons: overrides.cons ?? product.cons,
    review: overrides.review ?? product.review,
    offers: overrides.offers ?? product.offers,
    needs: overrides.needs,
    provides: overrides.provides,
    audiences: overrides.audiences,
  }
}

describe('formatting and lookup utilities', () => {
  test('formats money, joins lists, and resolves preference labels', () => {
    expect(money(7)).toBe('$7.00')
    expect(money(7.456)).toBe('$7.46')
    expect(listJoin([])).toBe('nothing')
    expect(listJoin(['cotton'])).toBe('cotton')
    expect(listJoin(['organic', 'cotton', 'wool'])).toBe('organic, cotton and wool')
    expect(prefLabel(PREFERENCES, 'organic')).toBe('Organic')
    expect(prefLabel(PREFERENCES, 'custom-preference')).toBe('custom-preference')
  })

  test('finds products by id and rejects unknown ids', () => {
    expect(productById('cereal').name).toBe('Sprouted Oat & Almond Cereal')
    expect(productsByIds(['cereal', 'tee']).map((candidate) => candidate.id)).toEqual([
      'cereal',
      'tee',
    ])
    expect(() => productById('missing')).toThrow('Unknown product id: missing')
  })

  test('normalizes merchant identity and builds stable cart keys', () => {
    expect(normalizedMerchantName(' Whole Foods ')).toBe('whole foods')
    expect(
      cartMerchantKey({
        merchant: 'Whole Foods',
        merchantId: 'merchant-1',
        merchantDomain: 'whole.test',
      }),
    ).toBe('merchant-1')
    expect(cartMerchantKey({ merchant: 'Whole Foods', merchantDomain: ' whole.test ' })).toBe(
      'whole.test',
    )
    expect(cartMerchantKey({ merchant: 'Whole Foods' })).toBe('whole foods')
  })

  test('firstUrl returns the first non-empty trimmed URL', () => {
    expect(firstUrl(null, undefined, '', '   ', ' https://checkout.example/cart ')).toBe(
      'https://checkout.example/cart',
    )
    expect(firstUrl('', ' \t ')).toBeNull()
  })

  test('formats valid order dates and preserves invalid input', () => {
    expect(formatOrderDate('2026-06-11')).toBe('Jun 11, 2026')
    expect(formatOrderDate('not-a-date')).toBe('not-a-date')
  })

  test('recognizes core preference ids', () => {
    expect(isCorePreferenceId(DEFAULT_PREFERENCE_IDS[0])).toBe(true)
    expect(isCorePreferenceId('custom-handmade')).toBe(false)
  })
})

describe('shopping decision utilities', () => {
  test('checks merchant shipping coverage by country and city', () => {
    expect(
      canMerchantShip('Unknown Merchant', [{ country: 'France', code: 'FR', city: 'Paris' }]),
    ).toBe(true)
    expect(canMerchantShip('Whole Foods', [])).toBe(true)
    expect(
      canMerchantShip('Whole Foods', [{ country: 'United States', code: 'US', city: 'Seattle' }]),
    ).toBe(true)
    expect(
      canMerchantShip('Whole Foods', [{ country: 'United States', code: 'US', city: 'Miami' }]),
    ).toBe(false)
  })

  test('filters offers and products for delivery location', () => {
    const uk = [{ country: 'United Kingdom', code: 'UK', city: 'London' }]
    const cereal = productById('cereal')
    const usOnly = productWith({
      id: 'us-only',
      offers: [{ merchant: 'Whole Foods', price: 10, delivery: 'Tomorrow' }],
    })

    expect(availableOffers(cereal, uk).map((offer) => offer.merchant)).toEqual(['iHerb'])
    expect(productsForLocation([cereal, usOnly], uk).map((candidate) => candidate.id)).toEqual([
      'cereal',
    ])
  })

  test('filters and sorts products by preferences', () => {
    const activePreferences = PREFERENCES.filter((preference) =>
      ['natural-materials', 'no-polyester'].includes(preference.id),
    )

    expect(
      productsForPreferences(
        [productById('runners'), productById('sweater'), productById('tee')],
        activePreferences,
      ).map((candidate) => candidate.id),
    ).toEqual(['tee', 'sweater'])
    expect(productsForPreferences([productById('runners')], [])).toEqual([productById('runners')])
  })

  test('matches clothing fit audiences', () => {
    expect(productMatchesClothingFit(productById('sweater'), 'women')).toBe(true)
    expect(productMatchesClothingFit(productById('sweater'), 'men')).toBe(false)
    expect(productMatchesClothingFit(productById('tee'), 'men')).toBe(true)
    expect(productMatchesClothingFit(productById('cereal'), 'men')).toBe(true)
    expect(
      productsForClothingFit([productById('sweater'), productById('tee')], 'men').map(
        (candidate) => candidate.id,
      ),
    ).toEqual(['tee'])
  })

  test('uses shippable offers for price, merchant count, and best offer', () => {
    const cereal = productById('cereal')
    const uk = [{ country: 'United Kingdom', code: 'UK', city: 'London' }]

    expect(productPriceFrom(cereal, uk)).toBe(8.2)
    expect(productMerchantCount(cereal, uk)).toBe(1)
    expect(bestOffer(cereal, uk)).toEqual({ merchant: 'iHerb', price: 8.2, delivery: '3 days' })
  })
})

describe('assistant and preference utilities', () => {
  test('resolves canned replies by query intent', () => {
    expect(resolveReply('healthy breakfast cereal')).toBe(REPLIES.cereal)
    expect(resolveReply('cotton shirt')).toBe(REPLIES.tee)
    expect(resolveReply('coffee brewer')).toBe(REPLIES.brewer)
    expect(resolveReply('surprise me')).toBe(REPLIES.default)
  })

  test('derives known and custom filters from free text', () => {
    const result = deriveFilters('organic, no polyester, I prefer handmade mugs')

    expect(result.matched).toEqual(['organic', 'no-polyester'])
    expect(result.customs).toEqual([
      {
        id: 'custom-handmade-mugs',
        label: 'Handmade mugs',
        desc: 'Added from your own description.',
      },
    ])
  })

  test('answers product and global ask prompts', () => {
    expect(resolveAsk('Is this a good match for me?', productById('tee'), PREFERENCES)).toContain(
      "It's a 94% match",
    )
    expect(resolveAsk('What is the price?', productById('tee'), PREFERENCES)).toContain(
      'Best price is $38.00 at Field & Loom',
    )
    expect(resolveAsk('compare these', null, PREFERENCES)).toBe(
      'Open Compare and I will line products up against every preference you care about.',
    )
  })
})

describe('cart recovery helpers', () => {
  test('detects stale-cart ApiError by status or code only', () => {
    expect(isCartNotFoundError(new ApiError('missing', 404, 'bad_request'))).toBe(true)
    expect(isCartNotFoundError(new ApiError('scrubbed', 400, 'not_found'))).toBe(true)
    expect(isCartNotFoundError(new ApiError('cart not found', 400, 'bad_request'))).toBe(false)
    expect(isCartNotFoundError(new Error('cart not found'))).toBe(false)
    expect(isCartNotFoundError(undefined)).toBe(false)
    expect(isCartNotFoundError(null)).toBe(false)
    expect(isCartNotFoundError({})).toBe(false)
  })

  test('builds merchant cart rebuild items from cart state', () => {
    const items: CartItem[] = [
      {
        id: 'cereal',
        merchant: 'Whole Foods',
        merchantId: 'merchant-1',
        qty: 2,
        productVariantId: 'variant-1',
      },
      {
        id: 'oil',
        merchant: 'Whole Foods',
        merchantId: 'merchant-1',
        qty: 0,
        productVariantId: 'variant-2',
      },
      {
        id: 'tee',
        merchant: 'Field & Loom',
        merchantId: 'merchant-2',
        qty: 3,
        productVariantId: 'variant-3',
      },
      { id: 'missing', merchant: 'Whole Foods', merchantId: 'merchant-1', qty: 4 },
    ]

    expect(cartRebuildItems(items, 'merchant-1')).toEqual([
      { productVariantId: 'variant-1', quantity: 2 },
      { productVariantId: 'variant-2', quantity: 1 },
    ])
    expect(cartRebuildItems(items, 'merchant-3')).toEqual([])
  })

  test('merges cart snapshots only into matching merchant items', () => {
    const oldGroup = group('old', [option('old-standard', '4.00', true)])
    const newGroup = group('new', [option('new-standard', '5.00', true)])
    const cart: CartItem[] = [
      {
        id: 'cereal',
        merchant: 'Whole Foods',
        merchantId: 'merchant-1',
        qty: 2,
        productVariantId: 'variant-1',
        cartId: 'old-cart',
        checkoutUrl: 'https://old.example/checkout',
        continueUrl: 'https://old.example/continue',
        deliveryGroups: [oldGroup],
        syncing: true,
        syncError: 'stale',
      },
      {
        id: 'tee',
        merchant: 'Field & Loom',
        merchantId: 'merchant-2',
        qty: 1,
        productVariantId: 'variant-2',
        cartId: 'other-cart',
        syncing: true,
      },
    ]
    const snapshot = {
      cartId: 'new-cart',
      remoteCartId: 'remote-new',
      merchantId: 'merchant-1',
      merchantDomain: 'whole.example',
      checkoutUrl: 'https://new.example/checkout',
      continueUrl: 'https://new.example/continue',
      totalAmount: '25.00',
      subtotalAmount: '20.00',
      currency: 'USD',
      deliveryGroups: [null, newGroup],
      lines: [
        {
          cartLineId: 'line-new',
          remoteCartLineId: 'remote-line-new',
          productVariantId: 'variant-1',
          variantTitle: 'Large',
          quantity: 5,
        },
      ],
    } as unknown as CartProfile

    const merged = mergeCartSnapshot(cart, 'merchant-1', snapshot)

    expect(merged[1]).toBe(cart[1])
    expect(merged[0]).toMatchObject({
      merchantId: 'merchant-1',
      merchantDomain: 'whole.example',
      cartId: 'new-cart',
      remoteCartId: 'remote-new',
      checkoutUrl: 'https://new.example/checkout',
      continueUrl: 'https://new.example/continue',
      cartLineId: 'line-new',
      remoteCartLineId: 'remote-line-new',
      productVariantId: 'variant-1',
      variantTitle: 'Large',
      cartTotalAmount: '25.00',
      cartSubtotalAmount: '20.00',
      cartCurrency: 'USD',
      qty: 5,
      syncing: false,
      syncError: null,
    })
    expect(merged[0].deliveryGroups).toEqual([newGroup])
  })

  test('falls back to existing item data when snapshot fields are absent', () => {
    const oldGroup = group('old', [option('old-standard', '4.00', true)])
    const cart: CartItem[] = [
      {
        id: 'cereal',
        merchant: 'Whole Foods',
        merchantId: 'merchant-1',
        qty: 2,
        productVariantId: 'variant-1',
        cartId: 'old-cart',
        checkoutUrl: 'https://old.example/checkout',
        continueUrl: 'https://old.example/continue',
        deliveryGroups: [oldGroup],
        syncing: true,
        syncError: 'stale',
      },
    ]
    const merged = mergeCartSnapshot(cart, 'merchant-1', {
      lines: [{ productVariantId: 'variant-1' }],
      deliveryGroups: [],
    } as unknown as CartProfile)

    expect(merged[0]).toMatchObject({
      cartId: 'old-cart',
      checkoutUrl: 'https://old.example/checkout',
      continueUrl: 'https://old.example/continue',
      qty: 2,
      syncing: false,
      syncError: null,
    })
    expect(merged[0].deliveryGroups).toEqual([oldGroup])
  })
})

describe('cart and order utilities', () => {
  test('maps cart items to product-backed cart lines and skips unknown products', () => {
    const lines = cartLines(
      [
        { id: 'cereal', merchant: 'Whole Foods', qty: 2 },
        { id: 'missing', merchant: 'Nowhere', qty: 1 },
      ],
      PRODUCTS,
    )

    expect(lines).toHaveLength(1)
    expect(lines[0]).toMatchObject({
      id: 'cereal',
      merchant: 'Whole Foods',
      qty: 2,
      price: 7.4,
      delivery: 'Tomorrow',
    })
  })

  test('computes smart alerts for connection warnings and fixes', () => {
    const laptopLine = cartLines([{ id: 'laptop', merchant: 'Lumen Store', qty: 1 }], PRODUCTS)[0]
    const driveLine = cartLines([{ id: 'drive', merchant: 'Hold Store', qty: 1 }], PRODUCTS)[0]

    const alerts = computeSmartAlerts([laptopLine, driveLine], PRODUCTS)

    expect(alerts[0]).toMatchObject({
      kind: 'warn',
      title: 'Connection mismatch',
      fix: {
        id: 'adapter',
        merchant: 'Lumen Store',
      },
    })
  })

  test('computes smart alerts for compatible accessories', () => {
    const lines = cartLines(
      [
        { id: 'laptop', merchant: 'Lumen Store', qty: 1 },
        { id: 'adapter', merchant: 'Lumen Store', qty: 1 },
        { id: 'drive', merchant: 'Hold Store', qty: 1 },
      ],
      PRODUCTS,
    )

    expect(computeSmartAlerts(lines, PRODUCTS).some((alert) => alert.kind === 'good')).toBe(true)
  })

  test('calculates order totals and creates processing orders from checkout payloads', () => {
    expect(
      orderTotal({
        id: 'MNT-1',
        date: '2026-06-01',
        status: 'Delivered',
        statusNote: '',
        items: [{ id: 'cereal', merchant: 'Whole Foods', qty: 2 }],
        saved: 1.48,
        savedNote: '',
      }),
    ).toBe(13.32)

    const order = createOrder({
      items: [{ id: 'tee', merchant: 'Field & Loom', qty: 1 }],
      saved: 1.235,
      savedNote: 'Rounded',
    })

    expect(order.id).toMatch(/^MNT-\d{4}$/)
    expect(order.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(order.status).toBe('Processing')
    expect(order.saved).toBe(1.24)
    expect(order.savedNote).toBe('Rounded')
  })

  test('reads and writes storage safely', () => {
    const originalWindow = globalThis.window
    const store = new Map<string, string>()
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: {
        localStorage: {
          getItem: (key: string) => store.get(key) ?? null,
          setItem: (key: string, value: string) => {
            store.set(key, value)
          },
        },
      } as unknown as Window,
    })

    try {
      writeStorage('meant.test', { ok: true })
      expect(readStorage('meant.test', { ok: false })).toEqual({ ok: true })
      store.set('meant.test', '{bad json')
      expect(readStorage('meant.test', { ok: false })).toEqual({ ok: false })
    } finally {
      Object.defineProperty(globalThis, 'window', {
        configurable: true,
        value: originalWindow,
      })
    }
  })
})

describe('cart delivery groups', () => {
  test('ignores stale zero remote totals when cart lines have prices', () => {
    const [cartGroup] = cartGroups([
      {
        ...line([]),
        price: 35,
        cartSubtotalAmount: '0.00',
        cartTotalAmount: '0.00',
      },
    ])

    expect(cartGroup.remoteSubtotal).toBeNull()
    expect(cartGroup.remoteTotal).toBeNull()
    expect(cartGroup.subtotal).toBe(35)
    expect(cartGroup.total).toBe(39.99)
  })

  test('ignores implausible remote totals when they do not match cart lines', () => {
    const [cartGroup] = cartGroups([
      {
        ...line([]),
        price: 35,
        cartSubtotalAmount: '752.00',
        cartTotalAmount: '752.00',
      },
    ])

    expect(cartGroup.remoteSubtotal).toBeNull()
    expect(cartGroup.remoteTotal).toBeNull()
    expect(cartGroup.subtotal).toBe(35)
    expect(cartGroup.total).toBe(39.99)
  })

  test('keeps plausible remote cart totals', () => {
    const [cartGroup] = cartGroups([
      {
        ...line([]),
        price: 35,
        cartSubtotalAmount: '35.00',
        cartTotalAmount: '42.00',
      },
    ])

    expect(cartGroup.remoteSubtotal).toBe(35)
    expect(cartGroup.remoteTotal).toBe(42)
    expect(cartGroup.total).toBe(42)
  })

  test('infers delivery from reliable remote total without remote subtotal', () => {
    const [cartGroup] = cartGroups([
      {
        ...line([]),
        price: 35,
        cartTotalAmount: '42.00',
      },
    ])

    expect(cartGroup.remoteSubtotal).toBeNull()
    expect(cartGroup.remoteTotal).toBe(42)
    expect(cartGroup.subtotal).toBe(35)
    expect(cartGroup.deliveryRaw).toBe(7)
    expect(cartGroup.total).toBe(42)
  })

  test('ignores negative remote cart amounts', () => {
    const [cartGroup] = cartGroups([
      {
        ...line([]),
        price: 35,
        cartSubtotalAmount: '-35.00',
        cartTotalAmount: '-42.00',
      },
    ])

    expect(cartGroup.remoteSubtotal).toBeNull()
    expect(cartGroup.remoteTotal).toBeNull()
    expect(cartGroup.subtotal).toBe(35)
    expect(cartGroup.total).toBeCloseTo(39.99)
  })

  test('rejects invalid remote cart amounts before zero-baseline fallback', () => {
    expect(reliableRemoteCartSubtotal(-35, 0)).toBeNull()
    expect(reliableRemoteCartTotal(-42, 0)).toBeNull()
    expect(reliableRemoteCartTotal(0, 0)).toBeNull()
    expect(reliableRemoteCartSubtotal(35, 0)).toBe(35)
    expect(reliableRemoteCartTotal(42, 0)).toBe(42)
  })

  test('requires every shipment with options to have a selected delivery option', () => {
    const selected = option('standard', '5.00', true)
    const unselected = option('express', '8.00', false)

    const [cartGroup] = cartGroups([
      line([group('shipment-1', [selected], selected), group('shipment-2', [unselected])]),
    ])

    expect(cartGroup.hasDeliveryOptions).toBe(true)
    expect(cartGroup.hasSelectedDelivery).toBe(false)
    expect(cartGroup.deliveryRaw).toBe(4.99)
  })

  test('uses selected delivery cost only after all selectable shipments are selected', () => {
    const standard = option('standard', '5.00', true)
    const economy = option('economy', '3.00', true)

    const [cartGroup] = cartGroups([
      line([group('shipment-1', [standard], standard), group('shipment-2', [economy], economy)]),
    ])

    expect(cartGroup.hasSelectedDelivery).toBe(true)
    expect(cartGroup.deliveryRaw).toBe(8)
    expect(cartGroup.total).toBe(28)
  })

  test('does not require selection for shipment groups without delivery options', () => {
    const standard = option('standard', '5.00', true)

    const [cartGroup] = cartGroups([
      line([group('shipment-1', []), group('shipment-2', [standard], standard)]),
    ])

    expect(cartGroup.hasSelectedDelivery).toBe(true)
    expect(cartGroup.deliveryRaw).toBe(5)
  })

  test('filters null delivery options at the cart boundary', () => {
    const standard = option('standard', '5.00', true)
    const deliveryGroup = {
      id: 'shipment-1',
      deliveryOptions: [null, standard],
    } as unknown as CartDeliveryGroup

    expect(cartDeliveryOptions(deliveryGroup)).toEqual([standard])
  })

  test('uses explicit selected delivery options and parses delivery costs', () => {
    const explicit = option('express', '12.50', false)
    const deliveryGroup = group('shipment-1', [option('standard', '5.00', true)], explicit)

    expect(selectedCartDeliveryOption(deliveryGroup)).toBe(explicit)
    expect(cartDeliveryOptionAmount(explicit)).toBe(12.5)
    expect(
      cartDeliveryOptionAmount({ cost: { amount: 'not-a-number', currency: 'USD' } }),
    ).toBeNull()
  })
})

describe('product category display values', () => {
  test('hides Shopify taxonomy ids and placeholder values', () => {
    expect(displayProductCategoryValue('gid://shopify/TaxonomyCategory/na')).toBeNull()
    expect(displayProductCategoryValue(' na ')).toBeNull()
  })

  test('keeps readable category labels', () => {
    expect(displayProductCategoryValue(' Groceries ')).toBe('Groceries')
  })
})
