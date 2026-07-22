import { describe, expect, test } from 'bun:test'

import type { CartItem } from '../types'
import { prepareCheckoutFromCurrentCart } from './checkoutPreparation'

function cartLine(id: string, cartId?: string | null): CartItem {
  return { id, merchant: 'Test merchant', qty: 1, cartId }
}

describe('prepareCheckoutFromCurrentCart', () => {
  test('submits checkout directly without persisting an active-cart refresh', async () => {
    const actions: Array<{
      toolName: string
      argumentsValue: unknown
      summary: string
    }> = []

    const outcome = await prepareCheckoutFromCurrentCart(
      [cartLine('product-1', ' cart-1 '), cartLine('product-2', 'cart-1')],
      async (toolName, argumentsValue, summary) => {
        actions.push({ toolName, argumentsValue, summary })
      },
    )

    expect(outcome).toBe('submitted')
    expect(actions).toEqual([
      {
        toolName: 'prepare_checkout',
        argumentsValue: { cartIds: ['cart-1'] },
        summary: 'Prepared checkout',
      },
    ])
  })

  test('does not execute an action when the live cart has no server cart ID', async () => {
    let executionCount = 0

    const outcome = await prepareCheckoutFromCurrentCart([cartLine('local-product')], async () => {
      executionCount += 1
    })

    expect(outcome).toBe('missing-cart')
    expect(executionCount).toBe(0)
  })
})
