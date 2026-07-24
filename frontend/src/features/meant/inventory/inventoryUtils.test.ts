import { describe, expect, test } from 'bun:test'

import { safeInventoryProductUrl } from './inventoryUtils'

describe('inventory product URL policy', () => {
  test('rejects protocol endpoints while preserving legitimate product URLs', () => {
    expect(safeInventoryProductUrl('https://seller.myshopify.com/products/shoe')).toBeNull()
    expect(
      safeInventoryProductUrl(
        'https://official-store.myshopify.com/products/shoe',
        'official-store.myshopify.com',
      ),
    ).toBe('https://official-store.myshopify.com/products/shoe')
    expect(safeInventoryProductUrl('https://gateway.example/.well-known/ucp.json')).toBeNull()
    expect(safeInventoryProductUrl('https://gateway.example/api/ucp/mcp/session/1')).toBeNull()
    expect(safeInventoryProductUrl('https://gateway.example/api/mcp')).toBeNull()
    expect(safeInventoryProductUrl('https://gateway.example/mcp')).toBeNull()
    expect(safeInventoryProductUrl('https://gateway.example/mcpology')).toBe(
      'https://gateway.example/mcpology',
    )
  })
})
