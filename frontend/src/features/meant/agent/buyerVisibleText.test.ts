import { describe, expect, test } from 'bun:test'

import { sanitizeBuyerVisibleJson, sanitizeBuyerVisibleText } from './buyerVisibleText'

describe('buyer-visible agent text sanitization', () => {
  test('neutralizes only technical references inside otherwise ordinary prose', () => {
    expect(
      sanitizeBuyerVisibleText(
        'I need details from seller.myshopify.com. Retry mcp.shop.example or ' +
          'https://transport.example/api/ucp/mcp/session/1. ' +
          'Browse https://official.example/products/shoe.',
      ),
    ).toBe(
      'I need details from the merchant. Retry the merchant or the merchant. ' +
        'Browse https://official.example/products/shoe.',
    )
  })

  test('recursively sanitizes JSON strings while preserving shape and ordinary URLs', () => {
    const result = JSON.parse(
      sanitizeBuyerVisibleJson(
        JSON.stringify({
          text: 'Continue at https://seller.myshopify.com/mcp.',
          nested: {
            messages: [
              'Ask api.mcp.shop.example for help.',
              'Browse https://official.example/products/shoe',
            ],
            count: 2,
            available: true,
          },
        }),
      ) ?? 'null',
    ) as Record<string, unknown>

    expect(result).toEqual({
      text: 'Continue at the merchant.',
      nested: {
        messages: ['Ask the merchant for help.', 'Browse https://official.example/products/shoe'],
        count: 2,
        available: true,
      },
    })
  })

  test('preserves an ordinary product path that only starts with the mcp letters', () => {
    expect(
      sanitizeBuyerVisibleText('Browse https://official.example/mcpology for the product.'),
    ).toBe('Browse https://official.example/mcpology for the product.')
  })

  test('uses the verified merchant origin when the rendering context provides it', () => {
    expect(
      sanitizeBuyerVisibleText(
        'Continue at seller.myshopify.com or https://transport.example/api/ucp/mcp.',
        'https://official.example',
      ),
    ).toBe('Continue at official.example or official.example.')
  })

  test('preserves a verified myshopify storefront origin', () => {
    expect(
      sanitizeBuyerVisibleText('Continue at seller.myshopify.com.', 'official-store.myshopify.com'),
    ).toBe('Continue at official-store.myshopify.com.')
  })

  test('does not accept a technical endpoint as the preferred replacement', () => {
    expect(
      sanitizeBuyerVisibleText(
        'Continue at seller.myshopify.com.',
        'https://other-shop.myshopify.com/api/ucp/mcp',
      ),
    ).toBe('Continue at the merchant.')
  })

  test('neutralizes standalone protocol paths without matching ordinary path prefixes', () => {
    expect(
      sanitizeBuyerVisibleText(
        'Internal paths: /.well-known/ucp.json, /api/mcp, and /mcp/session/1. Keep /mcpology.',
      ),
    ).toBe('Internal paths: the merchant, the merchant, and the merchant. Keep /mcpology.')
  })
})
