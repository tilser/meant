import { describe, expect, test } from 'bun:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

import { PreferencesView } from './PreferencesView'
import { productSearchPreferenceValues } from './productSearchPreferences'

describe('saved product search preference values', () => {
  test('normalizes comma-separated sizes and removes case-insensitive duplicates', () => {
    expect(productSearchPreferenceValues(' 10, 10.5, 10, EU 44, eu 44, ')).toEqual([
      '10',
      '10.5',
      'EU 44',
    ])
  })

  test('does not turn an empty draft into a stored size', () => {
    expect(productSearchPreferenceValues(' ,  , ')).toEqual([])
  })

  test('surfaces sync failures and disables stale saved-size edits', () => {
    const markup = renderToStaticMarkup(
      createElement(PreferencesView, {
        allPrefs: [],
        prefsOn: new Set<string>(),
        onToggle: () => undefined,
        onApplyDescription: async () => true,
        budget: null,
        onBudget: () => undefined,
        deliveryLocations: [],
        onDeliveryLocations: () => undefined,
        clothingFit: 'none',
        onClothingFit: () => undefined,
        productSearchPreferences: [{ scope: 'footwear', attributeName: 'SIZE', values: ['10'] }],
        productSearchPreferencesBusy: false,
        productSearchPreferencesError: 'Could not refresh saved sizes.',
        onSaveProductSearchPreference: () => undefined,
        onRemoveProductSearchPreference: () => undefined,
        onRefreshProductSearchPreferences: () => undefined,
        tasteProfile: { profileHash: '', signals: [], suggestions: [] },
        onAcceptTasteSuggestion: () => undefined,
        onRejectTasteSuggestion: () => undefined,
        onDisableTasteSignal: () => undefined,
        onRemoveTasteSignal: () => undefined,
        profile: { name: 'Alex' },
        onDone: () => undefined,
      }),
    )

    expect(markup).toContain('role="alert"')
    expect(markup).toContain('Could not refresh saved sizes.')
    expect(markup).toContain('disabled=""')
  })
})
