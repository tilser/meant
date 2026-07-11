export interface FocusTarget {
  focus: () => void
}

export function containModalTabFocus(
  focusable: readonly FocusTarget[],
  activeElement: FocusTarget | null,
  shiftKey: boolean,
  fallback: FocusTarget,
): boolean {
  if (focusable.length === 0) {
    fallback.focus()
    return true
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (!first || !last) return false

  if (focusable.length === 1) {
    first.focus()
    return true
  }
  if (!focusable.includes(activeElement ?? fallback)) {
    const destination = shiftKey ? last : first
    destination.focus()
    return true
  }
  if (shiftKey && activeElement === first) {
    last.focus()
    return true
  }
  if (!shiftKey && activeElement === last) {
    first.focus()
    return true
  }
  return false
}
