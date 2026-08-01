import { clothingFitLabel } from './preferences/preferencesUtils'
import { deliveryLocationSummary } from './shared/locations'
import type { ClothingFit, Preference, UserLocation } from './types'

export const PROFILE_PREFERENCE_LIMIT = 6

export function ProfileBar({
  preferences,
  deliveryLocations,
  clothingFit,
  onEdit,
}: Readonly<{
  preferences: readonly Preference[]
  deliveryLocations: readonly UserLocation[]
  clothingFit: ClothingFit
  onEdit: () => void
}>) {
  const hiddenPreferenceCount = Math.max(0, preferences.length - PROFILE_PREFERENCE_LIMIT)
  const hiddenPreferenceLabel = hiddenPreferenceCount === 1 ? 'preference' : 'preferences'

  return (
    <div className="mt-profile">
      <span className="mt-mono mt-profile-key">Your profile</span>
      <button
        className={`mt-loc-chip ${deliveryLocations.length > 0 ? '' : 'empty'}`}
        type="button"
        onClick={onEdit}
      >
        <span aria-hidden>⌖</span>
        {deliveryLocationSummary(deliveryLocations)}
      </button>
      {clothingFit !== 'none' ? (
        <button className="mt-loc-chip" type="button" onClick={onEdit}>
          {clothingFitLabel(clothingFit)}
        </button>
      ) : null}
      <div className="mt-profile-chips">
        {preferences.length === 0 ? (
          <span className="mt-profile-empty mt-mono">No active preferences</span>
        ) : (
          preferences.slice(0, PROFILE_PREFERENCE_LIMIT).map((preference) => (
            <span className="mt-pref-pill" key={preference.id}>
              {preference.label}
            </span>
          ))
        )}
        {hiddenPreferenceCount > 0 ? (
          <button
            aria-label={`${hiddenPreferenceCount} more ${hiddenPreferenceLabel}. Edit all preferences`}
            className="mt-pref-pill mt-profile-more"
            type="button"
            onClick={onEdit}
          >
            +{hiddenPreferenceCount}
          </button>
        ) : null}
      </div>
      <button className="mt-profile-edit mt-mono" type="button" onClick={onEdit}>
        Edit
      </button>
    </div>
  )
}
