import { afterEach, beforeEach, describe, expect, mock, test } from 'bun:test'

mock.module('./supabase', () => ({
  supabase: {
    auth: {
      getSession: async () => ({ data: { session: { access_token: 'test-token' } } }),
    },
  },
}))

const {
  bindSelectedOfferToCart,
  bootstrapEmbeddedCheckout,
  cancelEmbeddedCheckout,
  completeEmbeddedCheckout,
  getSavedProduct,
} = await import('./apiClient')
const originalFetch = globalThis.fetch
let requests: Request[] = []

beforeEach(() => {
  requests = []
  globalThis.fetch = mock(async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = new Request(input, init)
    requests.push(request)
    if (request.url.endsWith('/cancel')) {
      return new Response(null, { status: 204 })
    }
    return Response.json({ cartId: 'cart-1', lines: [] })
  }) as unknown as typeof fetch
})

describe('embedded checkout session API', () => {
  test('uses opaque server session routes without browser checkout authority', async () => {
    await bootstrapEmbeddedCheckout('cart-1')
    await completeEmbeddedCheckout({ cartId: 'cart-1', sessionId: 'session-1' })
    await cancelEmbeddedCheckout({ cartId: 'cart-1', sessionId: 'session-1' })

    expect(requests.map((request) => request.method)).toEqual(['POST', 'POST', 'POST'])
    expect(requests.map((request) => request.url)).toEqual([
      'http://localhost:8080/api/carts/cart-1/checkout/embedded',
      'http://localhost:8080/api/carts/cart-1/checkout/embedded/session-1/complete',
      'http://localhost:8080/api/carts/cart-1/checkout/embedded/session-1/cancel',
    ])
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

describe('saved product detail API', () => {
  test('loads one durable saved product with an encoded query parameter', async () => {
    await getSavedProduct('product/key')

    expect(requests).toHaveLength(1)
    expect(requests[0]?.method).toBe('GET')
    expect(requests[0]?.url).toBe(
      'http://localhost:8080/api/users/me/saved-products/detail?productKey=product%2Fkey',
    )
  })
})
