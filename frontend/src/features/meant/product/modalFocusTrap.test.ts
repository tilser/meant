import { describe, expect, test } from 'bun:test'

import { containModalTabFocus, type FocusTarget } from './modalFocusTrap'

function target(name: string, focused: string[]): FocusTarget {
  return { focus: () => focused.push(name) }
}

describe('grouped product modal focus containment', () => {
  test('wraps Tab and Shift+Tab at the dialog boundaries', () => {
    const focused: string[] = []
    const fallback = target('dialog', focused)
    const first = target('first', focused)
    const last = target('last', focused)

    expect(containModalTabFocus([first, last], last, false, fallback)).toBe(true)
    expect(containModalTabFocus([first, last], first, true, fallback)).toBe(true)
    expect(focused).toEqual(['first', 'last'])
  })

  test('keeps focus safe with zero or one enabled control', () => {
    const focused: string[] = []
    const fallback = target('dialog', focused)
    const only = target('only', focused)

    expect(containModalTabFocus([], null, false, fallback)).toBe(true)
    expect(containModalTabFocus([only], only, false, fallback)).toBe(true)
    expect(focused).toEqual(['dialog', 'only'])
  })

  test('brings focus inside when the active element is outside', () => {
    const focused: string[] = []
    const fallback = target('dialog', focused)
    const first = target('first', focused)
    const last = target('last', focused)
    const outside = target('outside', focused)

    expect(containModalTabFocus([first, last], outside, false, fallback)).toBe(true)
    expect(containModalTabFocus([first, last], outside, true, fallback)).toBe(true)
    expect(focused).toEqual(['first', 'last'])
  })

  test('allows normal browser movement between interior controls', () => {
    const focused: string[] = []
    const fallback = target('dialog', focused)
    const first = target('first', focused)
    const middle = target('middle', focused)
    const last = target('last', focused)

    expect(containModalTabFocus([first, middle, last], middle, false, fallback)).toBe(false)
    expect(focused).toEqual([])
  })
})
