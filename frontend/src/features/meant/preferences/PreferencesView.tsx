import { useEffect, useMemo, useRef, useState } from 'react'

import type { UserTasteProfile } from '../../../lib/apiClient'
import { LOCATIONS } from '../data'
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
  const [code, setCode] = useState('')
  const [city, setCity] = useState('')
  const country = LOCATIONS.find((option) => option.code === code)
  const locationKeys = new Set(locations.map((location) => `${location.code}:${location.city}`))

  const save = () => {
    if (!country || !city) {
      return
    }
    const nextLocation = { code: country.code, country: country.country, city }
    const nextKey = `${nextLocation.code}:${nextLocation.city}`
    onSet(locationKeys.has(nextKey) ? [...locations] : [...locations, nextLocation])
    setCode('')
    setCity('')
    setAdding(false)
  }

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
              key={`${location.code}:${location.city}`}
              type="button"
              onClick={() =>
                onSet(
                  locations.filter(
                    (candidate) =>
                      candidate.code !== location.code || candidate.city !== location.city,
                  ),
                )
              }
            >
              <span>
                {location.city}, {location.country}
              </span>
              <CloseIcon size={12} />
            </button>
          ))}
        </div>
      ) : null}

      {adding ? (
        <div className="mt-loc-form">
          <div className="mt-loc-fields">
            <label className="mt-field">
              <span className="mt-field-label mt-mono">Country</span>
              <select
                className="mt-select"
                value={code}
                onChange={(event) => {
                  setCode(event.target.value)
                  setCity('')
                }}
              >
                <option value="">Select country</option>
                {LOCATIONS.map((option) => (
                  <option key={option.code} value={option.code}>
                    {option.country}
                  </option>
                ))}
              </select>
            </label>
            <label className="mt-field">
              <span className="mt-field-label mt-mono">City</span>
              <select
                className="mt-select"
                value={city}
                disabled={!code}
                onChange={(event) => setCity(event.target.value)}
              >
                <option value="">{code ? 'Select city' : 'Pick a country first'}</option>
                {(country?.cities ?? []).map((candidate) => (
                  <option key={candidate} value={candidate}>
                    {candidate}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="mt-describe-actions">
            <button
              className="mt-act mt-act-primary"
              type="button"
              onClick={save}
              disabled={!code || !city}
            >
              Add location
            </button>
            <button
              className="mt-act mt-act-ghost"
              type="button"
              onClick={() => {
                setAdding(false)
                setCode('')
                setCity('')
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
