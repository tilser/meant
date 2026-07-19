import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { ComingSoonNewsletter } from './DiscoverChatBlockView'
import { comingSoonMessage } from './comingSoon'

describe('coming-soon product actions', () => {
  test('produces the newsletter signup block used by Watch', () => {
    expect(comingSoonMessage('coming-soon-1')).toEqual({
      id: 'coming-soon-1',
      role: 'ai',
      blocks: [{ type: 'newsletter' }],
    })
  })

  test('explain availability and offer newsletter signup', () => {
    const markup = renderToStaticMarkup(
      <ComingSoonNewsletter
        newsletter={false}
        newsletterPending={false}
        onNewsletterSignup={() => undefined}
      />,
    )

    expect(markup).toContain('This functionality isn&#x27;t ready yet')
    expect(markup).toContain('sign up')
    expect(markup).toContain('for our newsletter')
  })

  test('reflect an existing subscription instead of offering another signup', () => {
    const markup = renderToStaticMarkup(
      <ComingSoonNewsletter
        newsletter
        newsletterPending={false}
        onNewsletterSignup={() => undefined}
      />,
    )

    expect(markup).toContain('You&#x27;re already subscribed to the newsletter')
    expect(markup).not.toContain('sign up')
  })
})
