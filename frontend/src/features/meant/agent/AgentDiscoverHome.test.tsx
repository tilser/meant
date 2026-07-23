import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { PROFILE } from '../data'
import type { MerchantProfile } from '../../../lib/apiClient'
import { AgentDiscoverView, type AgentDiscoverViewProps } from './AgentDiscoverView'

const merchant: MerchantProfile = {
  id: '00000000-0000-4000-8000-000000000088',
  domain: 'merchant.example',
  name: 'Example Merchant',
  description: 'Direct merchant catalog',
  advertisedMcpEndpoint: 'https://merchant.example/mcp',
  profileMcpEndpoint: null,
  supportsIdentityLinking: false,
}

const props = {
  expectedUserId: 'user-1',
  profile: PROFILE,
  greeting: 'Good morning',
  prompts: ['Find running shoes'],
  merchants: [merchant],
  merchantsLoading: false,
  merchantsError: null,
  deliveryLocations: [],
  preferences: [],
  cart: [],
  cartProducts: [],
  savedSet: new Set(),
  savePendingSet: new Set(),
  shelf: [],
  shelfFlashMessageId: null,
  productDetailChatRequest: null,
  discoverFindRequest: null,
  homeRequestId: 0,
  newsletter: false,
  onOpen: () => undefined,
  onToggleSave: () => undefined,
  onAddSelectedOfferToCart: async () => true,
  onCompareProducts: () => undefined,
  onCheckout: () => undefined,
  activeCheckout: null,
  checkoutBusy: false,
  checkoutError: null,
  onCheckoutAssistant: async () => null,
  onRefreshCheckout: () => undefined,
  onReleaseCheckout: () => undefined,
  onOpenSaved: () => undefined,
  onOpenOrders: () => undefined,
  onOpenPrefs: () => undefined,
  onOpenCart: () => undefined,
  onOpenShelf: () => undefined,
  onNewsletterChange: () => undefined,
  onShelfAddMessage: () => undefined,
  onShelfAddProduct: () => undefined,
  onReadAgentCart: () => [],
  onCaptureAgentCartRevision: () => undefined,
  onAgentCartSnapshot: () => [],
  onUpdateCartQuantity: () => undefined,
  onProductDetailChatRequestHandled: () => undefined,
  onFlashMessage: () => undefined,
} satisfies AgentDiscoverViewProps

describe('agent Discovery home', () => {
  test('starts with the established Meant landing experience', () => {
    const markup = renderToStaticMarkup(<AgentDiscoverView {...props} />)

    expect(markup).toContain('Everything here is <em>Meant</em> for you.')
    expect(markup).toContain('Search with Meant - &quot;a good cotton T-shirt under $50&quot;')
    expect(markup).toContain('Find running shoes')
    expect(markup).toContain('aria-haspopup="listbox"')
    expect(markup).not.toContain('verified Shop IDs')
    expect(markup).toContain('Workbench')
    expect(markup).not.toContain('Loading your agent conversation')
  })
})
