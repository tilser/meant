import { describe, expect, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { Workbench } from './Workbench'

describe('Workbench availability', () => {
  test('describes the future feature without rendering insights or agent controls', () => {
    const markup = renderToStaticMarkup(<Workbench />)

    expect(markup).toContain('Coming soon')
    expect(markup).toContain('start your own sub-agents')
    expect(markup).toContain('automatically collect their findings')
    expect(markup).toContain('This feature is not available yet')
    expect(markup).not.toContain('mt-ins-tab-label')
    expect(markup).not.toContain('<form')
    expect(markup).not.toContain('Profile signal')
    expect(markup).not.toContain('Researching')
  })
})
