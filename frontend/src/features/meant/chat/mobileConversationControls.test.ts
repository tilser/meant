import { describe, expect, test } from 'bun:test'

import { mobileConversationControlsGestureState } from './mobileConversationControls'

describe('mobile conversation controls visibility', () => {
  test('starts hidden and ignores small gesture jitter', () => {
    const initial = { direction: null, travel: 0, visible: false } as const

    expect(mobileConversationControlsGestureState(initial, 0)).toEqual(initial)
    expect(mobileConversationControlsGestureState(initial, 4)).toEqual({
      direction: 'down',
      travel: 4,
      visible: false,
    })
  })

  test('hides after downward travel and returns after upward travel', () => {
    const hidden = mobileConversationControlsGestureState(
      { direction: null, travel: 0, visible: true },
      10,
    )
    expect(hidden).toEqual({ direction: 'down', travel: 0, visible: false })

    expect(mobileConversationControlsGestureState(hidden, -10)).toEqual({
      direction: 'up',
      travel: 0,
      visible: true,
    })
  })

  test('resets accumulated travel when the gesture changes direction', () => {
    const downwardJitter = mobileConversationControlsGestureState(
      { direction: null, travel: 0, visible: false },
      8,
    )

    expect(mobileConversationControlsGestureState(downwardJitter, -8)).toEqual({
      direction: 'up',
      travel: 8,
      visible: false,
    })
    expect(
      mobileConversationControlsGestureState({ direction: 'up', travel: 8, visible: false }, -2),
    ).toEqual({
      direction: 'up',
      travel: 0,
      visible: true,
    })
  })
})
