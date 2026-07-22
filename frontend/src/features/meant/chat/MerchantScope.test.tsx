import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import type { MerchantProfile } from '../../../lib/apiClient'
import { MerchantScope } from './ChatDiscoverView'

const merchant: MerchantProfile = {
  id: '00000000-0000-4000-8000-000000000088',
  domain: 'merchant.example',
  name: 'Example Merchant',
  description: 'Direct merchant catalog',
  advertisedMcpEndpoint: 'https://merchant.example/mcp',
  profileMcpEndpoint: null,
  supportsIdentityLinking: false,
}

describe('merchant search scope', () => {
  test('renders a database merchant as the active direct-search scope', () => {
    const markup = renderToStaticMarkup(
      <MerchantScope
        merchants={[merchant]}
        selectedMerchantId={merchant.id}
        loading={false}
        error={null}
        onMerchant={() => undefined}
      />,
    )

    expect(markup).toContain('Example Merchant')
    expect(markup).toContain('aria-haspopup="listbox"')
    expect(markup).not.toContain('disabled')
    expect(markup).not.toContain('verified Shop IDs')
  })

  test('renders nothing when the merchant list request fails', () => {
    const markup = renderToStaticMarkup(
      <MerchantScope
        merchants={[]}
        selectedMerchantId={null}
        loading={false}
        error="Failed to fetch"
        onMerchant={() => undefined}
      />,
    )

    expect(markup).toBe('')
    expect(markup).not.toContain('Merchant list unavailable')
    expect(markup).not.toContain('Failed to fetch')
  })
})
