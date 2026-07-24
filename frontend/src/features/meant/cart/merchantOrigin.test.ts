import { expect, test } from 'bun:test'

import {
  merchantAdjacentDisplayLabel,
  merchantAdjacentEditableLabel,
  merchantDisplayOrigin,
} from './merchantOrigin'

test('trusts verified storefront origins while rejecting endpoint-shaped values', () => {
  expect(merchantDisplayOrigin('nycfactory.com')).toBe('nycfactory.com')
  expect(merchantDisplayOrigin('sollys-online-grocery.myshopify.com')).toBe(
    'sollys-online-grocery.myshopify.com',
  )
  expect(merchantDisplayOrigin('https://merchant.example/mcp')).toBe('Merchant')
  expect(merchantDisplayOrigin('merchant.example/mcp')).toBe('Merchant')
  expect(merchantDisplayOrigin('mcp.shop.example')).toBe('Merchant')
  expect(merchantDisplayOrigin(null)).toBe('Merchant')
})

test('keeps ordinary adjacent labels but fails closed for technical seller labels', () => {
  expect(merchantAdjacentDisplayLabel('Independent Running Store')).toBe(
    'Independent Running Store',
  )
  expect(merchantAdjacentDisplayLabel('sollys-online-grocery.myshopify.com')).toBe('Merchant')
  expect(merchantAdjacentDisplayLabel('mcp.shop.example')).toBe('Merchant')
  expect(
    merchantAdjacentDisplayLabel(
      'https://sollys-online-grocery.myshopify.com/mcp',
      'nycfactory.com',
    ),
  ).toBe('nycfactory.com')
})

test('clears technical adjacent labels before they enter an editable field', () => {
  expect(merchantAdjacentEditableLabel('Independent Running Store')).toBe(
    'Independent Running Store',
  )
  expect(merchantAdjacentEditableLabel('sollys-online-grocery.myshopify.com')).toBe('')
  expect(merchantAdjacentEditableLabel('mcp.shop.example')).toBe('')
})
