import { describe, expect, test } from 'bun:test'
import type { ComponentProps } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import type { Product } from '../types'
import { DiscoverChatBlockView } from './DiscoverChatBlockView'
import type { DiscoverChatBlock } from './types'

const technicalSeller = 'sollys-online-grocery.myshopify.com'

const product: Product = {
  id: 'trail-shoe',
  name: 'Grounded trail shoe',
  brand: technicalSeller,
  category: 'Running shoes',
  tone: '#eee',
  match: 91,
  priceFrom: 119,
  merchants: 1,
  satisfies: [],
  misses: [],
  note: 'A strong fit.',
  pros: [],
  cons: [],
  review: { score: null, count: 0, insight: 'No reviews.' },
  offers: [
    {
      merchant: technicalSeller,
      price: 119,
      delivery: 'Calculated at checkout',
    },
  ],
}

const baseProps = {
  threadId: 'thread-1',
  deliveryLocations: [],
  preferences: [],
  cart: [],
  cartProducts: [],
  savedSet: new Set<string>(),
  savePendingSet: new Set<string>(),
  pinnedSet: new Set<string>(),
  watchedSet: new Set<string>(),
  shelfProductSet: new Set<string>(),
  onOpen: () => undefined,
  onToggleSave: () => undefined,
  onAddCart: () => undefined,
  onPin: () => undefined,
  onWatch: () => undefined,
  onDig: () => undefined,
  onJustPick: () => undefined,
  onCompareHere: () => undefined,
  onOpenFullCompare: () => undefined,
  onOpenSaved: () => undefined,
  onOpenOrders: () => undefined,
  onOpenPrefs: () => undefined,
  onOpenCart: () => undefined,
  onReviewCartHere: () => undefined,
  onRestoreCartLine: () => undefined,
  onCartQty: () => undefined,
  onCartRemove: () => undefined,
  onCheckout: () => undefined,
  activeCheckout: null,
  checkoutBusy: false,
  checkoutError: null,
  onCheckoutAssistant: async () => null,
  onRefreshCheckout: () => undefined,
  onCheckoutHere: () => undefined,
  newsletter: false,
  newsletterPending: false,
  onNewsletterSignup: () => undefined,
  onShelfAddProduct: () => undefined,
  onDragProduct: () => undefined,
  immutable: false,
  useLiveCart: true,
  agentActionsDisabled: false,
} satisfies Omit<ComponentProps<typeof DiscoverChatBlockView>, 'block'>

describe('DiscoverChatBlockView merchant display identity', () => {
  test('fails closed for poisoned decision, watch, and added snapshots', () => {
    const blocks: DiscoverChatBlock[] = [
      { type: 'decision', product, runnerUp: null },
      { type: 'watch', product, price: 119, merchant: technicalSeller },
      {
        type: 'added',
        product,
        merchant: technicalSeller,
        synced: true,
        price: 119,
        count: 1,
      },
    ]
    const markup = renderToStaticMarkup(
      <>
        {blocks.map((block, index) => (
          <DiscoverChatBlockView key={index} {...baseProps} block={block} />
        ))}
      </>,
    )

    expect(markup).toContain('Merchant')
    expect(markup).not.toContain(technicalSeller)
  })

  test('never renders a discount source link to Shopify or MCP transport coordinates', () => {
    const technicalUrls = [
      `https://${technicalSeller}/discounts/SAVE10`,
      'https://catalog.shopify.com/api/ucp/mcp',
      'https://mcp.shop.example/discounts/SAVE10',
    ]
    const block: DiscoverChatBlock = {
      type: 'code',
      product,
      merchant: 'sollys.com',
      codes: technicalUrls.map((sourceUrl, index) => ({
        code: `SAVE${index + 1}`,
        description: 'Accepted by the merchant.',
        sourceUrl,
      })),
    }

    const markup = renderToStaticMarkup(<DiscoverChatBlockView {...baseProps} block={block} />)

    expect(markup).not.toContain('myshopify.com')
    expect(markup).not.toContain('catalog.shopify.com')
    expect(markup).not.toContain('mcp.shop.example')
    expect(markup).not.toContain('href=')
  })

  test('binds an agent product link to the same product snapshot used by product cards', () => {
    const linkedProduct = {
      ...product,
      canonicalProduct: { key: 'product_v3_grounded' },
    } as Product
    const block: DiscoverChatBlock = {
      type: 'text',
      text: '[Grounded trail shoe](meant:product:product_v3_grounded)',
    }

    const markup = renderToStaticMarkup(
      <DiscoverChatBlockView {...baseProps} block={block} messageProducts={[linkedProduct]} />,
    )

    expect(markup).toContain('class="mt-agent-product-link"')
    expect(markup).toContain('data-product-key="product_v3_grounded"')
    expect(markup).not.toContain('href=')
  })
})
