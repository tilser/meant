import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { PROFILE } from '../data'
import { AgentDiscoverView, type AgentDiscoverViewProps } from './AgentDiscoverView'

const props = {
  expectedUserId: 'user-1',
  profile: PROFILE,
  greeting: 'Good morning',
  prompts: ['Find running shoes'],
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
  agentMutationBlocked: false,
  isAgentMutationBlocked: () => false,
  onAgentMutationStarted: () => 'mutation-1',
  onAgentMutationFinished: () => undefined,
  onAgentRunSubmissionStarted: () => 'submission-1',
  onAgentRunSubmissionFinished: () => undefined,
  onProductDetailChatRequestHandled: () => undefined,
  onFlashMessage: () => undefined,
} satisfies AgentDiscoverViewProps

describe('agent Discovery home', () => {
  test('starts with the established Meant landing experience', () => {
    const markup = renderToStaticMarkup(<AgentDiscoverView {...props} />)

    expect(markup).toContain('Everything here is <em>Meant</em> for you.')
    expect(markup).toContain('Search with Meant - &quot;a good cotton T-shirt under $50&quot;')
    expect(markup).toContain('Find running shoes')
    expect(markup).toContain('Workbench')
    expect(markup).not.toContain('Loading your agent conversation')
  })
})
