import { Children, isValidElement, type ReactElement, type ReactNode } from 'react'
import { describe, expect, mock, test } from 'bun:test'
import { renderToStaticMarkup } from 'react-dom/server'

import { ProfileBar, PROFILE_PREFERENCE_LIMIT } from './ProfileBar'
import type { Preference } from './types'

function preferences(count: number): Preference[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `preference-${index + 1}`,
    label: `Preference ${index + 1}`,
    desc: '',
  }))
}

function renderProfile(preferenceCount: number): string {
  return renderToStaticMarkup(
    <ProfileBar
      preferences={preferences(preferenceCount)}
      deliveryLocations={[]}
      clothingFit="none"
      onEdit={() => undefined}
    />,
  )
}

function findElement(
  node: ReactNode,
  predicate: (element: ReactElement) => boolean,
): ReactElement | undefined {
  if (!isValidElement(node)) {
    return undefined
  }
  if (predicate(node)) {
    return node
  }
  const children = (node.props as { children?: ReactNode }).children
  for (const child of Children.toArray(children)) {
    const match = findElement(child, predicate)
    if (match) {
      return match
    }
  }
  return undefined
}

describe('ProfileBar preference summary', () => {
  test('shows every preference and no overflow action below the display limit', () => {
    const markup = renderProfile(PROFILE_PREFERENCE_LIMIT - 1)

    expect(markup).toContain('Preference 5')
    expect(markup).not.toContain('mt-profile-more')
  })

  test('shows every preference and no overflow action at the display limit', () => {
    const markup = renderProfile(PROFILE_PREFERENCE_LIMIT)

    expect(markup).toContain('Preference 6')
    expect(markup).not.toContain('mt-profile-more')
  })

  test('shows the accurate hidden count with an accessible edit action above the limit', () => {
    for (const hiddenCount of [1, 2]) {
      const onEdit = mock(() => undefined)
      const element = ProfileBar({
        preferences: preferences(PROFILE_PREFERENCE_LIMIT + hiddenCount),
        deliveryLocations: [],
        clothingFit: 'none',
        onEdit,
      })
      const markup = renderToStaticMarkup(element)
      const preferenceNoun = hiddenCount === 1 ? 'preference' : 'preferences'
      const accessibleName = `${hiddenCount} more ${preferenceNoun}. Edit all preferences`

      expect(markup).toContain('Preference 6')
      expect(markup).not.toContain('Preference 7')
      expect(markup).not.toContain('Preference 8')
      expect(markup).toContain(`aria-label="${accessibleName}"`)
      expect(markup).toContain(`>+${hiddenCount}</button>`)

      const overflowAction = findElement(
        element,
        (candidate) =>
          (candidate.props as { 'aria-label'?: string })['aria-label'] === accessibleName,
      )
      expect(overflowAction).toBeDefined()
      if (!overflowAction) {
        throw new Error('Expected the preference overflow action to be rendered')
      }
      expect(overflowAction.type).toBe('button')

      const overflowActionProps = overflowAction.props as { onClick: () => void }
      overflowActionProps.onClick()
      expect(onEdit).toHaveBeenCalledTimes(1)
    }
  })
})
