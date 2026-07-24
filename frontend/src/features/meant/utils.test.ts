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
  cartItemIdentity,
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
import type {
  CartDeliveryGroup,
  CartDeliveryOption,
  CartItem,
  CartLine,
  Product,
  UserLocation,
} from './types'

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

function location(country: string, code: string, city: string): UserLocation {
  const canonicalCode = code === 'UK' ? 'GB' : code
  return {
    id: `test:${canonicalCode}:${city}`,
    country,
    code: canonicalCode,
    region: null,
    postalCode: null,
    regionName: null,
    city,
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
    expect(
      cartMerchantKey({
        merchant: 'Same display name',
        merchantScopeKey: 'shopify:external:merchant:shop-1',
      }),
    ).toBe('shopify:external:merchant:shop-1')
  })

  test('addresses sibling variants by exact offer or confirmed cart line identity', () => {
    const base = { id: 'tee', merchant: 'Merchant', qty: 1 }

    expect(cartItemIdentity({ ...base, offerKey: 'offer-medium' })).not.toBe(
      cartItemIdentity({ ...base, offerKey: 'offer-large' }),
    )
    expect(cartItemIdentity({ ...base, cartLineId: 'line-medium' })).not.toBe(
      cartItemIdentity({ ...base, cartLineId: 'line-large' }),
    )
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
    expect(canMerchantShip('Unknown Merchant', [location('France', 'FR', 'Paris')])).toBe(true)
    expect(canMerchantShip('Whole Foods', [])).toBe(true)
    expect(canMerchantShip('Whole Foods', [location('United States', 'US', 'Seattle')])).toBe(true)
    expect(canMerchantShip('Whole Foods', [location('United States', 'US', 'Miami')])).toBe(false)
  })

  test('filters offers and products for delivery location', () => {
    const uk = [location('United Kingdom', 'GB', 'London')]
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
    const uk = [location('United Kingdom', 'GB', 'London')]

    expect(productPriceFrom(cereal, uk)).toBe(8.2)
    expect(productMerchantCount(cereal, uk)).toBe(1)
    expect(bestOffer(cereal, uk)).toEqual({ merchant: 'iHerb', price: 8.2, delivery: '3 days' })
  })

  test('returns no best offer when current product offers are unavailable', () => {
    expect(bestOffer(product, [])).toBeNull()
  })

  test('prefers a priced offer when an exact saved offer has no display price', () => {
    const mixed = {
      ...product,
      offers: [
        { offerKey: 'saved-no-price', merchant: 'Unknown price', price: Number.NaN, delivery: '' },
        { offerKey: 'saved-priced', merchant: 'Priced', price: 15, delivery: 'Tomorrow' },
      ],
    }

    expect(bestOffer(mixed, [])?.offerKey).toBe('saved-priced')
    expect(productPriceFrom(mixed, [])).toBe(15)
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

  test('answers price and delivery questions safely when offers are unavailable', () => {
    expect(resolveAsk('What is the price?', product, PREFERENCES)).toContain(
      'prices are unavailable',
    )
    expect(resolveAsk('When can it arrive?', product, PREFERENCES)).toContain(
      'delivery options are unavailable',
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
        offerKey: 'offer-1',
      },
      {
        id: 'oil',
        merchant: 'Whole Foods',
        merchantId: 'merchant-1',
        qty: 0,
        productVariantId: 'variant-2',
        offerKey: 'offer-2',
      },
      {
        id: 'tee',
        merchant: 'Field & Loom',
        merchantId: 'merchant-2',
        qty: 3,
        productVariantId: 'variant-3',
        offerKey: 'offer-3',
      },
      { id: 'missing', merchant: 'Whole Foods', merchantId: 'merchant-1', qty: 4 },
    ]

    expect(cartRebuildItems(items, 'merchant-1')).toEqual([
      { offerKey: 'offer-1', quantity: 2 },
      { offerKey: 'offer-2', quantity: 1 },
    ])
    expect(cartRebuildItems(items, 'merchant-3')).toEqual([])
  })

  test('merges cart snapshots only into matching merchant items', () => {
    const oldGroup = group('old', [option('old-standard', '4.00', true)])
    const newGroup = group('new', [option('new-standard', '5.00', true)])
    const cart: CartItem[] = [
      {
        id: 'cereal',
        merchant: 'sollys-online-grocery.myshopify.com',
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
      merchantDomain: 'nycfactory.com',
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
      merchant: 'sollys-online-grocery.myshopify.com',
      merchantOrigin: 'nycfactory.com',
      merchantId: 'merchant-1',
      merchantDomain: 'nycfactory.com',
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

  test('binds a grouped cart line by the exact server-issued offer key', () => {
    const cart: CartItem[] = [
      {
        id: 'canonical-product',
        merchant: 'Merchant',
        offerKey: 'offer-exact',
        qty: 1,
        syncing: true,
      },
    ]
    const snapshot = {
      cartId: 'cart-1',
      merchantId: 'merchant-1',
      merchantDomain: 'merchant.example',
      remoteCartId: 'remote-cart-1',
      lines: [
        {
          cartLineId: 'line-1',
          remoteCartLineId: 'remote-line-1',
          offerKey: 'offer-exact',
          productVariantId: 'server-variant',
          quantity: 1,
        },
      ],
      deliveryGroups: [],
    } as unknown as CartProfile

    expect(mergeCartSnapshot(cart, 'merchant', snapshot)[0]).toMatchObject({
      offerKey: 'offer-exact',
      productVariantId: 'server-variant',
      cartLineId: 'line-1',
      syncing: false,
      syncError: null,
    })
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

  test('skips a cart line whose current product has no offer instead of throwing', () => {
    expect(cartLines([{ id: product.id, merchant: 'Unavailable', qty: 1 }], [product])).toEqual([])
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

  test('totals exact variants without falling back to a same-name sibling offer', () => {
    const variantProduct = productWith({
      id: 'variant-order-product',
      offers: [
        {
          offerKey: 'offer-small',
          productVariantId: 'variant-small',
          merchant: 'Shared merchant',
          price: 10,
          delivery: 'Standard',
        },
        {
          offerKey: 'offer-large',
          productVariantId: 'variant-large',
          merchant: 'Shared merchant',
          price: 18,
          delivery: 'Standard',
        },
      ],
    })

    expect(
      orderTotal(
        {
          id: 'MNT-variant',
          date: '2026-07-14',
          status: 'Processing',
          statusNote: '',
          items: [
            {
              id: variantProduct.id,
              merchant: 'Shared merchant',
              offerKey: 'offer-small',
              unitPriceAmount: '7.50',
              qty: 2,
            },
            {
              id: variantProduct.id,
              merchant: 'Shared merchant',
              offerKey: 'offer-large',
              qty: 1,
            },
            {
              id: variantProduct.id,
              merchant: 'Shared merchant',
              productVariantId: 'variant-small',
              qty: 2,
            },
            {
              id: variantProduct.id,
              merchant: 'Shared merchant',
              offerKey: 'missing-exact-offer',
              qty: 1,
            },
            { id: variantProduct.id, merchant: 'Shared merchant', qty: 1 },
          ],
          saved: 0,
          savedNote: '',
        },
        [variantProduct],
      ),
    ).toBe(63)
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
  test('keeps same-name merchants in separate authoritative cart scopes', () => {
    const groups = cartGroups([
      {
        ...line([]),
        merchant: 'Shared Store',
        merchantScopeKey: 'shopify:external:merchant:shared-store',
      },
      {
        ...line([]),
        merchant: 'Shared Store',
        merchantScopeKey: 'etsy:external:merchant:shared-store',
      },
    ])

    expect(groups).toHaveLength(2)
    expect(groups.map((cartGroup) => cartGroup.merchantKey)).toEqual([
      'shopify:external:merchant:shared-store',
      'etsy:external:merchant:shared-store',
    ])
    expect(groups.map((cartGroup) => cartGroup.merchant)).toEqual(['Shared Store', 'Shared Store'])
    expect(groups.every((cartGroup) => cartGroup.items.length === 1)).toBe(true)
  })

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
    expect(displayProductCategoryValue('shopify://taxonomy/category/aa-1')).toBeNull()
    expect(displayProductCategoryValue('urn:shopify:taxonomy:category:aa-1')).toBeNull()
    expect(displayProductCategoryValue(' na ')).toBeNull()
    expect(displayProductCategoryValue('not_applicable')).toBeNull()
    expect(displayProductCategoryValue('undefined')).toBeNull()
  })

  test('keeps readable category labels', () => {
    expect(displayProductCategoryValue(' Groceries ')).toBe('Groceries')
  })
})
