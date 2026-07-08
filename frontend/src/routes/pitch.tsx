import { createFileRoute } from '@tanstack/react-router'

import { PitchDeck } from '../features/pitch/PitchDeck'

export const Route = createFileRoute('/pitch')({
  component: PitchDeck,
})
