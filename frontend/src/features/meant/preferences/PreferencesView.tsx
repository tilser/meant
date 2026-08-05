import { useEffect, useMemo, useRef, useState } from 'react'

import {
  searchLocationSuggestions,
  type LocationSuggestionProfile,
  type UserProductSearchPreferenceProfile,
  type UserTasteProfile,
} from '../../../lib/apiClient'
import { deliveryLocationSummary } from '../shared/locations'
import { PlusIcon, SearchIcon } from '../shared/icons'
import { CloseIcon, ViewHead } from '../shared/ui'
import type { ClothingFit, Preference, PreferenceId, UserLocation } from '../types'
import { IMPORT_ASK } from '../utils'
import {
  type PreferenceGroupId,
  CLOTHING_FIT_OPTIONS,
  DEFAULT_BUDGET,
  PREFERENCE_GROUPS,
  PREFERENCE_POLARITY_LABELS,
  clothingFitLabel,
  preferenceCategory,
  preferenceGroupsFor,
  preferenceMatchesSearch,
  sortPreferences,
} from './preferencesUtils'
import { productSearchPreferenceValues } from './productSearchPreferences'
export function PreferencesView({
  allPrefs,
  prefsOn,
  onToggle,
  onApplyDescription,
  budget,
  onBudget,
  deliveryLocations,
  onDeliveryLocations,
  clothingFit,
  onClothingFit,
  productSearchPreferences,
  productSearchPreferencesBusy,
  productSearchPreferencesError,
  onSaveProductSearchPreference,
  onRemoveProductSearchPreference,
  onRefreshProductSearchPreferences,
  preferenceDraft,
  preferenceDraftBusy,
  preferenceDraftError,
  onAcceptPreferenceDraft,
  onDismissPreferenceDraft,
  tasteProfile,
  onAcceptTasteSuggestion,
  onRejectTasteSuggestion,
  onDisableTasteSignal,
  onRemoveTasteSignal,
  profile,
  onDone,
}: Readonly<{
  allPrefs: readonly Preference[]
  prefsOn: ReadonlySet<PreferenceId>
  onToggle: (id: PreferenceId) => void
  onApplyDescription: (text: string) => Promise<boolean>
  budget: number | null
  onBudget: (value: number | null) => void
  deliveryLocations: readonly UserLocation[]
  onDeliveryLocations: (locations: UserLocation[]) => void
  clothingFit: ClothingFit
  onClothingFit: (value: ClothingFit) => void
  productSearchPreferences: readonly UserProductSearchPreferenceProfile[]
  productSearchPreferencesBusy: boolean
  productSearchPreferencesError: string | null
  onSaveProductSearchPreference: (preference: UserProductSearchPreferenceProfile) => void
  onRemoveProductSearchPreference: (scope: string) => void
  onRefreshProductSearchPreferences: () => void
  preferenceDraft?: readonly Preference[]
  preferenceDraftBusy?: boolean
  preferenceDraftError?: string | null
  onAcceptPreferenceDraft?: () => void
  onDismissPreferenceDraft?: () => void
  tasteProfile: UserTasteProfile
  onAcceptTasteSuggestion: (filterId: string) => void
  onRejectTasteSuggestion: (filterId: string) => void
  onDisableTasteSignal: (signalId: string, disabled: boolean) => void
  onRemoveTasteSignal: (signalId: string) => void
  profile: Readonly<{ name: string }>
  onDone: () => void
}>) {
  const [desc, setDesc] = useState('')
  const [importText, setImportText] = useState('')
  const [imported, setImported] = useState(false)
  const [copied, setCopied] = useState(false)
  const [parsing, setParsing] = useState(false)
  const [activePreferenceGroup, setActivePreferenceGroup] = useState<PreferenceGroupId>('interests')
  const [preferenceSearch, setPreferenceSearch] = useState('')
  const copiedTimeoutRef = useRef<number | null>(null)
  const enabled = sortPreferences(allPrefs.filter((preference) => prefsOn.has(preference.id)))
  const preferenceSearchText = preferenceSearch.trim().toLowerCase()
  const activeGroup =
    PREFERENCE_GROUPS.find((group) => group.id === activePreferenceGroup) ?? PREFERENCE_GROUPS[0]
  const visiblePreferences = useMemo(() => {
    if (preferenceSearchText) {
      return sortPreferences(
        allPrefs.filter((preference) => preferenceMatchesSearch(preference, preferenceSearchText)),
      )
    }
    const activeCategories = new Set(activeGroup.categories)
    return sortPreferences(
      allPrefs.filter((preference) => activeCategories.has(preferenceCategory(preference))),
    )
  }, [activeGroup, allPrefs, preferenceSearchText])
  const visiblePreferenceGroups = useMemo(
    () => preferenceGroupsFor(visiblePreferences),
    [visiblePreferences],
  )
  const finiteBudget = budget ?? DEFAULT_BUDGET

  useEffect(() => {
    return () => {
      if (copiedTimeoutRef.current !== null) {
        window.clearTimeout(copiedTimeoutRef.current)
      }
    }
  }, [])

  const copyQuestion = () => {
    const done = () => {
      if (copiedTimeoutRef.current !== null) {
        window.clearTimeout(copiedTimeoutRef.current)
      }
      setCopied(true)
      copiedTimeoutRef.current = window.setTimeout(() => {
        copiedTimeoutRef.current = null
        setCopied(false)
      }, 1600)
    }
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(IMPORT_ASK).then(done, done)
    } else {
      done()
    }
  }

  const apply = (text: string, clear: () => void) => {
    if (!text.trim()) {
      return
    }
    setParsing(true)
    onApplyDescription(text.trim())
      .then((applied) => {
        if (applied) {
          clear()
        }
      })
      .catch(() => undefined)
      .finally(() => setParsing(false))
  }

  return (
    <main className="mt-feed mt-view mt-prefs-view">
      <ViewHead
        eyebrow={`${profile.name}'s profile`}
        title="What matters to you"
        sub="Start from prepared filters, describe it in your own words, or import your preferences from another AI."
        right={
          <button className="mt-act mt-act-primary mt-prefs-done" type="button" onClick={onDone}>
            Done
          </button>
        }
      />

      {preferenceDraft && preferenceDraft.length > 0 ? (
        <section className="mt-prefs-draft" aria-label="Suggested preference defaults">
          <div>
            <h3>Use these as your defaults?</h3>
            <p>Review the suggestions before adding them to your permanent preferences.</p>
            <div className="mt-prefs-draft-chips">
              {preferenceDraft.map((preference) => (
                <span key={preference.id}>{preference.label}</span>
              ))}
            </div>
            {preferenceDraftError ? <p role="alert">{preferenceDraftError}</p> : null}
          </div>
          <div className="mt-prefs-draft-actions">
            <button
              className="mt-act mt-act-primary"
              type="button"
              disabled={preferenceDraftBusy}
              onClick={onAcceptPreferenceDraft}
            >
              {preferenceDraftBusy ? 'Saving…' : 'Use as defaults'}
            </button>
            <button
              className="mt-act mt-act-ghost"
              type="button"
              disabled={preferenceDraftBusy}
              onClick={onDismissPreferenceDraft}
            >
              Not now
            </button>
          </div>
        </section>
      ) : null}

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Where it ships</h3>
            <p className="mt-secsub">
              Leave shipping unrestricted, or add every place you want merchants to be able to
              deliver.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">
            {deliveryLocations.length > 0 ? `${deliveryLocations.length} active` : 'Anywhere'}
          </span>
        </div>
        <LocationSection locations={deliveryLocations} onSet={onDeliveryLocations} />
      </section>

      <LearnedTasteSection
        profile={tasteProfile}
        onAcceptSuggestion={onAcceptTasteSuggestion}
        onRejectSuggestion={onRejectTasteSuggestion}
        onDisableSignal={onDisableTasteSignal}
        onRemoveSignal={onRemoveTasteSignal}
      />

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Clothing fit</h3>
            <p className="mt-secsub">
              Used when apparel, shoes, or sizing-specific products need a fit signal.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">{clothingFitLabel(clothingFit)}</span>
        </div>
        <div className="mt-fit-options" role="radiogroup" aria-label="Clothing fit">
          {CLOTHING_FIT_OPTIONS.map((option) => (
            <button
              className={`mt-fit-option ${clothingFit === option.value ? 'active' : ''}`}
              key={option.value}
              type="button"
              role="radio"
              aria-checked={clothingFit === option.value}
              onClick={() => onClothingFit(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Saved sizes</h3>
            <p className="mt-secsub">
              Stable sizes learned during search are reused only for the matching product scope.
              Separate multiple accepted sizes with commas.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">
            {productSearchPreferencesBusy ? 'Syncing…' : `${productSearchPreferences.length} saved`}
          </span>
        </div>
        {productSearchPreferencesError ? (
          <div className="mt-size-preference-error" role="alert">
            <span>{productSearchPreferencesError}</span>
            <button
              className="mt-act mt-act-ghost"
              type="button"
              disabled={productSearchPreferencesBusy}
              onClick={onRefreshProductSearchPreferences}
            >
              Retry
            </button>
          </div>
        ) : null}
        {productSearchPreferences.length > 0 ? (
          <div className="mt-size-preferences">
            {productSearchPreferences.map((preference) => (
              <SavedSizePreferenceRow
                key={`${preference.scope}:${preference.attributeName}`}
                preference={preference}
                disabled={productSearchPreferencesBusy || Boolean(productSearchPreferencesError)}
                onSave={(values) => onSaveProductSearchPreference({ ...preference, values })}
                onRemove={() => onRemoveProductSearchPreference(preference.scope)}
              />
            ))}
          </div>
        ) : (
          <div className="mt-pref-empty">
            No saved sizes yet. Meant will add a stable size after you confirm it while searching.
          </div>
        )}
      </section>

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Describe it in your words</h3>
            <p className="mt-secsub">
              Tell Meant what you care about. We will turn it into filters you can fine-tune below.
            </p>
          </div>
        </div>
        <textarea
          className="mt-describe"
          value={desc}
          onChange={(event) => setDesc(event.target.value)}
          placeholder="I eat mostly organic, avoid polyester in clothing, prefer sustainable brands, and want gluten-free snacks."
        />
        <div className="mt-describe-actions">
          <button
            className="mt-act mt-act-primary"
            type="button"
            disabled={!desc.trim() || parsing}
            onClick={() => apply(desc, () => setDesc(''))}
          >
            {parsing ? 'Creating filters' : 'Create filters'}
          </button>
          <span className="mt-describe-hint">
            Meant matches your words to filters and creates new ones for anything custom.
          </span>
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Shopping profile</h3>
            <p className="mt-secsub">
              Selected needs, values, taste, and interests shape every product Meant shows you.
            </p>
          </div>
          <span className="mt-mono mt-sec-count">{enabled.length} active</span>
        </div>
        <div className="mt-profile-summary">
          {enabled.length > 0 ? (
            <div className="mt-active-chip-grid" aria-label="Active shopping profile">
              {enabled.map((preference) => (
                <button
                  className={`mt-active-chip ${preferenceCategory(preference) === 'interests' ? 'interest' : ''}`}
                  key={preference.id}
                  type="button"
                  title={preference.desc}
                  onClick={() => onToggle(preference.id)}
                >
                  <span>{preference.label}</span>
                  <CloseIcon size={12} />
                </button>
              ))}
            </div>
          ) : (
            <div className="mt-pref-empty">No profile filters selected yet.</div>
          )}
        </div>

        <div className="mt-profile-builder">
          <div className="mt-pref-builder-head">
            <div>
              <div className="mt-pref-name">Add to profile</div>
              <div className="mt-pref-desc">
                Interests tune themes and references; needs and values still guide fit and ranking.
              </div>
            </div>
            <span className="mt-mono mt-sec-count">{visiblePreferences.length} shown</span>
          </div>

          <div className="mt-pref-tabs" role="tablist" aria-label="Preference groups">
            {PREFERENCE_GROUPS.map((group) => (
              <button
                className={`mt-pref-tab ${group.id === activePreferenceGroup ? 'active' : ''}`}
                key={group.id}
                type="button"
                role="tab"
                aria-selected={group.id === activePreferenceGroup}
                onClick={() => setActivePreferenceGroup(group.id)}
              >
                <span>{group.label}</span>
                <small>{group.description}</small>
              </button>
            ))}
          </div>

          <label className="mt-pref-search">
            <SearchIcon size={15} />
            <input
              value={preferenceSearch}
              onChange={(event) => setPreferenceSearch(event.target.value)}
              placeholder="Search filters and interests"
              aria-label="Search filters and interests"
            />
            {preferenceSearchText ? (
              <button
                className="mt-pref-search-clear"
                type="button"
                aria-label="Clear preference search"
                onClick={() => setPreferenceSearch('')}
              >
                <CloseIcon size={13} />
              </button>
            ) : null}
          </label>

          <div className="mt-pref-picker">
            {visiblePreferenceGroups.length === 0 ? (
              <div className="mt-pref-empty">No matching filters.</div>
            ) : null}
            {visiblePreferenceGroups.map((group) => (
              <div className="mt-pref-category" key={group.category}>
                <div className="mt-pref-category-head">
                  <span>{group.label}</span>
                  <span className="mt-mono">{group.preferences.length}</span>
                </div>
                <div className="mt-pref-chip-grid">
                  {group.preferences.map((preference) => {
                    const active = prefsOn.has(preference.id)
                    const category = preferenceCategory(preference)
                    const polarity = preference.polarity || 'prefer'
                    return (
                      <button
                        className={`mt-filter-chip ${active ? 'active' : ''} ${category === 'interests' ? 'interest' : ''} polarity-${polarity}`}
                        key={preference.id}
                        type="button"
                        aria-pressed={active}
                        title={preference.desc}
                        onClick={() => onToggle(preference.id)}
                      >
                        <span className="mt-filter-chip-label">{preference.label}</span>
                        {category === 'interests' ? null : (
                          <span className="mt-filter-chip-meta">
                            {PREFERENCE_POLARITY_LABELS[polarity] ?? polarity}
                          </span>
                        )}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-budget">
          <div className="mt-budget-head">
            <div>
              <div className="mt-pref-name">Comfortable spend</div>
              <div className="mt-pref-desc">
                {budget === null
                  ? 'Meant will not apply a profile-level price ceiling.'
                  : 'Meant looks for the best quality it can find under this.'}
              </div>
            </div>
            <div className="mt-budget-val">{budget === null ? 'Unlimited' : `$${budget}`}</div>
          </div>
          <label className="mt-budget-toggle">
            <input
              type="checkbox"
              checked={budget === null}
              onChange={(event) => onBudget(event.target.checked ? null : finiteBudget)}
            />
            <span>No spend limit</span>
          </label>
          <input
            className="mt-range"
            type="range"
            min="20"
            max="300"
            step="5"
            value={finiteBudget}
            disabled={budget === null}
            onChange={(event) => onBudget(Number(event.target.value))}
          />
          <div className="mt-budget-scale mt-mono">
            <span>$20</span>
            <span>$300</span>
          </div>
        </div>
      </section>

      <section className="mt-prefs-section">
        <div className="mt-sechead">
          <div>
            <h3 className="mt-sectitle">Bring your profile from another AI</h3>
            <p className="mt-secsub">
              Already chat with ChatGPT or Claude? Ask it about you, then paste its answer here to
              build filters.
            </p>
          </div>
        </div>
        <div className="mt-sync-pane">
          <div className="mt-sync-block">
            <div className="mt-sync-row">
              <div className="mt-sync-label">
                <span className="mt-step-num">1</span> Ask your assistant about you
              </div>
              <button
                className={`mt-act mt-act-ghost mt-copy ${copied ? 'done' : ''}`}
                type="button"
                onClick={copyQuestion}
              >
                {copied ? 'Copied' : 'Copy question'}
              </button>
            </div>
            <p className="mt-sync-desc">
              Paste this into another AI. It should reply with what it knows about your shopping
              preferences.
            </p>
            <textarea
              className="mt-prompt-text mt-mono"
              readOnly
              value={IMPORT_ASK}
              onFocus={(event) => event.currentTarget.select()}
            />
          </div>
          <div className="mt-sync-block">
            <div className="mt-sync-row">
              <div className="mt-sync-label">
                <span className="mt-step-num">2</span> Paste its answer back
              </div>
            </div>
            <textarea
              className="mt-describe"
              value={importText}
              onChange={(event) => setImportText(event.target.value)}
              placeholder="Paste your assistant's reply about your preferences here..."
            />
            <div className="mt-describe-actions">
              <button
                className="mt-act mt-act-primary"
                type="button"
                disabled={!importText.trim() || parsing}
                onClick={() => {
                  apply(importText, () => setImportText(''))
                  setImported(true)
                  window.setTimeout(() => setImported(false), 3200)
                }}
              >
                Create filters from this
              </button>
              <span className="mt-describe-hint">
                {imported
                  ? 'Added to your filters above.'
                  : 'Meant reads it the same way as your own description.'}
              </span>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}

function preferenceScopeLabel(scope: string): string {
  const label = scope.replaceAll(/[-_]+/g, ' ').trim()
  return label ? `${label[0].toUpperCase()}${label.slice(1)}` : 'Products'
}

function SavedSizePreferenceRow({
  preference,
  disabled,
  onSave,
  onRemove,
}: Readonly<{
  preference: UserProductSearchPreferenceProfile
  disabled: boolean
  onSave: (values: string[]) => void
  onRemove: () => void
}>) {
  const [draft, setDraft] = useState(preference.values.join(', '))
  const values = productSearchPreferenceValues(draft)
  const unchanged =
    values.length === preference.values.length &&
    values.every((value, index) => value === preference.values[index])
  const label = preferenceScopeLabel(preference.scope)

  useEffect(() => {
    setDraft(preference.values.join(', '))
  }, [preference.values])

  return (
    <div className="mt-size-preference-row">
      <div className="mt-size-preference-copy">
        <div className="mt-pref-name">{label}</div>
        <div className="mt-pref-desc">Size is applied only when this scope matches the search.</div>
      </div>
      <label className="mt-size-preference-field">
        <span className="mt-field-label mt-mono">Sizes</span>
        <input
          className="mt-input"
          value={draft}
          aria-label={`Sizes for ${label}`}
          placeholder="10, 10.5"
          disabled={disabled}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && values.length > 0 && !unchanged) {
              onSave(values)
            }
          }}
        />
      </label>
      <div className="mt-size-preference-actions">
        <button
          className="mt-act mt-act-primary"
          type="button"
          disabled={disabled || values.length === 0 || unchanged}
          onClick={() => onSave(values)}
        >
          Save
        </button>
        <button
          className="mt-act mt-act-ghost"
          type="button"
          disabled={disabled}
          onClick={onRemove}
        >
          Remove
        </button>
      </div>
    </div>
  )
}

function LearnedTasteSection({
  profile,
  onAcceptSuggestion,
  onRejectSuggestion,
  onDisableSignal,
  onRemoveSignal,
}: Readonly<{
  profile: UserTasteProfile
  onAcceptSuggestion: (filterId: string) => void
  onRejectSuggestion: (filterId: string) => void
  onDisableSignal: (signalId: string, disabled: boolean) => void
  onRemoveSignal: (signalId: string) => void
}>) {
  const visibleSignals = profile.signals
    .filter((signal) => Math.abs(signal.weight) >= 0.25)
    .slice(0, 12)
  return (
    <section className="mt-prefs-section mt-learned">
      <div className="mt-sechead">
        <div>
          <h3 className="mt-sectitle">Learned from behavior</h3>
          <p className="mt-secsub">
            Saves, purchases, dismissals, and repeat searches tune ranking without changing explicit
            filters.
          </p>
        </div>
        <span className="mt-mono mt-sec-count">{visibleSignals.length} signals</span>
      </div>

      {profile.suggestions.length > 0 ? (
        <div className="mt-learned-suggestions">
          {profile.suggestions.map((suggestion) => (
            <div className="mt-learned-suggestion" key={suggestion.filterId}>
              <div>
                <div className="mt-pref-name">Add {suggestion.label}</div>
                <div className="mt-pref-desc">{suggestion.reason}</div>
              </div>
              <div className="mt-learned-actions">
                <button
                  className="mt-act mt-act-primary"
                  type="button"
                  onClick={() => onAcceptSuggestion(suggestion.filterId)}
                >
                  Add filter
                </button>
                <button
                  className="mt-act mt-act-ghost"
                  type="button"
                  onClick={() => onRejectSuggestion(suggestion.filterId)}
                >
                  Dismiss
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {visibleSignals.length > 0 ? (
        <div className="mt-learned-grid">
          {visibleSignals.map((signal) => {
            const disabled = signal.status === 'DISABLED'
            return (
              <div className={`mt-learned-signal ${disabled ? 'disabled' : ''}`} key={signal.id}>
                <div>
                  <div className="mt-learned-label">{signal.label}</div>
                  <div className="mt-learned-meta">
                    {tasteSignalTypeLabel(signal.signalType)} ·{' '}
                    {tasteSignalWeightLabel(signal.weight)}
                  </div>
                </div>
                <div className="mt-learned-actions">
                  <button
                    className="mt-act mt-act-ghost"
                    type="button"
                    onClick={() => onDisableSignal(signal.id, !disabled)}
                  >
                    {disabled ? 'Enable' : 'Disable'}
                  </button>
                  <button
                    className="mt-act mt-act-ghost"
                    type="button"
                    onClick={() => onRemoveSignal(signal.id)}
                  >
                    Remove
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="mt-pref-empty">Meant has not learned enough from your behavior yet.</div>
      )}
    </section>
  )
}

function tasteSignalTypeLabel(type: UserTasteProfile['signals'][number]['signalType']): string {
  return type.toLowerCase().replaceAll('_', ' ')
}

function tasteSignalWeightLabel(weight: number): string {
  if (weight > 0) {
    return `boost +${weight.toFixed(1)}`
  }
  return `penalty ${Math.abs(weight).toFixed(1)}`
}

function LocationSection({
  locations,
  onSet,
}: Readonly<{
  locations: readonly UserLocation[]
  onSet: (locations: UserLocation[]) => void
}>) {
  const [adding, setAdding] = useState(false)
  const [query, setQuery] = useState('')
  const [suggestions, setSuggestions] = useState<LocationSuggestionProfile[]>([])
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [attribution, setAttribution] = useState<{ label: string; url: string } | null>(null)
  const locationIds = new Set(locations.map((location) => location.id))
  const hasGeoNamesLocation = locations.some((location) => location.id.startsWith('geonames:'))

  useEffect(() => {
    const normalizedQuery = query.trim()
    if (!adding || normalizedQuery.length < 2) {
      setSuggestions([])
      setSearching(false)
      setSearchError(null)
      return
    }

    const controller = new AbortController()
    setSearching(true)
    setSearchError(null)
    const timeout = window.setTimeout(() => {
      const language =
        typeof navigator === 'undefined' ? 'en' : navigator.language.split('-')[0] || 'en'
      searchLocationSuggestions(normalizedQuery, {
        language,
        limit: 8,
        signal: controller.signal,
      })
        .then((page) => {
          if (controller.signal.aborted) return
          setSuggestions(page.suggestions)
          setAttribution({ label: page.attribution, url: page.attributionUrl })
        })
        .catch(() => {
          if (controller.signal.aborted) return
          setSuggestions([])
          setSearchError('Location search is temporarily unavailable.')
        })
        .finally(() => {
          if (!controller.signal.aborted) setSearching(false)
        })
    }, 300)

    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [adding, query])

  const closeSearch = () => {
    setAdding(false)
    setQuery('')
    setSuggestions([])
    setSearching(false)
    setSearchError(null)
  }

  const select = (suggestion: LocationSuggestionProfile) => {
    const nextLocation: UserLocation = {
      id: suggestion.id,
      country: suggestion.countryName,
      code: suggestion.country,
      region: suggestion.region,
      postalCode: suggestion.postalCode,
      regionName: suggestion.regionName,
      city: suggestion.city,
    }
    onSet(locationIds.has(nextLocation.id) ? [...locations] : [...locations, nextLocation])
    closeSearch()
  }

  const distinctLocationParts = (
    cityName: string,
    regionName: string | null,
    countryName: string,
  ) =>
    [regionName, countryName]
      .filter((part, index, values) => part && part !== cityName && values.indexOf(part) === index)
      .join(', ')

  const normalizedQuery = query.trim()
  const status =
    normalizedQuery.length < 2
      ? 'Type at least 2 characters.'
      : searching
        ? 'Searching worldwide cities…'
        : searchError
          ? searchError
          : suggestions.length === 0
            ? 'No matching city found.'
            : null

  return (
    <div className="mt-loc-stack">
      <div className={`mt-loc-set ${locations.length === 0 ? 'unrestricted' : ''}`}>
        <span className="mt-loc-pin">⌖</span>
        <div className="mt-loc-text">
          <div className="mt-loc-city">
            {locations.length === 0 ? 'Anywhere' : deliveryLocationSummary(locations)}
          </div>
          <div className="mt-loc-note">
            {locations.length === 0
              ? 'Meant is not hiding products by delivery destination.'
              : 'Meant shows products that can ship to at least one selected destination.'}
          </div>
        </div>
        <div className="mt-loc-actions">
          {locations.length > 0 ? (
            <button className="mt-loc-change" type="button" onClick={() => onSet([])}>
              No delivery filter
            </button>
          ) : null}
          <button className="mt-loc-change" type="button" onClick={() => setAdding(true)}>
            <PlusIcon size={14} />
            Add location
          </button>
        </div>
      </div>

      {locations.length > 0 ? (
        <div className="mt-loc-selected" aria-label="Delivery locations">
          {locations.map((location) => (
            <button
              className="mt-active-chip"
              key={location.id}
              type="button"
              onClick={() => onSet(locations.filter((candidate) => candidate.id !== location.id))}
            >
              <span>
                {location.city},{' '}
                {distinctLocationParts(location.city, location.regionName, location.country)}
              </span>
              <CloseIcon size={12} />
            </button>
          ))}
        </div>
      ) : null}

      {!adding && hasGeoNamesLocation ? (
        <span className="mt-loc-attribution">
          Location data by{' '}
          <a href="https://www.geonames.org/" target="_blank" rel="noreferrer">
            GeoNames
          </a>
        </span>
      ) : null}

      {adding ? (
        <div className="mt-loc-form">
          <label className="mt-field">
            <span className="mt-field-label mt-mono">City</span>
            <div className="mt-loc-search">
              <SearchIcon size={16} />
              <input
                className="mt-input"
                type="search"
                role="combobox"
                aria-autocomplete="list"
                aria-controls="delivery-location-suggestions"
                aria-expanded={suggestions.length > 0}
                placeholder="Start typing any city in the world"
                value={query}
                autoComplete="off"
                autoFocus
                onChange={(event) => setQuery(event.target.value)}
              />
            </div>
          </label>

          {suggestions.length > 0 ? (
            <div
              className="mt-loc-suggestions"
              id="delivery-location-suggestions"
              role="listbox"
              aria-label="City suggestions"
            >
              {suggestions.map((suggestion) => (
                <button
                  className="mt-loc-suggestion"
                  key={suggestion.id}
                  type="button"
                  role="option"
                  aria-selected={locationIds.has(suggestion.id)}
                  onClick={() => select(suggestion)}
                >
                  <span className="mt-loc-suggestion-city">{suggestion.city}</span>
                  <span className="mt-loc-suggestion-detail">
                    {distinctLocationParts(
                      suggestion.city,
                      suggestion.regionName,
                      suggestion.countryName,
                    )}
                  </span>
                </button>
              ))}
            </div>
          ) : status ? (
            <div className={`mt-loc-search-status ${searchError ? 'error' : ''}`} role="status">
              {status}
            </div>
          ) : null}

          <div className="mt-loc-form-footer">
            {attribution ? (
              <span className="mt-loc-attribution">
                Location data by{' '}
                <a href={attribution.url} target="_blank" rel="noreferrer">
                  {attribution.label}
                </a>
              </span>
            ) : (
              <span />
            )}
            <button className="mt-act mt-act-ghost" type="button" onClick={closeSearch}>
              Cancel
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
