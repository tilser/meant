import { describe, expect, test } from 'bun:test'

import { safeProductMessageUrl } from './productMessageUrl'

describe('product message URL policy', () => {
  test('rejects protocol endpoints while preserving buyer-safe links', () => {
    expect(safeProductMessageUrl('https://merchant.example/products/shoe')).toBe(
      'https://merchant.example/products/shoe',
    )
    expect(safeProductMessageUrl('mailto:support@merchant.example')).toBe(
      'mailto:support@merchant.example',
    )
    expect(safeProductMessageUrl('https://gateway.example/.well-known/ucp.json')).toBeNull()
    expect(safeProductMessageUrl('https://gateway.example/api/ucp/mcp/session/1')).toBeNull()
    expect(safeProductMessageUrl('https://gateway.example/api/mcp')).toBeNull()
    expect(safeProductMessageUrl('https://gateway.example/mcp')).toBeNull()
    expect(safeProductMessageUrl('https://seller.myshopify.com/products/shoe')).toBeNull()
    expect(safeProductMessageUrl('https://mcp.gateway.example/custom')).toBeNull()
    expect(safeProductMessageUrl('https://gateway.example/mcpology')).toBe(
      'https://gateway.example/mcpology',
    )
  })
})
