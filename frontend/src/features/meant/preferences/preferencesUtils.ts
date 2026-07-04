import type { ClothingFit, Preference } from '../types'

export const DEFAULT_BUDGET = 120

export const CLOTHING_FIT_OPTIONS: readonly { value: ClothingFit; label: string }[] = [
  { value: 'none', label: 'No preference' },
  { value: 'men', label: "Men's" },
  { value: 'women', label: "Women's" },
  { value: 'other', label: 'Other' },
]

export function clothingFitLabel(value: ClothingFit): string {
  return CLOTHING_FIT_OPTIONS.find((option) => option.value === value)?.label ?? 'No preference'
}

export type PreferenceGroupId = 'needs' | 'values' | 'taste' | 'interests'

export const PREFERENCE_GROUPS: readonly {
  id: PreferenceGroupId
  label: string
  description: string
  categories: readonly string[]
}[] = [
  {
    id: 'needs',
    label: 'Needs',
    description: 'Diet, materials, care, and home constraints',
    categories: ['food', 'materials', 'personal-care', 'home'],
  },
  {
    id: 'values',
    label: 'Values',
    description: 'Ethics, sustainability, and sourcing',
    categories: ['sustainability'],
  },
  {
    id: 'taste',
    label: 'Taste',
    description: 'Quality, shopping style, and tech preferences',
    categories: ['shopping', 'technology'],
  },
  {
    id: 'interests',
    label: 'Interests',
    description: 'Culture and hobbies that influence style',
    categories: ['interests'],
  },
]

const PREFERENCE_CATEGORY_LABELS: Record<string, string> = {
  food: 'Food',
  materials: 'Materials and fit',
  sustainability: 'Values',
  'personal-care': 'Personal care',
  shopping: 'Shopping style',
  technology: 'Technology',
  home: 'Home',
  interests: 'Interests',
}

export const PREFERENCE_POLARITY_LABELS: Record<string, string> = {
  avoid: 'Avoid',
  prefer: 'Prefer',
  require: 'Need',
}

export function preferenceCategory(preference: Preference): string {
  return preference.category || 'other'
}

function preferenceCategoryLabel(category: string): string {
  return (
    PREFERENCE_CATEGORY_LABELS[category] ??
    category
      .split('-')
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ')
  )
}

function preferenceSortValue(preference: Preference): number {
  return preference.displayOrder ?? Number.MAX_SAFE_INTEGER
}

export function sortPreferences(preferences: readonly Preference[]): Preference[] {
  return [...preferences].sort(
    (left, right) =>
      preferenceSortValue(left) - preferenceSortValue(right) ||
      left.label.localeCompare(right.label),
  )
}

export function preferenceMatchesSearch(preference: Preference, searchText: string): boolean {
  const category = preferenceCategoryLabel(preferenceCategory(preference))
  return [
    preference.label,
    preference.desc,
    preference.category,
    preference.polarity,
    category,
  ].some((value) => typeof value === 'string' && value.toLowerCase().includes(searchText))
}

export function preferenceGroupsFor(
  preferences: readonly Preference[],
): { category: string; label: string; preferences: Preference[] }[] {
  const groups = new Map<string, Preference[]>()
  sortPreferences(preferences).forEach((preference) => {
    const category = preferenceCategory(preference)
    groups.set(category, [...(groups.get(category) ?? []), preference])
  })
  return [...groups.entries()].map(([category, groupedPreferences]) => ({
    category,
    label: preferenceCategoryLabel(category),
    preferences: groupedPreferences,
  }))
}
