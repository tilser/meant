import { afterEach, beforeEach, describe, expect, mock, test } from 'bun:test'

mock.module('./supabase', () => ({
  supabase: {
    auth: {
      getSession: async () => ({ data: { session: { access_token: 'test-token' } } }),
    },
  },
}))

const { bindSelectedOfferToCart } = await import('./apiClient')
const originalFetch = globalThis.fetch
let requests: Request[] = []

beforeEach(() => {
  requests = []
  globalThis.fetch = mock(async (input: RequestInfo | URL, init?: RequestInit) => {
    requests.push(new Request(input, init))
    return Response.json({ cartId: 'cart-1', lines: [] })
  }) as unknown as typeof fetch
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
