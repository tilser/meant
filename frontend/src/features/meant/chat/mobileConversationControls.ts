export interface MobileConversationControlsGestureState {
  direction: 'up' | 'down' | null
  travel: number
  visible: boolean
}

export const MOBILE_CONVERSATION_CONTROLS_SCROLL_THRESHOLD = 10

export function mobileConversationControlsGestureState(
  state: MobileConversationControlsGestureState,
  deltaY: number,
): MobileConversationControlsGestureState {
  if (!Number.isFinite(deltaY) || deltaY === 0) {
    return state
  }

  const direction = deltaY > 0 ? 'down' : 'up'
  const travel = state.direction === direction ? state.travel + Math.abs(deltaY) : Math.abs(deltaY)

  if (travel < MOBILE_CONVERSATION_CONTROLS_SCROLL_THRESHOLD) {
    return { ...state, direction, travel }
  }

  return {
    direction,
    travel: 0,
    visible: direction === 'up',
  }
}
