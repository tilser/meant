export type GalleryDirection = 'previous' | 'next'

export interface SwipePoint {
  x: number
  y: number
}

export function horizontalSwipeDirection(
  start: SwipePoint,
  end: SwipePoint,
  minimumDistance = 60,
): GalleryDirection | null {
  const dx = end.x - start.x
  const dy = end.y - start.y
  if (Math.abs(dx) < minimumDistance || Math.abs(dx) < Math.abs(dy) * 1.5) {
    return null
  }
  return dx < 0 ? 'next' : 'previous'
}

export function adjacentGalleryIndex(
  currentIndex: number,
  imageCount: number,
  direction: GalleryDirection,
): number | null {
  if (currentIndex < 0 || currentIndex >= imageCount) {
    return null
  }
  const nextIndex = currentIndex + (direction === 'next' ? 1 : -1)
  return nextIndex >= 0 && nextIndex < imageCount ? nextIndex : null
}
