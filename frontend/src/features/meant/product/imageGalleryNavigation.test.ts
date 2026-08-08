import { describe, expect, test } from 'bun:test'

import { adjacentGalleryIndex, horizontalSwipeDirection } from './imageGalleryNavigation'

describe('image gallery navigation', () => {
  test('recognizes deliberate horizontal swipes without treating vertical scrolling as a swipe', () => {
    expect(horizontalSwipeDirection({ x: 180, y: 100 }, { x: 80, y: 108 })).toBe('next')
    expect(horizontalSwipeDirection({ x: 80, y: 100 }, { x: 180, y: 92 })).toBe('previous')
    expect(horizontalSwipeDirection({ x: 180, y: 100 }, { x: 145, y: 103 })).toBeNull()
    expect(horizontalSwipeDirection({ x: 180, y: 100 }, { x: 100, y: 180 })).toBeNull()
  })

  test('moves through the gallery without crossing its boundaries', () => {
    expect(adjacentGalleryIndex(1, 3, 'previous')).toBe(0)
    expect(adjacentGalleryIndex(1, 3, 'next')).toBe(2)
    expect(adjacentGalleryIndex(0, 3, 'previous')).toBeNull()
    expect(adjacentGalleryIndex(2, 3, 'next')).toBeNull()
  })
})
