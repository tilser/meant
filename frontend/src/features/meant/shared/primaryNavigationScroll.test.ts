import { describe, expect, test } from 'bun:test'

import {
  resetPrimaryNavigationScroll,
  type PrimaryNavigationScrollContainer,
} from './primaryNavigationScroll'

describe('primary navigation scroll restoration', () => {
  test('resets a scrolled Discover document when primary navigation opens Compare', () => {
    let scrollTop = 1_680
    const calls: ScrollToOptions[] = []
    const documentScrollContainer: PrimaryNavigationScrollContainer = {
      scrollTo(options) {
        calls.push(options)
        scrollTop = options.top ?? scrollTop
      },
    }

    resetPrimaryNavigationScroll(documentScrollContainer)

    expect(scrollTop).toBe(0)
    expect(calls).toEqual([{ top: 0, left: 0, behavior: 'auto' }])
  })
})
