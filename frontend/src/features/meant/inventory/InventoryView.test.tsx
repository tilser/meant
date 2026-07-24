import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { UserInventoryItemProfile } from '../../../lib/apiClient'
import { InventoryView } from './InventoryView'
import { inventoryFormFromItem } from './inventoryUtils'

const purchasedItem: UserInventoryItemProfile = {
  id: 'inventory-1',
  source: 'MEANT_PURCHASE',
  sourceProductKey: 'canonical-1',
  productHash: null,
  sourceCheckoutAttemptId: 'attempt-1',
  commerceReference: {
    provider: 'SHOPIFY',
    merchantOrigin: 'nycfactory.com',
    sourceType: 'PROVIDER_CATALOG',
    sourceIdentity: 'shopify-global-catalog',
    externalProductId: 'product-1',
    externalVariantId: 'variant-42-blue',
    selectedOptions: [
      { group: 'Variant', name: 'Size', value: '42' },
      { name: 'Color', value: 'Blue' },
    ],
  },
  name: 'Running shoes',
  brand: 'Example',
  category: 'APPAREL',
  description: null,
  photoPath: null,
  imageUrl: null,
  productUrl: null,
  photoUrl: null,
  size: '42',
  color: 'Blue',
  material: null,
  quantity: 1,
  unit: null,
  location: null,
  notes: null,
  attributes: [],
  consumable: false,
  restockEnabled: false,
  restockThreshold: null,
  purchasedOn: null,
  purchasedAt: '2026-07-18T12:00:00Z',
  createdAt: '2026-07-18T12:00:00Z',
  updatedAt: '2026-07-18T12:00:00Z',
}

function renderInventory(item: UserInventoryItemProfile): string {
  return renderToStaticMarkup(
    <InventoryView
      userId="user-1"
      items={[item]}
      loading={false}
      error={null}
      onRefresh={() => undefined}
      onAddItem={async () => item}
      onUpdateItem={async () => item}
      onDeleteItem={async () => undefined}
      onExport={async () => undefined}
    />,
  )
}

describe('InventoryView commerce identity', () => {
  test('renders one add flow with required photo, name, and category', () => {
    const markup = renderInventory(purchasedItem)

    expect(markup).toContain('Add item')
    expect(markup).toContain('Photo *')
    expect(markup).toContain('accept="image/jpeg,image/png,image/webp"')
    expect(markup).toContain('Name *')
    expect(markup).toContain('Category *')
    expect(markup).toContain('More details')
    expect(markup).not.toContain('Manual add')
    expect(markup).not.toContain('Photo add')
    expect(markup).not.toContain('Image URL')
  })

  test('renders purchased selected options as read-only chips', () => {
    const markup = renderInventory(purchasedItem)

    expect(markup).toContain('aria-label="Purchased options"')
    expect(markup).toContain('Variant · Size: 42')
    expect(markup).toContain('Color: Blue')
  })

  test('keeps legacy inventory without commerce identity readable', () => {
    const legacyItem: UserInventoryItemProfile = {
      ...purchasedItem,
      id: 'inventory-legacy',
      source: 'MANUAL',
      sourceProductKey: null,
      sourceCheckoutAttemptId: undefined,
      commerceReference: undefined,
      name: 'Old wool coat',
      purchasedAt: null,
    }

    const markup = renderInventory(legacyItem)

    expect(markup).toContain('Old wool coat')
    expect(markup).not.toContain('aria-label="Purchased options"')
  })

  test('fails closed for a technical seller label retained by a historical purchase', () => {
    const technicalSeller = 'sollys-online-grocery.myshopify.com'
    const markup = renderInventory({
      ...purchasedItem,
      brand: technicalSeller,
    })

    expect(markup).toContain('class="mt-inv-brand">nycfactory.com</div>')
    expect(markup).not.toContain(technicalSeller)
  })

  test('does not render stale MCP-host product or image URLs', () => {
    const technicalHost = 'mcp.shop.example'
    const markup = renderInventory({
      ...purchasedItem,
      imageUrl: `https://${technicalHost}/products/shoe.png`,
      productUrl: `https://${technicalHost}/products/shoe`,
    })

    expect(markup).not.toContain(technicalHost)
    expect(markup).not.toContain('class="mt-inv-product-link"')
  })

  test('clears only technical historical seller labels from edit state', () => {
    expect(inventoryFormFromItem(purchasedItem).brand).toBe('Example')

    for (const technicalSeller of ['sollys-online-grocery.myshopify.com', 'mcp.shop.example']) {
      expect(
        inventoryFormFromItem({
          ...purchasedItem,
          brand: technicalSeller,
        }).brand,
      ).toBe('')
    }
  })

  test('does not render unsafe legacy product links', () => {
    const markup = renderInventory({
      ...purchasedItem,
      productUrl: 'javascript:alert(1)',
    })

    expect(markup).not.toContain('javascript:')
    expect(markup).not.toContain('class="mt-inv-product-link"')
  })
})
