export interface PrimaryNavigationScrollContainer {
  scrollTo(options: ScrollToOptions): void
}

export function resetPrimaryNavigationScroll(
  scrollContainer: PrimaryNavigationScrollContainer | null,
): void {
  scrollContainer?.scrollTo({ top: 0, left: 0, behavior: 'auto' })
}
