import { afterEach, beforeEach, describe, expect, mock, test } from 'bun:test'

let authenticatedUserId = 'user-a'

mock.module('./supabase', () => ({
  supabase: {
    auth: {
      getSession: async () => ({
        data: {
          session: { access_token: 'test-token', user: { id: authenticatedUserId } },
        },
      }),
    },
  },
}))

const {
  acknowledgeEmbeddedCheckoutOpened,
  bindSelectedOfferToCart,
  bootstrapEmbeddedCheckout,
  cancelEmbeddedCheckout,
  completeEmbeddedCheckout,
  createCart,
  createUserInventoryItem,
  deleteUserProductSearchPreference,
  getCartCheckout,
  getSavedProduct,
  rehydrateCanonicalProducts,
  searchLocationSuggestions,
  searchSimilarGroupedProducts,
  searchDiscountCodes,
  searchGroupedProducts,
  selectProductVariant,
  updateUserSettings,
  updateUserTasteSignal,
  updateCart,
  updateCartCheckout,
  validateInventoryPhotoFile,
} = await import('./apiClient')
const originalFetch = globalThis.fetch
let requests: Request[] = []
let requestKeepalive: Array<boolean | undefined> = []

beforeEach(() => {
  authenticatedUserId = 'user-a'
  requests = []
  requestKeepalive = []
  globalThis.fetch = mock(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = new Request(input, init)
    requests.push(request)
    requestKeepalive.push(init?.keepalive)
    if (request.url.endsWith('/cancel') || request.url.endsWith('/opened')) {
      return new Response(null, { status: 204 })
    }
    if (request.url.endsWith('/api/v1/users/me/product-searches')) {
      return Response.json({ products: [] })
    }
    if (request.url.includes('/api/locations/suggestions')) {
      return Response.json({
        suggestions: [],
        attribution: 'GeoNames',
        attributionUrl: 'https://www.geonames.org/',
      })
    }
    return Response.json({ cartId: 'cart-1', lines: [] })
  }) as unknown as typeof fetch
})

describe('embedded checkout session API', () => {
  test('uses opaque server session routes without browser checkout authority', async () => {
    await bootstrapEmbeddedCheckout('cart-1')
    await acknowledgeEmbeddedCheckoutOpened({ cartId: 'cart-1', sessionId: 'session-1' })
    await completeEmbeddedCheckout({ cartId: 'cart-1', sessionId: 'session-1' })
    await cancelEmbeddedCheckout({ cartId: 'cart-1', sessionId: 'session-1' })

    expect(requests.map((request) => request.method)).toEqual(['POST', 'POST', 'POST', 'POST'])
    expect(requests.map((request) => request.url)).toEqual([
      'http://localhost:8080/api/carts/cart-1/checkout/embedded',
      'http://localhost:8080/api/carts/cart-1/checkout/embedded/session-1/opened',
      'http://localhost:8080/api/carts/cart-1/checkout/embedded/session-1/complete',
      'http://localhost:8080/api/carts/cart-1/checkout/embedded/session-1/cancel',
    ])
    expect(requestKeepalive[1]).toBe(true)
    for (const request of requests) {
      expect(await request.text()).toBe('')
    }
  })
})

afterEach(() => {
  globalThis.fetch = originalFetch
})

describe('bindSelectedOfferToCart', () => {
  test('creates a cart with only exact offer identity and quantity', async () => {
    await bindSelectedOfferToCart({ offerKey: 'offer-exact', quantity: 2 })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('POST')
    expect(await requests[0]?.json()).toEqual({
      addItems: [{ offerKey: 'offer-exact', quantity: 2 }],
    })
  })

  test('adds the exact offer to an existing cart without browser routing facts', async () => {
    await bindSelectedOfferToCart({ offerKey: 'offer-exact', cartId: 'cart-1' })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('PATCH')
    expect(requests[0]?.url).toEndWith('/api/carts/cart-1')
    expect(await requests[0]?.json()).toEqual({
      addItems: [{ offerKey: 'offer-exact', quantity: 1 }],
    })
  })
})

describe('cart request API', () => {
  test('creates a cart with typed buyer, fulfillment, code, and note fields', async () => {
    await createCart({
      addItems: [{ offerKey: 'offer-exact', quantity: 2 }],
      buyerIdentity: {
        email: 'ada@example.com',
        phoneNumber: '+14155552671',
        firstName: 'Ada',
        lastName: 'Lovelace',
        countryCode: 'US',
      },
      deliveryAddressesToAdd: [
        {
          methodId: 'shipping',
          id: 'home',
          selected: true,
          addressLocality: 'New York',
          addressRegion: 'NY',
          postalCode: '10001',
          addressCountry: 'US',
        },
      ],
      selectedDeliveryOptions: [
        {
          methodId: 'shipping',
          groupId: 'delivery-group-1',
          selectedOptionId: 'express',
        },
      ],
      discountCodes: ['SAVE5'],
      giftCardCodes: ['GIFT10'],
      note: 'Leave at reception',
    })

    expect(await requests[0]?.json()).toEqual({
      addItems: [{ offerKey: 'offer-exact', quantity: 2 }],
      buyerIdentity: {
        email: 'ada@example.com',
        phoneNumber: '+14155552671',
        firstName: 'Ada',
        lastName: 'Lovelace',
        countryCode: 'US',
      },
      deliveryAddressesToAdd: [
        {
          methodId: 'shipping',
          id: 'home',
          selected: true,
          addressLocality: 'New York',
          addressRegion: 'NY',
          postalCode: '10001',
          addressCountry: 'US',
        },
      ],
      selectedDeliveryOptions: [
        {
          methodId: 'shipping',
          groupId: 'delivery-group-1',
          selectedOptionId: 'express',
        },
      ],
      discountCodes: ['SAVE5'],
      giftCardCodes: ['GIFT10'],
      note: 'Leave at reception',
    })
  })

  test('updates a cart with typed replacement fulfillment and buyer note fields', async () => {
    await updateCart({
      cartId: 'cart-1',
      buyerIdentity: { email: 'grace@example.com' },
      deliveryAddressesToReplace: [
        {
          methodId: 'shipping',
          id: 'office',
          streetAddress: '1 Market St',
          addressLocality: 'San Francisco',
          postalCode: '94105',
          addressCountry: 'US',
        },
      ],
      selectedDeliveryOptions: [{ groupId: 'delivery-group-1', selectedOptionId: 'standard' }],
      note: 'Ring the bell',
    })

    expect(requests[0]?.method).toBe('PATCH')
    expect(await requests[0]?.json()).toEqual({
      buyerIdentity: { email: 'grace@example.com' },
      deliveryAddressesToReplace: [
        {
          methodId: 'shipping',
          id: 'office',
          streetAddress: '1 Market St',
          addressLocality: 'San Francisco',
          postalCode: '94105',
          addressCountry: 'US',
        },
      ],
      selectedDeliveryOptions: [{ groupId: 'delivery-group-1', selectedOptionId: 'standard' }],
      note: 'Ring the bell',
    })
  })

  test('reuses saved buyer and address details through the authenticated checkout PATCH', async () => {
    await updateCartCheckout({
      cartId: 'cart/saved',
      expectedUserId: 'user-a',
      buyer: {
        email: 'ada@example.com',
        firstName: 'Ada',
        lastName: 'Lovelace',
        phoneNumber: '+14155552671',
      },
      shippingAddress: {
        streetAddress: '1 Market St',
        extendedAddress: 'Suite 200',
        addressLocality: 'San Francisco',
        addressRegion: 'CA',
        postalCode: '94105',
        addressCountry: 'US',
      },
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('PATCH')
    expect(requests[0]?.url).toBe('http://localhost:8080/api/carts/cart%2Fsaved/checkout')
    expect(requests[0]?.headers.get('Authorization')).toBe('Bearer test-token')
    expect(requests[0]?.headers.get('Content-Type')).toBe('application/json')
    expect(await requests[0]?.json()).toEqual({
      buyer: {
        email: 'ada@example.com',
        firstName: 'Ada',
        lastName: 'Lovelace',
        phoneNumber: '+14155552671',
      },
      shippingAddress: {
        streetAddress: '1 Market St',
        extendedAddress: 'Suite 200',
        addressLocality: 'San Francisco',
        addressRegion: 'CA',
        postalCode: '94105',
        addressCountry: 'US',
      },
    })
  })
})

describe('discount search request API', () => {
  test('uses the endpoint-specific generated buyer and fulfillment field names', async () => {
    await searchDiscountCodes({
      merchantDomain: 'merchant.example',
      items: [{ productVariantId: 'variant-1', quantity: 1 }],
      buyerIdentity: { countryCode: 'US' },
      deliveryAddressesToAdd: [
        {
          id: 'home',
          selected: true,
          city: 'New York',
          provinceCode: 'NY',
          postalCode: '10001',
          countryCode: 'US',
        },
      ],
      selectedDeliveryOptions: [
        {
          groupId: 'delivery-group-1',
          selectedOptionId: 'express',
        },
      ],
    })

    expect(requests[0]?.url).toEndWith('/api/discounts/search')
    expect(await requests[0]?.json()).toEqual({
      merchantDomain: 'merchant.example',
      items: [{ productVariantId: 'variant-1', quantity: 1 }],
      buyerIdentity: { countryCode: 'US' },
      deliveryAddressesToAdd: [
        {
          id: 'home',
          selected: true,
          city: 'New York',
          provinceCode: 'NY',
          postalCode: '10001',
          countryCode: 'US',
        },
      ],
      selectedDeliveryOptions: [
        {
          groupId: 'delivery-group-1',
          selectedOptionId: 'express',
        },
      ],
    })
  })
})

describe('saved product detail API', () => {
  test('loads one durable saved product with an encoded query parameter', async () => {
    await getSavedProduct('product/key')

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('GET')
    expect(requests[0]?.url).toBe(
      'http://localhost:8080/api/users/me/saved-products/detail?productKey=product%2Fkey',
    )
  })

  test('does not send an account-bound request with the next user session', async () => {
    authenticatedUserId = 'user-b'

    await expect(getSavedProduct('product/key', { expectedUserId: 'user-a' })).rejects.toThrow(
      'Authenticated user changed before request',
    )
    expect(requests).toHaveLength(0)
  })
})

describe('account-bound mutation APIs', () => {
  test('do not send settings, inventory, taste, or search mutations with the next session', async () => {
    authenticatedUserId = 'user-b'

    await expect(updateUserSettings({ budget: 100 }, { expectedUserId: 'user-a' })).rejects.toThrow(
      'Authenticated user changed before request',
    )
    await expect(
      createUserInventoryItem(
        { photoPath: 'user-a/shoes.jpg', name: 'Shoes', category: 'APPAREL' },
        { expectedUserId: 'user-a' },
      ),
    ).rejects.toThrow('Authenticated user changed before request')
    await expect(
      updateUserTasteSignal({
        signalId: 'signal-1',
        disabled: true,
        expectedUserId: 'user-a',
      }),
    ).rejects.toThrow('Authenticated user changed before request')
    await expect(getCartCheckout({ cartId: 'cart-1', expectedUserId: 'user-a' })).rejects.toThrow(
      'Authenticated user changed before request',
    )
    await expect(bootstrapEmbeddedCheckout('cart-1', { expectedUserId: 'user-a' })).rejects.toThrow(
      'Authenticated user changed before request',
    )
    await expect(
      acknowledgeEmbeddedCheckoutOpened({
        cartId: 'cart-1',
        sessionId: 'session-1',
        expectedUserId: 'user-a',
      }),
    ).rejects.toThrow('Authenticated user changed before request')

    expect(requests).toHaveLength(0)
  })
})

describe('inventory photo validation', () => {
  test('accepts JPEG, PNG, and WebP files up to 5 MiB', () => {
    expect(() =>
      validateInventoryPhotoFile(new File(['jpeg'], 'item.jpg', { type: 'image/jpeg' })),
    ).not.toThrow()
    expect(() =>
      validateInventoryPhotoFile(new File(['png'], 'item.png', { type: 'image/png' })),
    ).not.toThrow()
    expect(() =>
      validateInventoryPhotoFile(new File(['webp'], 'item.webp', { type: 'image/webp' })),
    ).not.toThrow()
  })

  test('rejects HEIC, empty, and files larger than 5 MiB', () => {
    expect(() =>
      validateInventoryPhotoFile(new File(['heic'], 'item.heic', { type: 'image/heic' })),
    ).toThrow('Choose a JPEG, PNG, or WebP photo')
    expect(() =>
      validateInventoryPhotoFile(new File([], 'item.jpg', { type: 'image/jpeg' })),
    ).toThrow('Choose a photo that is not empty')
    expect(() =>
      validateInventoryPhotoFile(
        new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'item.jpg', { type: 'image/jpeg' }),
      ),
    ).toThrow('Photo must be 5 MB or smaller')
  })
})

describe('product variant selection API', () => {
  test('sends the clicked option name as provider relaxation priority', async () => {
    await selectProductVariant({
      anchorOfferKey: 'offer-anchor',
      selectedOptions: [
        { name: 'Size', value: 'M' },
        { name: 'Color', value: 'Blue' },
      ],
      preferredOptionName: 'Size',
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('POST')
    expect(requests[0]?.url).toEndWith('/api/v1/users/me/product-variant-selections')
    expect(await requests[0]?.json()).toEqual({
      anchorOfferKey: 'offer-anchor',
      selectedOptions: [
        { name: 'Size', value: 'M' },
        { name: 'Color', value: 'Blue' },
      ],
      preferredOptionName: 'Size',
    })
  })
})

describe('similar grouped product search API', () => {
  test('posts the encoded canonical key and originating search constraints', async () => {
    const controller = new AbortController()
    await searchSimilarGroupedProducts({
      canonicalProductKey: 'canonical/product key',
      query: 'trail running shoes',
      qualificationId: '00000000-0000-4000-8000-000000000099',
      signal: controller.signal,
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('POST')
    expect(requests[0]?.url).toBe(
      'http://localhost:8080/api/v1/users/me/products/canonical%2Fproduct%20key/similar',
    )
    expect(await requests[0]?.json()).toEqual({
      query: 'trail running shoes',
      qualificationId: '00000000-0000-4000-8000-000000000099',
    })
  })
})

describe('canonical product history rehydration API', () => {
  test('posts only server-issued canonical product keys', async () => {
    const controller = new AbortController()
    await rehydrateCanonicalProducts({
      canonicalProductKeys: ['anchor-key', 'result-key'],
      signal: controller.signal,
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('POST')
    expect(requests[0]?.url).toBe('http://localhost:8080/api/v1/users/me/products:rehydrate')
    expect(await requests[0]?.json()).toEqual({
      canonicalProductKeys: ['anchor-key', 'result-key'],
    })
  })
})

describe('qualified product search API', () => {
  test('binds a ready search to its qualification id', async () => {
    await searchGroupedProducts({
      query: 'black running shoes size 10',
      qualificationId: 'qualification-1',
      merchantId: 'merchant-1',
      offset: 0,
      limit: 20,
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('POST')
    expect(requests[0]?.url).toEndWith('/api/v1/users/me/product-searches')
    expect(await requests[0]?.json()).toEqual({
      query: 'black running shoes size 10',
      qualificationId: 'qualification-1',
      merchantId: 'merchant-1',
      offset: 0,
      limit: 20,
    })
  })
})

describe('product search preferences API', () => {
  test('sends one scoped size preference for merge', async () => {
    await updateUserSettings({
      productSearchPreferences: [
        { scope: 'footwear', attributeName: 'SIZE', values: ['10', '10.5'] },
      ],
    })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('PATCH')
    expect(requests[0]?.url).toEndWith('/api/users/me/settings')
    expect(await requests[0]?.json()).toEqual({
      productSearchPreferences: [
        { scope: 'footwear', attributeName: 'SIZE', values: ['10', '10.5'] },
      ],
    })
  })

  test('deletes only the requested scoped size preference', async () => {
    await deleteUserProductSearchPreference('trail footwear')

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('DELETE')
    expect(requests[0]?.url).toEndWith(
      '/api/users/me/settings/product-search-preferences/trail%20footwear',
    )
  })
})

describe('validated delivery location API', () => {
  test('searches worldwide cities through the authenticated backend proxy', async () => {
    await searchLocationSuggestions('Pra', { language: 'en', limit: 8 })

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('GET')
    expect(requests[0]?.url).toBe(
      'http://localhost:8080/api/locations/suggestions?query=Pra&language=en&limit=8',
    )
  })

  test('sends only provider-validated IDs when saving locations', async () => {
    await updateUserSettings({
      locations: [
        {
          id: 'geonames:3067696',
          country: 'Czechia',
          code: 'CZ',
          region: '10',
          postalCode: null,
          regionName: 'Prague',
          city: 'Prague',
        },
      ],
    })

    expect(await requests[0]?.json()).toEqual({
      locations: [{ id: 'geonames:3067696' }],
    })
  })
})
