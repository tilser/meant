import type { HTMLAttributes } from 'react'

export function productCarouselItemAccessibility({
  isPhone,
  index,
  activeIndex,
  total,
  name,
}: Readonly<{
  isPhone: boolean
  index: number
  activeIndex: number
  total: number
  name: string
}>): HTMLAttributes<HTMLDivElement> {
  if (!isPhone) {
    return {}
  }
  const hidden = index !== activeIndex
  return {
    role: 'group',
    'aria-roledescription': 'slide',
    'aria-label': `${index + 1} of ${total}: ${name}`,
    'aria-hidden': hidden ? true : undefined,
    inert: hidden ? true : undefined,
  }
}
