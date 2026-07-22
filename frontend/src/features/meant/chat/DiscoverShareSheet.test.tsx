import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { DiscoverShareSheet } from './DiscoverShareSheet'

describe('DiscoverShareSheet', () => {
  test('shows the coming-soon share destination, friends, avatars, and newsletter signup', () => {
    const markup = renderToStaticMarkup(
      <DiscoverShareSheet
        thread={{ id: 'thread-1', title: 'Summer dresses', messages: [] }}
        newsletter={false}
        newsletterPending={false}
        onClose={() => undefined}
        onSend={() => undefined}
        onNewsletterSignup={() => undefined}
      />,
    )

    expect(markup).toContain('app.meant.com/s/coming_soon')
    expect(markup).toContain('/assets/share-mia.png')
    expect(markup).toContain('/assets/share-sofia.png')
    expect(markup).toContain('/assets/share-olivia.png')
    expect(markup).toContain('Mia')
    expect(markup).toContain('Sofia')
    expect(markup).toContain('Olivia')
    expect(markup).toContain('Coming soon')
    expect(markup).toContain('sign up')
    expect(markup).not.toContain('meant.app/s/thread-1')
  })
})
